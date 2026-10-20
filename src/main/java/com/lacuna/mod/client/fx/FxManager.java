package com.lacuna.mod.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Owns every active {@link ClientFx}: ticks them on the client tick and renders them after particles.
 * <ul>
 *   <li><b>Isolation</b>: every effect ticks and renders inside its own guard ({@link FxGuard}). An effect that throws
 *       is dropped on its own and logged; the manager itself never switches off. (It used to run all effects inside a
 *       single {@code SafeRun} feature: one failing effect - any feature's, in any world - disabled every Lacuna effect
 *       for the rest of the session and {@link #add} silently discarded new ones, so the Tidecaller and Gravemaw attacks
 *       kept working without their visuals.)</li>
 *   <li><b>Own buffers</b>: effects draw into a private {@link MultiBufferSource.BufferSource}, never into vanilla's
 *       shared one, so an effect failing halfway through a quad cannot corrupt vanilla's batches; the private buffers
 *       are simply thrown away and rebuilt.</li>
 *   <li><b>Fabulous graphics</b>: the effects land in the particles target, which the transparency shader sorts against
 *       water, glass, clouds and weather by its depth. Effects write no depth (they are translucent), so every effect
 *       pixel was sorted at the depth of the opaque surface behind it and any water, ice, glass or cloud behind an effect
 *       was composited on top of it - over the sea a Tidecaller whirlpool all but vanished. Under Fabulous a second,
 *       depth-only pass ({@link FxRenderTypes#depthTwin}) writes the effects' own depth after their colour.</li>
 * </ul>
 * Bounded (oldest effects are dropped past {@link #MAX}); cleared on logout.
 */
public final class FxManager {
    /** Diagnostics key of the render pass ({@link FxGuard}); nothing switches the manager off any more. */
    public static final String FEATURE = "client_fx";
    private static final int MAX = 384;
    private static final int BUFFER_BYTES = 262_144;
    private static final List<ClientFx> ACTIVE = new ArrayList<>();
    /** Effects added while the list is being ticked or rendered (an effect spawning another). */
    private static final List<ClientFx> PENDING = new ArrayList<>();
    private static boolean iterating;
    @Nullable
    private static ByteBufferBuilder bytes;
    @Nullable
    private static MultiBufferSource.BufferSource colour;
    @Nullable
    private static MultiBufferSource.BufferSource depth;

    private FxManager() {}

    public static void add(ClientFx fx) {
        if (fx == null) {
            return;
        }
        if (iterating) {
            PENDING.add(fx);
            return;
        }
        if (ACTIVE.size() >= MAX) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(fx);
    }

    public static int count() {
        return ACTIVE.size();
    }

    public static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }

    private static void flushPending() {
        iterating = false;
        if (!PENDING.isEmpty()) {
            List<ClientFx> added = new ArrayList<>(PENDING);
            PENDING.clear();
            added.forEach(FxManager::add);
        }
    }

    private static String key(ClientFx fx) {
        return "fx " + fx.getClass().getName();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        if (mc.isPaused() || ACTIVE.isEmpty()) {
            return;
        }
        iterating = true;
        try {
            for (int i = 0; i < ACTIVE.size(); ) {
                ClientFx fx = ACTIVE.get(i);
                boolean keep;
                try {
                    keep = fx.tick();
                } catch (Throwable t) {
                    FxGuard.report(key(fx), t);
                    keep = false;
                }
                if (keep) {
                    i++;
                } else {
                    ACTIVE.remove(i);
                }
            }
        } finally {
            flushPending();
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        // additive particle render types leave their blend function behind: restore the default first
        RenderSystem.defaultBlendFunc();
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        if (ACTIVE.isEmpty() || mc.level == null || pose == null) {
            return;
        }
        iterating = true;
        try {
            if (!FxGuard.run(FEATURE, () -> render(event, mc, pose))) {
                releaseBuffers();
            }
        } finally {
            flushPending();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void render(RenderLevelStageEvent event, Minecraft mc, PoseStack pose) {
        if (bytes == null) {
            bytes = new ByteBufferBuilder(BUFFER_BYTES);
            colour = MultiBufferSource.immediate(bytes);
            depth = new DepthSource(bytes);
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        FxRenderContext ctx = new FxRenderContext(pose, event.getCamera(), event.getCamera().getPosition(), partial, colour, mc.level);
        if (!drawAll(ctx)) {
            releaseBuffers();
            return;
        }
        colour.endBatch();
        if (Minecraft.useShaderTransparency()) {
            FxRenderContext depthCtx = new FxRenderContext(pose, event.getCamera(), event.getCamera().getPosition(), partial, depth,
                    mc.level);
            if (!drawAll(depthCtx)) {
                releaseBuffers();
                return;
            }
            depth.endBatch();
        }
    }

    /** Renders every effect; an effect that throws is dropped and false is returned (its half-built batch is discarded). */
    private static boolean drawAll(FxRenderContext ctx) {
        for (int i = 0; i < ACTIVE.size(); i++) {
            ClientFx fx = ACTIVE.get(i);
            try {
                fx.render(ctx);
            } catch (Throwable t) {
                FxGuard.report(key(fx), t);
                ACTIVE.remove(i);
                return false;
            }
        }
        return true;
    }

    /** Throws the private buffers away (a failed effect may have left half a vertex in them); rebuilt next frame. */
    private static void releaseBuffers() {
        if (bytes != null) {
            try {
                bytes.close();
            } catch (RuntimeException ignored) {
                // already freed: nothing else to do
            }
        }
        bytes = null;
        colour = null;
        depth = null;
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        releaseBuffers();
    }

    /** Fabulous depth pass: routes each effect render type to its depth-only twin and drops the rest. */
    private static final class DepthSource extends MultiBufferSource.BufferSource {
        DepthSource(ByteBufferBuilder shared) {
            super(shared, Object2ObjectSortedMaps.emptyMap());
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            RenderType twin = FxRenderTypes.depthTwin(type);
            return twin == null ? NoVertices.INSTANCE : super.getBuffer(twin);
        }
    }

    /** Swallows vertices (render types without a depth twin). */
    private enum NoVertices implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
