package com.lacuna.mod.client.chart;

import com.lacuna.mod.chart.WayfarersChartItem;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First person: the Wayfarer's Chart is held exactly like a vanilla filled map (both hands when it is in the main hand
 * and the off hand is empty, otherwise one-handed beside the arm), with {@link ChartPainter} drawing the chart where
 * vanilla draws the map. The transforms are vanilla's {@code ItemInHandRenderer} map ones (private there).
 */
final class ChartHandRenderer {
    static final String FEATURE = "wayfarers_chart_hand";

    private ChartHandRenderer() {}

    static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WayfarersChartItem) || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isScoping()) {
            return;
        }
        event.setCanceled(true);
        SafeRun.run(FEATURE, () -> render(mc, player, event));
    }

    private static void render(Minecraft mc, LocalPlayer player, RenderHandEvent event) {
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        PlayerRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer r ? r : null;
        pose.pushPose();
        if (main && player.getOffhandItem().isEmpty()) {
            twoHanded(pose, buffers, light, player, renderer, event);
        } else {
            oneHanded(pose, buffers, light, player, renderer, arm, event);
        }
        pose.popPose();
    }

    /** ItemInHandRenderer#renderTwoHandedMap. */
    private static void twoHanded(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player, PlayerRenderer renderer,
                                  RenderHandEvent event) {
        float swing = event.getSwingProgress();
        float root = Mth.sqrt(swing);
        pose.translate(0.0F, 0.2F * Mth.sin(swing * Mth.PI) / 2.0F, -0.4F * Mth.sin(root * Mth.PI));
        float tilt = tilt(event.getInterpolatedPitch());
        pose.translate(0.0F, 0.04F + event.getEquipProgress() * -1.2F + tilt * -0.5F, -0.72F);
        pose.mulPose(Axis.XP.rotationDegrees(tilt * -85.0F));
        if (!player.isInvisible() && renderer != null) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            mapHand(pose, buffers, light, player, renderer, HumanoidArm.RIGHT);
            mapHand(pose, buffers, light, player, renderer, HumanoidArm.LEFT);
            pose.popPose();
        }
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(root * Mth.PI) * 20.0F));
        pose.scale(2.0F, 2.0F, 2.0F);
        chart(pose, buffers, light, player, event.getPartialTick());
    }

    /** ItemInHandRenderer#renderOneHandedMap. */
    private static void oneHanded(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player, PlayerRenderer renderer,
                                  HumanoidArm arm, RenderHandEvent event) {
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        pose.translate(f * 0.125F, -0.125F, 0.0F);
        if (!player.isInvisible() && renderer != null) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(f * 10.0F));
            playerArm(pose, buffers, light, player, renderer, equip, swing, arm);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(f * 0.51F, -0.08F + equip * -1.2F, -0.75F);
        float root = Mth.sqrt(swing);
        float lift = Mth.sin(root * Mth.PI);
        pose.translate(f * -0.5F * lift, 0.4F * Mth.sin(root * Mth.TWO_PI) - 0.3F * lift, -0.3F * Mth.sin(swing * Mth.PI));
        pose.mulPose(Axis.XP.rotationDegrees(lift * -45.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * lift * -30.0F));
        chart(pose, buffers, light, player, event.getPartialTick());
        pose.popPose();
    }

    /** ItemInHandRenderer#renderMap's transform into the 128 x 128 map plane, then the chart itself. */
    private static void chart(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player, float partialTick) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(0.38F, 0.38F, 0.38F);
        pose.translate(-0.5F, -0.5F, 0.0F);
        pose.scale(0.0078125F, 0.0078125F, 0.0078125F);
        ChartPainter.paint(pose, buffers, light, player, partialTick);
    }

    /** ItemInHandRenderer#calculateMapTilt: the chart is raised into view as the player looks down. */
    private static float tilt(float pitch) {
        float f = Mth.clamp(1.0F - pitch / 45.0F + 0.1F, 0.0F, 1.0F);
        return -Mth.cos(f * Mth.PI) * 0.5F + 0.5F;
    }

    /** ItemInHandRenderer#renderMapHand: one hand holding the chart's side. */
    private static void mapHand(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player, PlayerRenderer renderer,
                                HumanoidArm arm) {
        pose.pushPose();
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.mulPose(Axis.YP.rotationDegrees(92.0F));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
        pose.translate(f * 0.3F, -1.1F, 0.45F);
        hand(pose, buffers, light, player, renderer, arm);
        pose.popPose();
    }

    /** ItemInHandRenderer#renderPlayerArm: the bare arm beside a one-handed chart. */
    private static void playerArm(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player, PlayerRenderer renderer,
                                  float equip, float swing, HumanoidArm arm) {
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float root = Mth.sqrt(swing);
        pose.translate(f * (-0.3F * Mth.sin(root * Mth.PI) + 0.64000005F), 0.4F * Mth.sin(root * Mth.TWO_PI) - 0.6F + equip * -0.6F,
                -0.4F * Mth.sin(swing * Mth.PI) - 0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * Mth.sin(root * Mth.PI) * 70.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * Mth.sin(swing * swing * Mth.PI) * -20.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);
        hand(pose, buffers, light, player, renderer, arm);
    }

    private static void hand(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player, PlayerRenderer renderer,
                             HumanoidArm arm) {
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(pose, buffers, light, player);
        } else {
            renderer.renderLeftHand(pose, buffers, light, player);
        }
    }
}
