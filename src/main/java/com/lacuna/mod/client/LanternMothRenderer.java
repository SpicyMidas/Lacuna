package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.mob.LanternMoth;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Lantern Moth: the vanilla bee model, retextured, scaled 0.8 and drawn full-bright (it glows). */
public class LanternMothRenderer extends MobRenderer<LanternMoth, BeeModel<LanternMoth>> {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/entity/lantern_moth.png");

    public LanternMothRenderer(EntityRendererProvider.Context context) {
        super(context, new BeeModel<>(context.bakeLayer(ModelLayers.BEE)), 0.3F);
    }

    @Override
    protected void scale(LanternMoth entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.8F, 0.8F, 0.8F);
    }

    @Override
    protected int getBlockLightLevel(LanternMoth entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(LanternMoth entity) {
        return TEXTURE;
    }
}
