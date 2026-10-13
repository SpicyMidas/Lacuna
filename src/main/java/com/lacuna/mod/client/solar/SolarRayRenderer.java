package com.lacuna.mod.client.solar;

import com.lacuna.mod.solar.SolarRayEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** The solar column has no model: its light is queued for {@link SolarVisuals} (drawn after particles). */
public class SolarRayRenderer extends EntityRenderer<SolarRayEntity> {
    public SolarRayRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SolarRayEntity ray, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        SolarVisuals.queue(ray);
    }

    @Override
    public ResourceLocation getTextureLocation(SolarRayEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
