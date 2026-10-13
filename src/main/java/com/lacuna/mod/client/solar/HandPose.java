package com.lacuna.mod.client.solar;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Where a point of the first-person held item appears in the world, so light can start exactly at the drawn item:
 * the hand pose of {@code ItemInHandRenderer} for the SPEAR and BOW use animations is replayed in camera space
 * (hand sway, arm transform, pull-back, the item model's first-person display transform), then placed back into the
 * world along the same screen ray (the hand is drawn with a fixed 70 degree field of view, the world with the
 * player's own).
 */
final class HandPose {
    /** First-person display of {@code lacuna:item/suns_fury_throwing} (tools/content/m04_eclipse_solar.py). */
    static final float[] SPEAR_ROT = {-175.0F, 58.6F, 216.5F};
    static final float[] SPEAR_POS = {5.36F, 4.45F, -9.84F};
    static final float SPEAR_SCALE = 0.85F;
    /** First-person display of {@code minecraft:item/handheld} (the Divine Magnifying Glass). */
    static final float[] HANDHELD_ROT = {0.0F, -90.0F, 25.0F};
    static final float[] HANDHELD_POS = {1.13F, 3.2F, 1.13F};
    static final float HANDHELD_SCALE = 0.68F;

    private static double worldFov = 70.0;
    private static double handFov = 70.0;

    private HandPose() {}

    static void register(IEventBus forgeBus) {
        forgeBus.addListener(EventPriority.LOWEST, HandPose::onComputeFov);
    }

    /** Remembers the final world and hand fields of view (every frame; the hand one lags a frame behind). */
    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            worldFov = event.getFOV();
        } else {
            handFov = event.getFOV();
        }
    }

    static int side(Player player) {
        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    /** Sun's Fury drawn back (UseAnim.SPEAR): world position of sprite pixel ({@code px}, {@code py}), front face. */
    static Vec3 spearPixel(LocalPlayer player, Camera camera, float pt, float px, float py) {
        int k = side(player);
        Matrix4f m = sway(player, pt);
        m.translate(0.56F * k, -0.52F, -0.72F);
        m.translate(-0.5F * k, 0.7F, 0.1F);
        m.rotateX(-55.0F * Mth.DEG_TO_RAD);
        m.rotateY(35.3F * k * Mth.DEG_TO_RAD);
        m.rotateZ(-9.785F * k * Mth.DEG_TO_RAD);
        float used = player.getTicksUsingItem() + pt;
        float pull = Math.min(1.0F, used / 10.0F);
        if (pull > 0.1F) {
            m.translate(0.0F, Mth.sin((used - 0.1F) * 1.3F) * (pull - 0.1F) * 0.004F, 0.0F);
        }
        m.translate(0.0F, 0.0F, pull * 0.2F);
        m.scale(1.0F, 1.0F, 1.0F + pull * 0.2F);
        m.rotateY(-45.0F * k * Mth.DEG_TO_RAD);
        display(m, SPEAR_ROT, SPEAR_POS, SPEAR_SCALE, k, true);
        return toWorld(camera, pixel(m, px, py));
    }

    /** Divine Magnifying Glass raised (UseAnim.BOW): world position of sprite pixel ({@code px}, {@code py}), front face. */
    static Vec3 bowPixel(LocalPlayer player, Camera camera, float pt, float px, float py) {
        int k = side(player);
        Matrix4f m = sway(player, pt);
        m.translate(0.56F * k, -0.52F, -0.72F);
        m.translate(-0.2785682F * k, 0.18344387F, 0.15731531F);
        m.rotateX(-13.935F * Mth.DEG_TO_RAD);
        m.rotateY(35.3F * k * Mth.DEG_TO_RAD);
        m.rotateZ(-9.785F * k * Mth.DEG_TO_RAD);
        float used = player.getTicksUsingItem() + pt;
        float pull = used / 20.0F;
        pull = Math.min(1.0F, (pull * pull + pull * 2.0F) / 3.0F);
        if (pull > 0.1F) {
            m.translate(0.0F, Mth.sin((used - 0.1F) * 1.3F) * (pull - 0.1F) * 0.004F, 0.0F);
        }
        m.translate(0.0F, 0.0F, pull * 0.04F);
        m.scale(1.0F, 1.0F, 1.0F + pull * 0.2F);
        m.rotateY(-45.0F * k * Mth.DEG_TO_RAD);
        display(m, HANDHELD_ROT, HANDHELD_POS, HANDHELD_SCALE, k, false);
        return toWorld(camera, pixel(m, px, py));
    }

    /** The lag sway {@code renderHandsWithItems} applies before everything else. */
    private static Matrix4f sway(LocalPlayer player, float pt) {
        float xBob = Mth.lerp(pt, player.xBobO, player.xBob);
        float yBob = Mth.lerp(pt, player.yBobO, player.yBob);
        return new Matrix4f()
                .rotateX((player.getViewXRot(pt) - xBob) * 0.1F * Mth.DEG_TO_RAD)
                .rotateY((player.getViewYRot(pt) - yBob) * 0.1F * Mth.DEG_TO_RAD);
    }

    /**
     * {@code ItemTransform.apply} with the right-hand entry, then the model's -0.5 centring. The left hand negates the
     * x translation and, when its JSON entry equals the right-hand one ({@code mirrored}), the y/z rotation too;
     * vanilla {@code item/handheld} lists a pre-mirrored left entry, so its applied rotation equals the right one.
     */
    private static void display(Matrix4f m, float[] rot, float[] pos, float scale, int k, boolean mirrored) {
        int r = mirrored ? k : 1;
        m.translate(k * pos[0] / 16.0F, pos[1] / 16.0F, pos[2] / 16.0F);
        m.rotateXYZ(rot[0] * Mth.DEG_TO_RAD, r * rot[1] * Mth.DEG_TO_RAD, r * rot[2] * Mth.DEG_TO_RAD);
        m.scale(scale);
        m.translate(-0.5F, -0.5F, -0.5F);
    }

    /** Camera-space position of sprite pixel (px, py) (texture coordinates, y down) on the front face. */
    private static Vector3f pixel(Matrix4f m, float px, float py) {
        Vector4f v = m.transform(new Vector4f(px / 16.0F, 1.0F - py / 16.0F, 8.5F / 16.0F, 1.0F));
        return new Vector3f(v.x, v.y, v.z);
    }

    /**
     * Camera-space point drawn with the hand's projection → the world point on the same screen ray at the same depth.
     */
    static Vec3 toWorld(Camera camera, Vector3f p) {
        float depth = Math.max(0.06F, -p.z);
        double hand = Math.tan(Math.toRadians(handFov) * 0.5);
        double world = Math.tan(Math.toRadians(worldFov) * 0.5);
        double ratio = hand <= 1.0E-4 ? 1.0 : world / hand;
        double x = p.x * ratio;
        double y = p.y * ratio;
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        return camera.getPosition()
                .add(look.x() * depth, look.y() * depth, look.z() * depth)
                .add(-left.x() * x, -left.y() * x, -left.z() * x)
                .add(up.x() * y, up.y() * y, up.z() * y);
    }
}
