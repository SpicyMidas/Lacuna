package com.lacuna.mod.client.eclipse;

import com.lacuna.mod.client.fx.FxRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Dark translucent copies of a player model frozen in a dash pose (Eclipser afterimages). Uses private model
 * instances (never the live player renderer's model) and the emissive translucent entity type, so the copies stay
 * a flat dark silhouette whatever the light, and overlapping parts blend like smoke.
 */
final class Afterimage {
    private static PlayerModel<AbstractClientPlayer> wide;
    private static PlayerModel<AbstractClientPlayer> slim;

    private Afterimage() {}

    private static PlayerModel<AbstractClientPlayer> model(boolean slimArms) {
        if (wide == null || slim == null) {
            EntityModelSet models = Minecraft.getInstance().getEntityModels();
            wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        return slimArms ? slim : wide;
    }

    static void reset() {
        wide = null;
        slim = null;
    }

    /** Sword arm swept back, body forward, legs in a long stride; {@code variant} mirrors the stride. */
    private static void pose(PlayerModel<AbstractClientPlayer> m, float leanRad, boolean variant) {
        m.young = false;
        m.riding = false;
        m.crouching = false;
        m.attackTime = 0.0F;
        m.setAllVisible(true);
        m.head.resetPose();
        m.body.resetPose();
        m.rightArm.resetPose();
        m.leftArm.resetPose();
        m.rightLeg.resetPose();
        m.leftLeg.resetPose();
        m.head.xRot = -leanRad * 0.8F;
        m.rightArm.xRot = 1.15F;
        m.rightArm.zRot = 0.35F;
        m.leftArm.xRot = variant ? -0.5F : 0.75F;
        m.leftArm.zRot = -0.3F;
        m.rightLeg.xRot = variant ? 0.75F : -0.65F;
        m.leftLeg.xRot = variant ? -0.65F : 0.75F;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /**
     * Draws one afterimage standing at {@code feet}, facing {@code yawDeg} and leaning forward by {@code leanDeg}.
     * {@code argb} tints the skin (keep it dark) and carries the translucency.
     */
    static void render(FxRenderContext ctx, Vec3 feet, float yawDeg, float leanDeg, boolean variant, int argb,
                       ResourceLocation skin, boolean slimArms) {
        PlayerModel<AbstractClientPlayer> m = model(slimArms);
        pose(m, (float) Math.toRadians(leanDeg), variant);
        PoseStack ps = ctx.pose();
        ps.pushPose();
        Vec3 r = ctx.rel(feet);
        ps.translate(r.x, r.y, r.z);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yawDeg));
        ps.translate(0.0F, 0.9F, 0.0F);
        ps.mulPose(Axis.XP.rotationDegrees(-leanDeg));
        ps.translate(0.0F, -0.9F, 0.0F);
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer vc = ctx.buffers().getBuffer(RenderType.entityTranslucentEmissive(skin, false));
        m.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
        ps.popPose();
    }
}
