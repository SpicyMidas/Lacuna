package com.lacuna.mod.client.illusioner;

import com.lacuna.mod.illusioner.IllusionerDouble;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * An illusioner's duplicate, drawn exactly as the vanilla illusioner renderer draws the real one: the illusioner model
 * and texture (Lacuna's starcloth robe and hood), the hood (hat) part shown, the bow in hand only while aiming or
 * casting, and the same starlight eyes.
 */
public class IllusionerDoubleRenderer extends IllagerRenderer<IllusionerDouble> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/illager/illusioner.png");

    public IllusionerDoubleRenderer(EntityRendererProvider.Context context) {
        super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.ILLUSIONER)), 0.5F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, IllusionerDouble entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (entity.isCastingSpell() || entity.isAggressive()) {
                    super.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw,
                            headPitch);
                }
            }
        });
        addLayer(new IllusionerEyesLayer<>(this));
        model.getHat().visible = true;
    }

    @Override
    public ResourceLocation getTextureLocation(IllusionerDouble entity) {
        return TEXTURE;
    }
}
