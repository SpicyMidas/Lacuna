package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.mob.Gloomcrawler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Gloomcrawler: the vanilla spider model, retextured and scaled 0.8. While crouched in cover it is flattened and
 * drawn with a near-invisible translucent texture; its eyes only glint when it is not hiding.
 */
public class GloomcrawlerRenderer extends MobRenderer<Gloomcrawler, SpiderModel<Gloomcrawler>> {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/entity/gloomcrawler.png");
    private static final ResourceLocation HIDDEN = Lacuna.id("textures/entity/gloomcrawler_hidden.png");
    private static final ResourceLocation EYES = Lacuna.id("textures/entity/gloomcrawler_eyes.png");

    public GloomcrawlerRenderer(EntityRendererProvider.Context context) {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.64F);
        addLayer(new Eyes(this));
    }

    @Override
    protected void scale(Gloomcrawler entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.8F, entity.isCrouched() ? 0.5F : 0.8F, 0.8F);
    }

    @Override
    protected float getFlipDegrees(Gloomcrawler entity) {
        return 180.0F;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(Gloomcrawler entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (entity.isCrouched() && bodyVisible) {
            return RenderType.entityTranslucent(HIDDEN);
        }
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Override
    public ResourceLocation getTextureLocation(Gloomcrawler entity) {
        return TEXTURE;
    }

    static final class Eyes extends EyesLayer<Gloomcrawler, SpiderModel<Gloomcrawler>> {
        private static final RenderType TYPE = RenderType.eyes(EYES);

        Eyes(RenderLayerParent<Gloomcrawler, SpiderModel<Gloomcrawler>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Gloomcrawler entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!entity.isCrouched()) {
                super.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        }

        @Override
        public RenderType renderType() {
            return TYPE;
        }
    }
}
