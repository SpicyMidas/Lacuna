package com.lacuna.mod.bag;

import com.lacuna.mod.util.SafeRun;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Comically Large Bag: holds exactly one creature, cart, boat or block, with all of its data, and lets it back out
 * as the very same thing. Empty: use on an entity to bag it ({@link BagEvents}), sneak + use on a block to bag the
 * block. Full: use on any block face to let the contents out there. See {@link BagCapture} for the rules.
 */
public class ComicallyLargeBagItem extends Item {
    public ComicallyLargeBagItem(Properties properties) {
        super(properties);
    }

    /** Runs before the clicked block reacts, so a full bag empties even onto a chest instead of opening it. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        boolean full = BagCapture.isFull(stack);
        if (!full && !player.isSecondaryUseActive()) {
            return InteractionResult.PASS; // empty bag, no sneak: the block behaves as usual (chests open, etc.)
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return SafeRun.call(BagCapture.FEATURE, () -> full ? BagCapture.release(serverPlayer, stack, context)
                : BagCapture.captureBlock(serverPlayer, stack, context.getClickedPos(), context.getClickedFace()), InteractionResult.FAIL);
    }

    /** A full bag never goes into shulker boxes or bundles: nesting stuff is how bags stop being large. */
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return !BagCapture.isFull(stack);
    }

    /** A dropped full bag shrugs off lava, fire, cactus and explosions. */
    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return !BagCapture.isFull(stack);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        SafeRun.run(BagCapture.FEATURE, () -> BagCapture.guardDropped(stack, entity));
        return false;
    }

    /** A bag still holding a player whose carry is over (let out, or lost to a crash) turns back into an empty bag. */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && BagCapture.isFull(stack)) {
            SafeRun.run(BaggedPlayers.FEATURE, () -> BaggedPlayers.checkToken(stack));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.comically_large_bag.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.comically_large_bag.use").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.lacuna.comically_large_bag.use2").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.lacuna.comically_large_bag.use3").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.lacuna.comically_large_bag.use4").withStyle(ChatFormatting.AQUA));
    }
}
