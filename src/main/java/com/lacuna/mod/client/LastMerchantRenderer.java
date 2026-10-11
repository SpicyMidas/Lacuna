package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.merchant.LastMerchant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Last Merchant: the vanilla wandering-trader villager model with a faded travelling robe. */
public class LastMerchantRenderer extends MobRenderer<LastMerchant, VillagerModel<LastMerchant>> {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/entity/last_merchant.png");

    public LastMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5F);
    }

    @Override
    protected void scale(LastMerchant entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public ResourceLocation getTextureLocation(LastMerchant entity) {
        return TEXTURE;
    }
}
