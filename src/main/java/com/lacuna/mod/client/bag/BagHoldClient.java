package com.lacuna.mod.client.bag;

import com.lacuna.mod.bag.BagCapture;
import com.lacuna.mod.client.pose.LacunaArmPoses;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How the Comically Large Bag is held, cartoon style, always with both hands.
 * <ul>
 * <li>Empty: held open out in front, arms raised forward, ready to catch something ({@link LacunaArmPoses#BAG_OPEN}).
 * In first person both arms are drawn holding the mouth of the sack up at the bottom of the view.</li>
 * <li>Full / stuffed: slung over the shoulder like a thief's loot, both hands on its neck
 * ({@link LacunaArmPoses#BAG_CARRY}); in first person it rides at the edge of the view (item model display).</li>
 * </ul>
 * Where the bag sits in each hold is the item models' display (tools/content/m15_bag.py).
 */
public final class BagHoldClient {
    // first person, empty bag: where the hands grip the rim of the sack (view space, blocks; right arm, x mirrored)
    private static final Vector3f HAND = new Vector3f(0.36F, -0.31F, -1.28F);
    // and where the arms come from (the shoulders, just off the bottom corners of the view)
    private static final Vector3f SHOULDER = new Vector3f(0.75F, -1.0F, -0.45F);

    private BagHoldClient() {}

    static void register(IEventBus modBus) {
        modBus.addListener(BagHoldClient::onRegisterExtensions);
        NeoForge.EVENT_BUS.addListener(BagHoldClient::onRenderHand);
    }

    private static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return BagCapture.fullness(stack) == 0 ? LacunaArmPoses.BAG_OPEN.getValue() : LacunaArmPoses.BAG_CARRY.getValue();
            }
        }, ModItems.COMICALLY_LARGE_BAG.get());
    }

    /** First person, empty bag in the main hand: both arms up holding it open (vanilla draws an item with no arm). */
    private static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND || !stack.is(ModItems.COMICALLY_LARGE_BAG.get()) || BagCapture.fullness(stack) != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isScoping()) {
            return;
        }
        event.setCanceled(true);
        SafeRun.run("bag_hands", () -> renderHeldOpen(mc, player, stack, event));
    }

    private static void renderHeldOpen(Minecraft mc, LocalPlayer player, ItemStack stack, RenderHandEvent event) {
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        HumanoidArm main = player.getMainArm();
        int k = main == HumanoidArm.RIGHT ? 1 : -1;
        float swing = event.getSwingProgress();
        float root = Mth.sqrt(swing);
        pose.pushPose();
        // a softer version of the vanilla swing (both hands push the sack forward), and the equip slide from below
        pose.translate(k * -0.2F * Mth.sin(root * Mth.PI), 0.1F * Mth.sin(root * Mth.TWO_PI), -0.3F * Mth.sin(swing * Mth.PI));
        pose.translate(0.0F, event.getEquipProgress() * -0.6F, 0.0F);
        if (!player.isInvisible() && mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
            arm(pose, buffers, light, player, renderer, main);
            if (player.getOffhandItem().isEmpty()) {
                arm(pose, buffers, light, player, renderer, main.getOpposite());
            }
        }
        pose.pushPose();
        pose.translate(k * 0.56F, -0.52F, -0.72F); // the vanilla hand base the first-person display is written for
        mc.gameRenderer.itemInHandRenderer.renderItem(player, stack,
                k == 1 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                k != 1, pose, buffers, light);
        pose.popPose();
        pose.popPose();
    }

    /** One arm, the vanilla first-person arm swung rigidly so its hand grips the rim of the sack. */
    private static void arm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                            PlayerRenderer renderer, HumanoidArm side) {
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        Matrix4f vanilla = vanillaArm(f);
        // arm model space: pivot (-5, 2, 0) px, fist centre 8 px down the arm, shoulder at its top (x mirrored)
        Vector3f hand = vanilla.transformPosition(new Vector3f(f * -6.0F / 16.0F, 10.0F / 16.0F, 0.0F));
        Vector3f shoulder = vanilla.transformPosition(new Vector3f(f * -6.0F / 16.0F, 0.0F, 0.0F));
        Vector3f target = new Vector3f(f * HAND.x, HAND.y, HAND.z);
        Vector3f from = new Vector3f(hand).sub(shoulder).normalize();
        Vector3f to = new Vector3f(target).sub(f * SHOULDER.x, SHOULDER.y, SHOULDER.z).normalize();
        pose.pushPose();
        pose.translate(target.x, target.y, target.z);
        pose.mulPose(new Quaternionf().rotationTo(from, to));
        pose.translate(-hand.x, -hand.y, -hand.z);
        pose.mulPose(vanilla);
        if (f > 0) {
            renderer.renderRightHand(pose, buffers, light, player);
        } else {
            renderer.renderLeftHand(pose, buffers, light, player);
        }
        pose.popPose();
    }

    /** ItemInHandRenderer#renderPlayerArm's transform with no swing and no equip offset. */
    private static Matrix4f vanillaArm(float f) {
        PoseStack p = new PoseStack();
        p.translate(f * 0.64000005F, -0.6F, -0.71999997F);
        p.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        p.translate(f * -1.0F, 3.6F, 3.5F);
        p.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        p.mulPose(Axis.XP.rotationDegrees(200.0F));
        p.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        p.translate(f * 5.6F, 0.0F, 0.0F);
        return new Matrix4f(p.last().pose());
    }
}
