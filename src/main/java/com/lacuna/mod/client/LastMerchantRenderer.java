package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.merchant.LastMerchant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Last Merchant: a plain plains villager (vanilla villager model, base skin and plains type layer) wearing a blue
 * vest drawn as a third cutout layer over the robe.
 */
public class LastMerchantRenderer extends MobRenderer<LastMerchant, VillagerModel<LastMerchant>> {
    private static final ResourceLocation BASE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation PLAINS = ResourceLocation.withDefaultNamespace("textures/entity/villager/type/plains.png");
    private static final ResourceLocation VEST = Lacuna.id("textures/entity/last_merchant_vest.png");

    public LastMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        addLayer(new Overlay(this, PLAINS));
        addLayer(new Overlay(this, VEST));
    }

    @Override
    protected void scale(LastMerchant entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public ResourceLocation getTextureLocation(LastMerchant entity) {
        return BASE;
    }

    /** One fixed texture over the parent model, drawn like vanilla's villager type/profession layers. */
    static final class Overlay extends RenderLayer<LastMerchant, VillagerModel<LastMerchant>> {
        private final ResourceLocation texture;

        Overlay(RenderLayerParent<LastMerchant, VillagerModel<LastMerchant>> parent, ResourceLocation texture) {
            super(parent);
            this.texture = texture;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LastMerchant entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!entity.isInvisible()) {
                renderColoredCutoutModel(getParentModel(), texture, poseStack, buffer, packedLight, entity, -1);
            }
        }
    }
}
