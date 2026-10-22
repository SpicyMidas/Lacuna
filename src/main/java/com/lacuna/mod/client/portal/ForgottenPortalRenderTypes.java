package com.lacuna.mod.client.portal;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.FxShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Render types of the {@code lacuna:forgotten_portal} core shader (registered in {@link FxShaders}), built like
 * vanilla's {@code RenderType.endPortal()}: POSITION vertices only and a {@link RenderStateShard.MultiTextureStateShard}
 * with the fog ({@code Sampler0}) and mote ({@code Sampler1}) layers. Both textures are ours (tools/art/forgotten_portal.py).
 * {@link #surface()} draws the portal faces in the world, opaque, depth-tested like the End portal. When the shader did
 * not load (old drivers, shader packs) the surface falls back to vanilla's end portal.
 */
public final class ForgottenPortalRenderTypes {
    public static final ResourceLocation SKY = Lacuna.id("textures/environment/forgotten_portal_sky.png");
    public static final ResourceLocation MOTES = Lacuna.id("textures/environment/forgotten_portal_motes.png");

    private static final RenderStateShard.ShaderStateShard SHADER = new RenderStateShard.ShaderStateShard(FxShaders::forgottenPortal);
    private static final RenderStateShard.MultiTextureStateShard TEXTURES = RenderStateShard.MultiTextureStateShard.builder()
            .add(SKY, true, false)
            .add(MOTES, true, false)
            .build();

    private static final RenderType SURFACE = RenderType.create("lacuna_forgotten_portal", DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(SHADER)
                    .setTextureState(TEXTURES)
                    .createCompositeState(false));

    private ForgottenPortalRenderTypes() {}

    /** The portal faces: our shader, or vanilla's end portal when it is unavailable. */
    public static RenderType surface() {
        return FxShaders.forgottenPortal() != null ? SURFACE : RenderType.endPortal();
    }
}
