package com.lacuna.mod.client.fx;

import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Owns every active {@link ClientFx}: ticks them on the client tick and renders them after particles.
 * Bounded (oldest effects are dropped past {@link #MAX}); cleared on logout; wrapped in {@link SafeRun}.
 */
public final class FxManager {
    public static final String FEATURE = "client_fx";
    private static final int MAX = 384;
    private static final List<ClientFx> ACTIVE = new ArrayList<>();

    private FxManager() {}

    public static void add(ClientFx fx) {
        if (SafeRun.isDisabled(FEATURE)) {
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
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused() || ACTIVE.isEmpty()) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            Iterator<ClientFx> it = ACTIVE.iterator();
            while (it.hasNext()) {
                if (!it.next().tick()) {
                    it.remove();
                }
            }
        }, FxManager::clear);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        // additive particle render types leave their blend function behind: restore the default first
        RenderSystem.defaultBlendFunc();
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        if (mc.level == null || pose == null) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            FxRenderContext ctx = new FxRenderContext(pose, event.getCamera(), event.getCamera().getPosition(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false), buffers, mc.level);
            for (ClientFx fx : ACTIVE) {
                fx.render(ctx);
            }
            buffers.endBatch();
            RenderSystem.defaultBlendFunc();
        }, FxManager::clear);
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
