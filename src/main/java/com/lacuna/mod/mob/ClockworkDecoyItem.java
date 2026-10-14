package com.lacuna.mod.mob;

import com.lacuna.mod.Balance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Clockwork Decoy: thrown like a snowball, 400 tick cooldown. */
public class ClockworkDecoyItem extends Item {
    public ClockworkDecoyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.6F);
        // the spring is wound as it leaves the hand
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
        if (!level.isClientSide()) {
            ThrownDecoyEntity thrown = new ThrownDecoyEntity(level, player);
            thrown.setItem(stack.copyWithCount(1));
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.0F, 1.0F);
            level.addFreshEntity(thrown);
        }
        player.getCooldowns().addCooldown(this, Balance.DECOY_COOLDOWN);
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
