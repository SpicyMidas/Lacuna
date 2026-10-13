package com.lacuna.mod.client.solar;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.FxShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Solar shaders and render types. Two dedicated GPU core shaders ({@code assets/lacuna/shaders/core/}):
 * <ul>
 *   <li>{@code lacuna:solar_column}: the pillar of sunlight (white-hot core, downward-sliding golden striations,
 *       descending light bands, shimmering halo); u across a camera-facing ribbon, v = height in blocks.</li>
 *   <li>{@code lacuna:solar_corona}: the blazing sun with turning crowns of rays, drawn in the sky.</li>
 * </ul>
 * Both output premultiplied colour, so they can be blended two ways: <b>additive</b> (light piles up into white-hot
 * cores) and <b>over</b> ({@code ONE, ONE_MINUS_SRC_ALPHA}: the light also covers what is behind it, which keeps the
 * gold visible against a bright daytime sky instead of washing out to white). Like {@link FxShaders}, a shader that
 * fails to load is logged and replaced by the framework's beam / glow types.
 */
public final class SolarRenderTypes {
    @Nullable
    private static ShaderInstance column;
    @Nullable
    private static ShaderInstance corona;

    /** Premultiplied "over": src + dst * (1 - src.a). */
    private static final RenderStateShard.TransparencyStateShard OVER = new RenderStateShard.TransparencyStateShard("lacuna_premultiplied_over",
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
    private static final RenderStateShard.ShaderStateShard GLOW_SHADER = new RenderStateShard.ShaderStateShard(FxShaders::glow);
    private static final RenderStateShard.ShaderStateShard POS_TEX_COLOR =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);

    // world space: after particles, depth-tested, particle target (correct under Fabulous)
    private static final RenderType COLUMN_ADD = create("lacuna_solar_column", COLUMN_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    private static final RenderType COLUMN_OVER = create("lacuna_solar_column_over", COLUMN_SHADER, OVER,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    private static final RenderType GLOW_OVER = create("lacuna_solar_glow_over", GLOW_SHADER, OVER,
            RenderStateShard.LEQUAL_DEPTH_TEST, true, null);
    // sky: right after the sky into the main target, no depth (terrain drawn later covers it)
    private static final RenderType SKY_CORONA = create("lacuna_sky_corona", CORONA_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.NO_DEPTH_TEST, false, null);
    private static final RenderType SKY_GLOW = create("lacuna_sky_glow", GLOW_SHADER, RenderStateShard.ADDITIVE_TRANSPARENCY,
            RenderStateShard.NO_DEPTH_TEST, false, null);
    private static final RenderType SKY_GLOW_OVER = create("lacuna_sky_glow_over", GLOW_SHADER, OVER,
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
            b.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false));
        }
        return RenderType.create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                b.createCompositeState(false));
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id("solar_column"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), s -> column = s);
        } catch (IOException | RuntimeException e) {
            column = null;
            Lacuna.LOGGER.warn("Lacuna solar column shader unavailable, using the beam fallback: {}", e.toString());
        }
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id("solar_corona"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), s -> corona = s);
        } catch (IOException | RuntimeException e) {
            corona = null;
            Lacuna.LOGGER.warn("Lacuna solar corona shader unavailable, using the glow fallback: {}", e.toString());
        }
    }

    /** Additive column light (u across, v = height in blocks; colour = tint, alpha = intensity), or the beam fallback. */
    public static RenderType column() {
        return column != null ? COLUMN_ADD : FxRenderTypes.beam();
    }

    /** Column light that also covers the background (keeps its colour on a bright sky). */
    public static RenderType columnOver() {
        return column != null ? COLUMN_OVER : FxRenderTypes.beam();
    }

    /** Soft round glow that covers the background (u,v 0..1). */
    public static RenderType glowOver() {
        return FxShaders.glow() != null ? GLOW_OVER : FxRenderTypes.glowShader();
    }

    /** Sun corona in the sky (u,v 0..1 over the quad), with glow-shader and textured fallbacks. */
    public static RenderType skyCorona() {
        if (corona != null) {
            return SKY_CORONA;
        }
        return FxShaders.glow() != null ? SKY_GLOW : SKY_GLOW_TEXTURED;
    }

    /** Large warm wash around the sun that tints the sky instead of bleaching it. */
    public static RenderType skyWash() {
        return FxShaders.glow() != null ? SKY_GLOW_OVER : SKY_GLOW_TEXTURED;
    }
}
