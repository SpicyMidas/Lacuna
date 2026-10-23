package com.lacuna.mod.client.star;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.pose.LacunaArmPoses;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * How the Starcaller Trumpet is held. At rest it is gripped by its middle, across the fist and square to the forearm,
 * bell forward (item model display, tools/content/m15_starcaller.py). While blown ({@code lacuna:blowing} switches to
 * the starcaller_trumpet_blowing model) both hands raise it level to the lips: {@link LacunaArmPoses#TRUMPET_BLOW} in
 * third person, and in first person it is lifted from the hand to the middle of the view.
 */
public final class TrumpetClient {
    /** Ticks to lift the trumpet from the hand to the lips in first person. */
    private static final float RAISE_TICKS = 4.0F;

    private TrumpetClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(TrumpetClient::onClientSetup);
        modBus.addListener(TrumpetClient::onRegisterExtensions);
    }

    private static boolean blowing(LivingEntity entity, InteractionHand hand) {
        return entity.isUsingItem() && entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0;
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SafeRun.run("item_properties", () -> ItemProperties.register(ModItems.STARCALLER_TRUMPET.get(),
                Lacuna.id("blowing"), (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F)));
    }

    private static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                // at rest: the plain one-handed hold (null = vanilla ITEM), the display puts the fist mid-trumpet
                return blowing(entity, hand) ? LacunaArmPoses.TRUMPET_BLOW.getValue() : null;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equipProcess, float swingProcess) {
                InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                if (!blowing(player, hand)) {
                    return false; // vanilla hold and swing
                }
                int k = arm == HumanoidArm.RIGHT ? 1 : -1;
                float raise = Mth.clamp((player.getTicksUsingItem() + partialTick) / RAISE_TICKS, 0.0F, 1.0F);
                float ease = 1.0F - (1.0F - raise) * (1.0F - raise);
                // vanilla hand base, then drawn in from the hand toward the middle while it rises
                poseStack.translate(k * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
                poseStack.translate((1.0F - ease) * k * 0.25F, (1.0F - ease) * -0.3F, 0.0F);
                // a faint wobble of breath while it sounds
                poseStack.translate(0.0F, Mth.sin((player.tickCount + partialTick) * 1.3F) * 0.004F * ease, 0.0F);
                return true;
            }
        }, ModItems.STARCALLER_TRUMPET.get());
    }
}
