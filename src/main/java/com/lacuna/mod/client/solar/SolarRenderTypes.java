package com.lacuna.mod.client.solar;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.compat.ShaderPackCompat;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.FxShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Solar shaders and render types. Three dedicated GPU core shaders ({@code assets/lacuna/shaders/core/}):
 * <ul>
 *   <li>{@code lacuna:solar_column}: pillars and shafts of sunlight (white-hot core, sliding golden striations,
 *       descending light bands, rippling edges, shimmering halo); u across a camera-facing ribbon, v = blocks along.</li>
 *   <li>{@code lacuna:solar_corona}: the blazing sun with turning crowns of rays, and impact flares.</li>
 *   <li>{@code lacuna:solar_ring}: smooth procedural marks on flat quads: the focus reticle, shockwaves, light pools
 *       and thin rings ({@link #RETICLE}, {@link #SHOCKWAVE}, {@link #POOL}, {@link #THIN} select the mode via u).</li>
 * </ul>
 * All output premultiplied colour. Light is always <b>additive</b> (it piles up into white-hot cores and never darkens
 * what is behind it); only the light pools lying on the ground keep the covering <b>over</b> blend
 * ({@code ONE, ONE_MINUS_SRC_ALPHA}). Like {@link FxShaders}, a shader that fails to load
 * is logged and replaced by the framework's beam / glow / textured types (so is every one while an Iris / Oculus shader
 * pack is in use, {@link ShaderPackCompat}).
 * <p>
 * The light (columns, sunbeams, the trail, flares and glows) is drawn with depth and volume after the level by
 * {@link SolarVolume} whenever its shaders loaded: these types then only name it (the swap happens in
 * {@link SolarBuffers}); the ground marks stay here, after particles.
 */
public final class SolarRenderTypes {
    /** {@code solar_ring} modes: pass {@code mode * 2} as the u offset of the quad (u = offset + 0..1). */
    public static final int RETICLE = 0;
    public static final int SHOCKWAVE = 1;
    public static final int POOL = 2;
    public static final int THIN = 3;

    @Nullable
    private static ShaderInstance column;
    @Nullable
    private static ShaderInstance corona;
    @Nullable
    private static ShaderInstance ring;

    /** Premultiplied "over": src + dst * (1 - src.a). */
    static final RenderStateShard.TransparencyStateShard OVER = new RenderStateShard.TransparencyStateShard("lacuna_premultiplied_over",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderStateShard.ShaderStateShard COLUMN_SHADER = new RenderStateShard.ShaderStateShard(() -> column);
    private static final RenderStateShard.ShaderStateShard CORONA_SHADER = new RenderStateShard.ShaderStateShard(() -> corona);
    private static final RenderStateShard.ShaderStateShard RING_SHADER = new RenderStateShard.ShaderStateShard(() -> ring);
    private static final RenderStateShard.ShaderStateShard GLOW_SHADER = new RenderStateShard.ShaderStateShard(FxShaders::glow);
    private static final RenderStateShard.ShaderStateShard POS_TEX_COLOR =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);

    // world space: after particles, depth-tested, particle target; the light ones are drawn after the level with depth
    // and volume instead when their SolarVolume twin loaded
    static final RenderType COLUMN_ADD = create("lacuna_solar_column", COLUMN_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    static final RenderType SKY_COLUMN_ADD = create("lacuna_solar_sky_column", COLUMN_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    static final RenderType FLARE_ADD = create("lacuna_solar_flare", CORONA_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    private static final RenderType RING_ADD = create("lacuna_solar_ring", RING_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    private static final RenderType RING_OVER = create("lacuna_solar_ring_over", RING_SHADER, OVER,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    private static final RenderType GLOW_OVER = create("lacuna_solar_glow_over", GLOW_SHADER, OVER,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    /**
     * The Divine Magnifying Glass' scorch marks ({@link MagnifierScorch}): dark grey, alpha-blended (they darken the
     * face they lie on), depth-tested, polygon offset against the face, after particles. Vanilla position / colour
     * shader, so shader packs keep them.
     */
    private static final RenderType SCORCH = RenderType.create("lacuna_magnifier_scorch", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));
    // sky: right after the sky into the main target, no depth (terrain drawn later covers it)
    private static final RenderType SKY_CORONA = create("lacuna_sky_corona", CORONA_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.NO_DEPTH_TEST, false, null);
    private static final RenderType SKY_GLOW = create("lacuna_sky_glow", GLOW_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.NO_DEPTH_TEST, false, null);
    private static final RenderType SKY_GLOW_TEXTURED = create("lacuna_sky_glow_textured", POS_TEX_COLOR,
            RenderStateShard.LIGHTNING_TRANSPARENCY, RenderStateShard.NO_DEPTH_TEST, false, FxRenderTypes.GLOW);

    private SolarRenderTypes() {}

    private static RenderType create(String name, RenderStateShard.ShaderStateShard shader, RenderStateShard.TransparencyStateShard blend,
                                     RenderStateShard.DepthTestStateShard depth, boolean particleTarget, @Nullable ResourceLocation texture) {
        RenderType.CompositeState.CompositeStateBuilder b = RenderType.CompositeState.builder()
                .setShaderState(shader)
                .setTransparencyState(blend)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(depth);
        if (particleTarget) {
            b.setOutputState(RenderStateShard.PARTICLES_TARGET);
        }
        if (texture != null) {
            b.setTextureState(new RenderStateShard.TextureStateShard(texture, true, false));
        }
        return RenderType.create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                b.createCompositeState(false));
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        register(event, "solar_column", s -> column = s, () -> column = null);
        register(event, "solar_corona", s -> corona = s, () -> corona = null);
        register(event, "solar_ring", s -> ring = s, () -> ring = null);
    }

    private static void register(RegisterShadersEvent event, String name, Consumer<ShaderInstance> onLoad, Runnable onFail) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id(name), DefaultVertexFormat.POSITION_TEX_COLOR),
                    onLoad);
        } catch (IOException | RuntimeException e) {
            onFail.run();
            Lacuna.LOGGER.warn("Lacuna {} shader unavailable, using the framework fallback: {}", name, e.toString());
        }
    }

    /**
     * Every world-space render type the solar effects use (as currently resolved, fallbacks included), in drawing
     * order: the covering layers first, then the light that adds on top of them.
     */
    static java.util.List<RenderType> drawOrder() {
        return java.util.List.of(SCORCH, columnOver(), skyColumnOver(), ringOver(), glowOver(), ring(), column(), skyColumn(),
                FxRenderTypes.beam(), FxRenderTypes.glowShader(), flare());
    }

    /** Scorch marks of the Divine Magnifying Glass on block faces (position / colour quads). */
    static RenderType scorch() {
        return SCORCH;
    }

    /** Additive column light (u across, v = blocks along; colour = tint, alpha = intensity), or the beam fallback. */
    public static RenderType column() {
        return ShaderPackCompat.usable(column) ? COLUMN_ADD : FxRenderTypes.beam();
    }

    /**
     * The wide outer column layers (haze, halo, trail glow). Additive like the rest of the light: a covering blend
     * here darkened the sky and clouds behind the faint outer layers into a grey smoke around every ray.
     */
    public static RenderType columnOver() {
        return column();
    }

    /**
     * {@link #column()} for sunlight coming down from the sun's disc in the sky: drawn the same, but not dimmed like
     * the terrain (vanilla does not fog the sun either), so the shafts keep leaving the disc at any render distance.
     */
    public static RenderType skyColumn() {
        return ShaderPackCompat.usable(column) ? SKY_COLUMN_ADD : FxRenderTypes.beam();
    }

    /** {@link #columnOver()} for sunlight from the sky (see {@link #skyColumn()}): additive too. */
    public static RenderType skyColumnOver() {
        return skyColumn();
    }

    /** Additive starburst flare (u,v 0..1 over a billboard): the corona shader in world space, or the glow fallback. */
    public static RenderType flare() {
        return ShaderPackCompat.usable(corona) ? FLARE_ADD : FxRenderTypes.glowShader();
    }

    /** Additive procedural ring marks (u = mode * 2 + 0..1, v 0..1), or the textured ring. */
    public static RenderType ring() {
        return ShaderPackCompat.usable(ring) ? RING_ADD : FxRenderTypes.additive(FxRenderTypes.RING);
    }

    /** Ring marks that also cover the background (light pools on bright ground). */
    public static RenderType ringOver() {
        return ShaderPackCompat.usable(ring) ? RING_OVER : FxRenderTypes.additive(FxRenderTypes.GLOW);
    }

    /** Soft round glow that covers the background (u,v 0..1). */
    public static RenderType glowOver() {
        return ShaderPackCompat.usable(FxShaders.glow()) ? GLOW_OVER : FxRenderTypes.glowShader();
    }

    /** Sun corona in the sky (u,v 0..1 over the quad), with glow-shader and textured fallbacks. */
    public static RenderType skyCorona() {
        if (ShaderPackCompat.usable(corona)) {
            return SKY_CORONA;
        }
        return ShaderPackCompat.usable(FxShaders.glow()) ? SKY_GLOW : SKY_GLOW_TEXTURED;
    }

    /** Large warm wash around the sun: additive light (a covering wash greyed the blue sky around the sun). */
    public static RenderType skyWash() {
        return ShaderPackCompat.usable(FxShaders.glow()) ? SKY_GLOW : SKY_GLOW_TEXTURED;
    }
}
