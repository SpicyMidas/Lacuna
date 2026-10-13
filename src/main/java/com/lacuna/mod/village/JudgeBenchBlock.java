package com.lacuna.mod.village;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Judge's bench (spec 18.3): with village coins in hand pays fines (+5 reputation per coin, never above 0, one
 * pardon every 3 in-game days); with an empty hand shows the player's standing and the fine.
 */
public class JudgeBenchBlock extends CivicBlock {
    private static final int PARDON_COOLDOWN_DAYS = 3;

    public JudgeBenchBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.VILLAGE_COIN.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            SafeRun.run(VillageService.FEATURE, () -> VillageService.atOrTell(server, pos, sp).ifPresent(t -> pay(server, t, sp, stack, pos)));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            SafeRun.run(VillageService.FEATURE, () -> VillageService.atOrTell(server, pos, sp).ifPresent(t -> {
                int rep = t.reputation(sp.getUUID());
                int fine = ReputationMath.coinsForFine(rep, Integer.MAX_VALUE);
                long wait = Math.max(0, t.lastPardonDay(sp.getUUID()) + PARDON_COOLDOWN_DAYS - VillageService.day(server));
                sp.sendSystemMessage(Component.translatable("lacuna.court.standing", rep,
                        VillageService.tierName(rep), fine, wait));
            }));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void pay(ServerLevel level, VillageRecord t, ServerPlayer player, ItemStack coins, BlockPos pos) {
        long today = VillageService.day(level);
        if (today - t.lastPardonDay(player.getUUID()) < PARDON_COOLDOWN_DAYS) {
            player.displayClientMessage(Component.translatable("lacuna.court.too_soon").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int rep = t.reputation(player.getUUID());
        int used = ReputationMath.coinsForFine(rep, coins.getCount());
        if (used <= 0) {
            player.displayClientMessage(Component.translatable("lacuna.court.nothing_owed"), true);
            return;
        }
        coins.consume(used, player);
        VillageService.set(level, t, player.getUUID(), Math.min(0, rep + used * ReputationMath.Change.FINE_PAID.amount));
        t.setLastPardonDay(player.getUUID(), today);
        player.sendSystemMessage(Component.translatable("lacuna.court.paid", used, t.reputation(player.getUUID())).withStyle(ChatFormatting.GREEN));
        level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
