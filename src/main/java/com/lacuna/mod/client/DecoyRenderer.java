package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.mob.DecoyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Clockwork Decoy: the vanilla zombie-layer humanoid model as a brass mannequin. */
public class DecoyRenderer extends HumanoidMobRenderer<DecoyEntity, HumanoidModel<DecoyEntity>> {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/entity/clockwork_decoy.png");

    public DecoyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(DecoyEntity entity) {
        return TEXTURE;
    }
}
