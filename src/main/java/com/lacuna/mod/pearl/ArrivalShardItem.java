package com.lacuna.mod.pearl;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Arrival Shard (right half): thrown like a pearl; where it hits a block it locks into an Arrival Anchor. */
public class ArrivalShardItem extends ShardItem {
    public ArrivalShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.PEARL_ENABLED) || PearlLinks.link(stack) == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide()) {
            ItemStack thrown = stack.copyWithCount(1);
            int damage = thrown.getDamageValue() + 1;
            if (!player.hasInfiniteMaterials()) {
                if (damage >= thrown.getMaxDamage()) {
                    stack.shrink(1);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
                            SoundSource.PLAYERS, 1.0F, 0.6F);
                    player.displayClientMessage(Component.translatable("lacuna.pearl.crumbles"), true);
                    return InteractionResultHolder.consume(stack);
                }
                thrown.setDamageValue(damage);
            }
            ArrivalShardEntity entity = new ArrivalShardEntity(level, player);
            entity.setItem(thrown);
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, Balance.ARRIVAL_SHARD_SPEED, 1.0F);
            level.addFreshEntity(entity);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS,
                    0.5F, 0.6F);
            player.getCooldowns().addCooldown(this, Balance.ARRIVAL_SHARD_COOLDOWN);
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
