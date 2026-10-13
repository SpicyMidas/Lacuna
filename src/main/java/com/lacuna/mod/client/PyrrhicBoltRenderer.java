package com.lacuna.mod.client;

import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.gear.PyrrhicBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pyrrhic bolt: a blazing flare with a molten core and a fiery tail along its flight. */
public class PyrrhicBoltRenderer extends EntityRenderer<PyrrhicBoltEntity> {
    public PyrrhicBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PyrrhicBoltEntity bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = bolt.tickCount + partialTick;
        float flicker = 0.85F + 0.15F * Mth.sin(t * 1.7F);
        Vec3 center = new Vec3(0, bolt.getBbHeight() * 0.5, 0);
        Vec3 tail = center.subtract(bolt.getDeltaMovement().scale(4.0));
        PoseStack.Pose p = pose.last();
        FxDraw.ribbon(p, buffers.getBuffer(FxRenderTypes.beam()), center, tail, 0.45F, 0.05F,
                FxDraw.argb(0.9F, 0xFF7A2A), FxDraw.argb(0.0F, 0xFF3A10), 0.0F, 3.0F);
        FxDraw.billboard(p, buffers.getBuffer(FxRenderTypes.glowShader()), this.entityRenderDispatcher.camera, center, 0.7F * flicker, 0.0F,
                FxDraw.argb(1.0F, 0xFF8A3A));
        FxDraw.billboard(p, buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW)), this.entityRenderDispatcher.camera, center,
                0.22F, 0.0F, FxDraw.argb(1.0F, 0xFFF0C0));
        super.render(bolt, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PyrrhicBoltEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
