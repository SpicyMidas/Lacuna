package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.structure.ReflectionEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Reflection: the zombie-layer humanoid model in dark mirrored silver, slightly translucent. */
public class ReflectionRenderer extends HumanoidMobRenderer<ReflectionEntity, HumanoidModel<ReflectionEntity>> {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/entity/reflection.png");

    public ReflectionRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE), RenderType::entityTranslucent), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(ReflectionEntity entity) {
        return TEXTURE;
    }
}
