package com.lacuna.mod.pearl;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModComponents;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Departure Shard (left half): right click teleports the user to the linked anchor, in any dimension and across
 * dimensions. Invalid / missing link: "The shard hums, but nothing answers."; a jump into or out of the Forgotten Land:
 * "The Forgotten Land swallows the call." Neither costs durability nor starts the cooldown.
 */
public class DepartureShardItem extends ShardItem {
    public DepartureShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PearlLink link = PearlLinks.link(stack);
        if (!LacunaConfig.get(LacunaConfig.PEARL_ENABLED) || link == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (PearlLinks.crossesForgottenLand(serverPlayer.server, serverPlayer.level().dimension(), link)) {
            player.displayClientMessage(Component.translatable("lacuna.pearl.forgotten_refuses"), true);
            return InteractionResultHolder.fail(stack);
        }
        Optional<PearlLinks.Target> target = PearlLinks.resolve(serverPlayer.server, serverPlayer.level().dimension(), link);
        if (target.isEmpty() || !PearlLinks.travel(serverPlayer, target.get())) {
            if (!link.cracked()) {
                stack.set(ModComponents.PEARL_LINK.get(), link.withCracked(true));
            }
            player.displayClientMessage(Component.translatable("lacuna.pearl.nothing_answers"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (link.cracked()) {
            stack.set(ModComponents.PEARL_LINK.get(), link.withCracked(false));
        }
        player.getCooldowns().addCooldown(this, Balance.DEPARTURE_SHARD_COOLDOWN);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.success(stack);
    }
}
