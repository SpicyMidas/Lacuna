package com.lacuna.mod.client.solar;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.compat.ShaderPackCompat;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.FxShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Depth and volume for the solar light (Sun's Fury shafts, pillar, trail and flares, the Divine Magnifying Glass beam).
 * <p>
 * <b>Why</b>: the light used to be drawn after particles, before vanilla draws the clouds, writing no depth: every
 * cloud drawn later painted over it whatever its distance (Fast / Fancy: clouds blend over the main target; Fabulous:
 * the transparency composite sorted the light at the depth of the opaque surface behind it, so any cloud in front of
 * that sky or terrain covered it). Ribbons were also cut by a hard line wherever they met the ground.
 * <p>
 * <b>How</b>: the shaft and flare render types get a late twin ({@link #late}) whose quads are collected during the
 * after-particles pass like before (same geometry, same code in {@link SolarVisuals}, {@link MagnifierBeams},
 * {@link SpearCharge}) but drawn after the whole level ({@code AFTER_LEVEL}: clouds, weather and the Fabulous composite
 * are done, the hand is not drawn yet) into the main target, without a depth test, by shaders that read the scene
 * depth instead ({@link SolarDepth}: terrain, entities, water, glass and clouds; one depth copy per frame in Fast /
 * Fancy, none under Fabulous, where the translucent and cloud layers' own depth textures are read):
 * <ul>
 *   <li>{@code lacuna:solar_volume} / {@code lacuna:beam_volume}: each camera-facing ribbon is read as a round shaft
 *       around its axis. Per pixel the ray crosses the shaft along a chord centred on the ribbon whose length follows
 *       from the radius, the distance from the axis and the angle to it, so the light is brighter where the eye looks
 *       along the shaft, and the part of the chord behind the scene or the eye is dropped: near clouds and hills hide
 *       it, far clouds stay behind it, it melts into the ground and walls it enters instead of being cut, and it fades
 *       smoothly when the eye is inside it. Dimmed with distance, never fogged grey (not at all for sunlight from the
 *       sky).</li>
 *   <li>{@code lacuna:solar_soft_glow} / {@code lacuna:solar_soft_corona}: flares and glows fade softly behind the
 *       surfaces they touch (soft particles that never dim what is in front), same cloud ordering and fog.</li>
 * </ul>
 * The pillar and the focus sunbeams are real 3D volumes drawn in the same pass ({@link SolarTubes}); the ribbons
 * left here (the flight trail, the Divine Magnifying Glass beam, and every shaft when the tube shader is missing) are
 * re-encoded on the fly by {@link Shafts} into {@link DefaultVertexFormat#PARTICLE} (u = signed
 * distance from the axis in blocks, the radius log-encoded in UV2), no allocation. Every shader is optional: a type
 * whose late shader failed to load is drawn after particles exactly as before.
 */
public final class SolarVolume {
    /** {@code UV2.y} flag: the soft sunbeam profile (ribbon u offset 2). */
    static final int SUNBEAM = 1;
    /** {@code UV2.y} flag: sunlight coming down from the sky's sun disc, not dimmed with distance. */
    static final int SKY = 2;

    @Nullable
    private static ShaderInstance volume;
    @Nullable
    private static ShaderInstance beamVolume;
    @Nullable
    private static ShaderInstance softGlow;
    @Nullable
    private static ShaderInstance softCorona;

    private static final RenderStateShard.ShaderStateShard VOLUME_SHADER = new RenderStateShard.ShaderStateShard(
            () -> SolarDepth.bind(volume));
    private static final RenderStateShard.ShaderStateShard BEAM_SHADER = new RenderStateShard.ShaderStateShard(
            () -> SolarDepth.bind(beamVolume));
    private static final RenderStateShard.ShaderStateShard GLOW_SHADER = new RenderStateShard.ShaderStateShard(
            () -> SolarDepth.bind(softGlow));
    private static final RenderStateShard.ShaderStateShard CORONA_SHADER = new RenderStateShard.ShaderStateShard(
            () -> SolarDepth.bind(softCorona));

    private static final RenderType VOLUME_ADD = create("lacuna_solar_volume", VOLUME_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            DefaultVertexFormat.PARTICLE);
    private static final RenderType BEAM_ADD = create("lacuna_beam_volume", BEAM_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            DefaultVertexFormat.PARTICLE);
    private static final RenderType GLOW_ADD = create("lacuna_soft_glow", GLOW_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            DefaultVertexFormat.POSITION_TEX_COLOR);
    private static final RenderType FLARE_ADD = create("lacuna_soft_flare", CORONA_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            DefaultVertexFormat.POSITION_TEX_COLOR);
    /** Late drawing order (all additive: light never darkens what is behind it). */
    private static final List<RenderType> ORDER = List.of(VOLUME_ADD, BEAM_ADD, GLOW_ADD, FLARE_ADD);

    private static final Shafts ADD_WORLD = new Shafts(VOLUME_ADD, 0);
    private static final Shafts ADD_SKY = new Shafts(VOLUME_ADD, SKY);
    private static final Shafts BEAM = new Shafts(BEAM_ADD, 0);
    private static final Late GLOW = new Direct(GLOW_ADD);
    private static final Late FLARE = new Direct(FLARE_ADD);

    // state of the after-particles pass, restored for the late one
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static float fogStart = Float.MAX_VALUE;
    private static float fogEnd = 0.0F;

    private SolarVolume() {}

    private static RenderType create(String name, RenderStateShard.ShaderStateShard shader, RenderStateShard.TransparencyStateShard blend,
                                     VertexFormat format) {
        // no output state: drawn into the main target after the level (and the Fabulous composite)
        return RenderType.create(name, format, VertexFormat.Mode.QUADS, 4096, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(shader)
                        .setTransparencyState(blend)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .createCompositeState(false));
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        register(event, "solar_volume", DefaultVertexFormat.PARTICLE, s -> volume = s, () -> volume = null);
        register(event, "beam_volume", DefaultVertexFormat.PARTICLE, s -> beamVolume = s, () -> beamVolume = null);
        register(event, "solar_soft_glow", DefaultVertexFormat.POSITION_TEX_COLOR, s -> softGlow = s, () -> softGlow = null);
        register(event, "solar_soft_corona", DefaultVertexFormat.POSITION_TEX_COLOR, s -> softCorona = s, () -> softCorona = null);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> onLoad,
                                 Runnable onFail) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id(name), format), onLoad);
        } catch (IOException | RuntimeException e) {
            onFail.run();
            Lacuna.LOGGER.warn("Lacuna {} shader unavailable, the light keeps its flat after-particles look: {}", name, e.toString());
        }
    }

    /** The late render types, in drawing order (fixed buffers of {@link SolarBuffers}). */
    static List<RenderType> drawOrder() {
        return ORDER;
    }

    /**
     * The late, depth-aware twin of a solar / effect render type as currently resolved, or null to draw it after
     * particles as before (not light, its late shader did not load, or a shader pack is in use: the pack's buffers
     * hold the scene depth, {@link ShaderPackCompat}).
     */
    @Nullable
    static Late late(RenderType type) {
        if (ShaderPackCompat.active()) {
            return null;
        }
        if (volume != null) {
            if (type == SolarRenderTypes.COLUMN_ADD) {
                return ADD_WORLD;
            }
            if (type == SolarRenderTypes.SKY_COLUMN_ADD) {
                return ADD_SKY;
            }
        }
        if (beamVolume != null && FxRenderTypes.hasBeamShader() && type == FxRenderTypes.beam()) {
            return BEAM;
        }
        if (softGlow != null && FxShaders.glow() != null && type == FxRenderTypes.glowShader()) {
            return GLOW;
        }
        if (softCorona != null && type == SolarRenderTypes.FLARE_ADD) {
            return FLARE;
        }
        return null;
    }

    /**
     * Start of the after-particles pass: drops late quads of a frame whose late pass never ran and remembers the
     * camera rotation and terrain fog (vanilla resets both before {@code AFTER_LEVEL}).
     */
    static void beginFrame(SolarBuffers buffers) {
        buffers.discard(ORDER);
        SolarTubes.clear();
        MODEL_VIEW.set(RenderSystem.getModelViewMatrix());
        fogStart = RenderSystem.getShaderFogStart();
        fogEnd = RenderSystem.getShaderFogEnd();
    }

    /** {@code AFTER_LEVEL}: draws the late light, if any was queued this frame, against the finished scene. */
    static void drawLate(RenderLevelStageEvent event, SolarBuffers buffers) {
        if (!buffers.started(ORDER) && !SolarTubes.pending()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!SolarDepth.prepare(mc)) {
            buffers.discard(ORDER);
            SolarTubes.clear();
            return;
        }
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(MODEL_VIEW);
        RenderSystem.applyModelViewMatrix();
        float savedStart = RenderSystem.getShaderFogStart();
        float savedEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        mc.getMainRenderTarget().bindWrite(false);
        RenderSystem.disableDepthTest();
        RenderSystem.defaultBlendFunc();
        try {
            // the shafts of light as 3D volumes first (screened light), then the light that only adds (glows and flares
            // on top of the shafts)
            SolarTubes.draw(MODEL_VIEW);
            RenderSystem.disableDepthTest();
            RenderSystem.defaultBlendFunc();
            for (RenderType type : ORDER) {
                buffers.endBatch(type);
            }
        } finally {
            buffers.discard(ORDER);
            SolarTubes.clear();
            RenderSystem.setShaderFogStart(savedStart);
            RenderSystem.setShaderFogEnd(savedEnd);
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /** Where a remapped render type's quads go: its late type, and how they are written there. */
    interface Late {
        RenderType type();

        VertexConsumer wrap(VertexConsumer target);
    }

    /** Billboards: same format, written as they are. */
    private record Direct(RenderType type) implements Late {
        @Override
        public VertexConsumer wrap(VertexConsumer target) {
            return target;
        }
    }

    /**
     * Re-encodes camera-facing ribbon quads ({@code FxDraw.ribbon} / {@code SolarVisuals.strip} layout: the two
     * vertices of one end first, u offset 0 or 2 on one edge and +1 on the other, v = blocks along) into
     * {@code PARTICLE} vertices for the volume shaders: u becomes the signed distance from the axis in blocks, UV2 the
     * radius of that end (log-encoded) and the flags. Fixed arrays, no allocation.
     */
    private static final class Shafts implements Late, VertexConsumer {
        private final RenderType type;
        private final int flags;
        private VertexConsumer out;
        private final float[] x = new float[4];
        private final float[] y = new float[4];
        private final float[] z = new float[4];
        private final float[] u = new float[4];
        private final float[] v = new float[4];
        private final int[] colour = new int[4];
        private int count;
        private int filled;

        Shafts(RenderType type, int flags) {
            this.type = type;
            this.flags = flags;
        }

        @Override
        public RenderType type() {
            return type;
        }

        @Override
        public VertexConsumer wrap(VertexConsumer target) {
            out = target;
            count = 0;
            filled = 0;
            return this;
        }

        @Override
        public VertexConsumer addVertex(float px, float py, float pz) {
            if (count == 4) {
                count = 0;
            }
            x[count] = px;
            y[count] = py;
            z[count] = pz;
            u[count] = 0.0F;
            v[count] = 0.0F;
            colour[count] = -1;
            count++;
            filled = 1;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (count > 0) {
                colour[count - 1] = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
                filled |= 4;
                complete();
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float pu, float pv) {
            if (count > 0) {
                u[count - 1] = pu;
                v[count - 1] = pv;
                filled |= 2;
                complete();
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int a, int b) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int a, int b) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            return this;
        }

        private void complete() {
            if (count == 4 && filled == 7) {
                emit();
                count = 0;
                filled = 0;
            }
        }

        private void emit() {
            if (out == null) {
                return;
            }
            float ra = 0.5F * distance(0, 1);
            float rb = 0.5F * distance(3, 2);
            float u0 = Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3]));
            int mode = u0 >= 1.5F ? SUNBEAM : 0;
            float base = mode == SUNBEAM ? 2.0F : 0.0F;
            int f = flags | mode;
            for (int i = 0; i < 4; i++) {
                float r = i < 2 ? ra : rb;
                float lateral = Mth.clamp((u[i] - base) * 2.0F - 1.0F, -1.0F, 1.0F) * r;
                out.addVertex(x[i], y[i], z[i]).setUv(lateral, v[i]).setColor(colour[i]).setUv2(encode(r), f);
            }
        }

        private float distance(int a, int b) {
            float dx = x[a] - x[b];
            float dy = y[a] - y[b];
            float dz = z[a] - z[b];
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** Radius as a short: {@code r = exp(k / 2048 - 8)} (0.0003 to 2900 blocks, 0.05% steps). */
        private static int encode(float r) {
            double k = (Math.log(Math.max(r, 1.0E-3)) + 8.0) * 2048.0;
            return (int) Mth.clamp(Math.round(k), 0L, 32767L);
        }
    }
}
