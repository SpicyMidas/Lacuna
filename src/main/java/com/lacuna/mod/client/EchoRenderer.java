package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.flute.EchoEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Echo: the vanilla zombie-layer humanoid model drawn with a translucent render type and a ghostly texture. */
public class EchoRenderer extends HumanoidMobRenderer<EchoEntity, HumanoidModel<EchoEntity>> {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/entity/echo.png");

    public EchoRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE), RenderType::entityTranslucent), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoEntity entity) {
        return TEXTURE;
    }
}
