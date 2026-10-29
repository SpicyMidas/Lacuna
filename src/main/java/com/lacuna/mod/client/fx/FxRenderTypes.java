package com.lacuna.mod.client.fx;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.compat.ShaderPackCompat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Effect render types. All use {@code POSITION_TEX_COLOR} quads (see {@link FxDraw}), never write depth, cull nothing
 * and render into the particle target (correct under Fabulous graphics).
 * <ul>
 *   <li>{@link #additive}: light (SRC_ALPHA, ONE) — glows, beams, sparks.</li>
 *   <li>{@link #shade}: ordinary alpha blending — darkness, smoke, black slashes (light cannot be black).</li>
 *   <li>{@link #beam()} / {@link #glowShader()}: procedural core shaders (premultiplied, ONE/ONE), with an
 *       automatic textured fallback when the shader did not load.</li>
 * </ul>
 * Every type that renders in depth order also has a depth-only twin ({@link #depthTwin}), drawn by {@link FxManager}
 * after the colour under Fabulous graphics so the transparency shader sorts effects by their own depth instead of the
 * depth of the opaque surface behind them.
 */
public final class FxRenderTypes {
    /** Soft radial alpha mask. */
    public static final ResourceLocation GLOW = Lacuna.id("textures/effect/glow.png");
    /** Beam cross-section mask (bright centre line, u across). */
    public static final ResourceLocation BEAM = Lacuna.id("textures/effect/beam.png");
    /** Thin ring mask. */
    public static final ResourceLocation RING = Lacuna.id("textures/effect/ring.png");

    private static final RenderStateShard.ShaderStateShard POS_TEX_COLOR =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);
    private static final RenderStateShard.ShaderStateShard BEAM_SHADER = new RenderStateShard.ShaderStateShard(FxShaders::beam);
    private static final RenderStateShard.ShaderStateShard GLOW_SHADER = new RenderStateShard.ShaderStateShard(FxShaders::glow);

    /** Effect render type -> its depth-only twin (render thread only). */
    private static final Map<RenderType, RenderType> DEPTH_TWINS = new IdentityHashMap<>();
    private static final Function<ResourceLocation, RenderType> DEPTH = Util.memoize(tex -> RenderType.create(
            "lacuna_depth_" + tex.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POS_TEX_COLOR)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> ADDITIVE = Util.memoize(tex -> twin(RenderType.create(
            "lacuna_additive_" + tex.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            state(tex, RenderStateShard.LIGHTNING_TRANSPARENCY, POS_TEX_COLOR, RenderStateShard.LEQUAL_DEPTH_TEST)), tex));
    private static final Function<ResourceLocation, RenderType> SHADE = Util.memoize(tex -> twin(RenderType.create(
            "lacuna_shade_" + tex.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            state(tex, RenderStateShard.TRANSLUCENT_TRANSPARENCY, POS_TEX_COLOR, RenderStateShard.LEQUAL_DEPTH_TEST)), tex));

    private static final RenderType BEAM_SHADED = RenderType.create("lacuna_beam", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(BEAM_SHADER)
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));
    private static final RenderType GLOW_SHADED = RenderType.create("lacuna_glow", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(GLOW_SHADER)
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    private FxRenderTypes() {}

    private static RenderType twin(RenderType type, ResourceLocation mask) {
        DEPTH_TWINS.put(type, DEPTH.apply(mask));
        return type;
    }

    /**
     * The depth-only twin of an effect render type (same texture, so fully transparent texels write nothing), or null for
     * types that must not write depth (seen-through effects) or that are not Lacuna effect types.
     */
    @Nullable
    public static RenderType depthTwin(RenderType type) {
        if (type == BEAM_SHADED) {
            return DEPTH.apply(BEAM);
        }
        if (type == GLOW_SHADED) {
            return DEPTH.apply(GLOW);
        }
        return DEPTH_TWINS.get(type);
    }

    /**
     * Registers {@code twin} as the depth-only twin of an effect render type defined elsewhere (same vertex format;
     * render thread only), so the Fabulous depth pass of {@link FxManager} draws it. Returns {@code type}.
     */
    public static RenderType withDepthTwin(RenderType type, RenderType twin) {
        DEPTH_TWINS.put(type, twin);
        return type;
    }

    private static RenderType.CompositeState state(ResourceLocation tex, RenderStateShard.TransparencyStateShard transparency,
                                                   RenderStateShard.ShaderStateShard shader, RenderStateShard.DepthTestStateShard depth) {
        return RenderType.CompositeState.builder()
                .setShaderState(shader)
                .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                .setTransparencyState(transparency)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(depth)
                .setOutputState(RenderStateShard.PARTICLES_TARGET)
                .createCompositeState(false);
    }

    /** Additive light with a texture mask (vertex colour tints, alpha scales intensity). */
    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.apply(texture);
    }

    /** Alpha-blended (non-glowing) textured quads, for darkness and smoke. */
    public static RenderType shade(ResourceLocation texture) {
        return SHADE.apply(texture);
    }

    /**
     * True when the procedural beam shader compiled and may be used (no shader pack in use, {@link ShaderPackCompat});
     * {@link #beam()} falls back to {@code additive(BEAM)}.
     */
    public static boolean hasBeamShader() {
        return ShaderPackCompat.usable(FxShaders.beam());
    }

    /**
     * Procedural energy beam. Vertex u runs 0..1 across the beam, v along it in blocks (drives the flow noise),
     * colour = tint, alpha = intensity. Falls back to the textured additive beam.
     */
    public static RenderType beam() {
        return ShaderPackCompat.usable(FxShaders.beam()) ? BEAM_SHADED : additive(BEAM);
    }

    /** Procedural soft glow / flare over a quad with u,v in 0..1. Falls back to the textured glow. */
    public static RenderType glowShader() {
        return ShaderPackCompat.usable(FxShaders.glow()) ? GLOW_SHADED : additive(GLOW);
    }
}
