package com.lacuna.mod.client.portal;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.FxShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Render types of the {@code lacuna:forgotten_portal} core shader (registered in {@link FxShaders}), built like
 * vanilla's {@code RenderType.endPortal()}: POSITION vertices only and a {@link RenderStateShard.MultiTextureStateShard}
 * with the fog ({@code Sampler0}) and mote ({@code Sampler1}) layers. Both textures are ours (tools/art/forgotten_portal.py).
 * <ul>
 *   <li>{@link #surface()}: the portal faces in the world, opaque, depth-tested like the End portal.</li>
 *   <li>{@link #voidOverlay()}: the same field over the whole screen for the travel transition, alpha-blended by
 *       {@code ColorModulator} (the shader colour) and wound into a vortex by the {@code Swirl} uniform.</li>
 * </ul>
 * When the shader did not load (old drivers, shader packs) the surface falls back to vanilla's end portal and the
 * overlay to a plain fill ({@link #voidOverlay()} returns null).
 */
public final class ForgottenPortalRenderTypes {
    public static final ResourceLocation SKY = Lacuna.id("textures/environment/forgotten_portal_sky.png");
    public static final ResourceLocation MOTES = Lacuna.id("textures/environment/forgotten_portal_motes.png");

    private static final RenderStateShard.ShaderStateShard SHADER = new RenderStateShard.ShaderStateShard(FxShaders::forgottenPortal);
    private static final RenderStateShard.MultiTextureStateShard TEXTURES = RenderStateShard.MultiTextureStateShard.builder()
            .add(SKY, true, false)
            .add(MOTES, true, false)
            .build();

    /** Uniforms of the next overlay draw (applied while the overlay type is set up, reset afterwards). */
    private static float overlaySwirl;
    private static float overlayTime;

    private static final RenderType SURFACE = RenderType.create("lacuna_forgotten_portal", DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(SHADER)
                    .setTextureState(TEXTURES)
                    .createCompositeState(false));

    private static final RenderType OVERLAY = RenderType.create("lacuna_forgotten_void", DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(SHADER)
                    .setTextureState(TEXTURES)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setTexturingState(new RenderStateShard.TexturingStateShard("lacuna_forgotten_void_swirl",
                            () -> uniforms(overlaySwirl, overlayTime), () -> uniforms(0.0F, 0.0F)))
                    .createCompositeState(false));

    private ForgottenPortalRenderTypes() {}

    private static void uniforms(float swirl, float time) {
        ShaderInstance shader = FxShaders.forgottenPortal();
        if (shader != null) {
            shader.safeGetUniform("Swirl").set(swirl);
            shader.safeGetUniform("VoidTime").set(time);
        }
    }

    /** The portal faces: our shader, or vanilla's end portal when it is unavailable. */
    public static RenderType surface() {
        return FxShaders.forgottenPortal() != null ? SURFACE : RenderType.endPortal();
    }

    /**
     * The full-screen void, or null without the shader. {@code swirl} 0..1 winds the vortex; {@code time} is the
     * transition's own clock in GameTime units (1 = 24000 ticks), so the field never jumps at a dimension change.
     */
    @Nullable
    public static RenderType voidOverlay(float swirl, float time) {
        if (FxShaders.forgottenPortal() == null) {
            return null;
        }
        overlaySwirl = swirl;
        overlayTime = time;
        return OVERLAY;
    }
}
