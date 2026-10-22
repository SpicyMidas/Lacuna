package com.lacuna.mod.village;

import com.lacuna.mod.logic.ReputationMath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Judge's bench (spec 18.3): right click opens the court screen (standing, fine, pardon); paying the fine there uses
 * village coins from the inventory (+5 reputation per coin, never above 0, one pardon every 3 in-game days).
 */
public class JudgeBenchBlock extends CivicBlock {
    static final int PARDON_COOLDOWN_DAYS = 3;

    public JudgeBenchBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    /** Days until the court hears the player again (0 = now). */
    static long pardonWait(ServerLevel level, VillageRecord t, ServerPlayer player) {
        return Math.max(0, t.lastPardonDay(player.getUUID()) + PARDON_COOLDOWN_DAYS - VillageService.day(level));
    }

    static CivicScreens.Result pay(ServerLevel level, VillageRecord t, ServerPlayer player, BlockPos pos) {
        long today = VillageService.day(level);
        if (today - t.lastPardonDay(player.getUUID()) < PARDON_COOLDOWN_DAYS) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.court.too_soon"));
        }
        int rep = t.reputation(player.getUUID());
        if (ReputationMath.coinsForFine(rep, Integer.MAX_VALUE) <= 0) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.court.nothing_owed"));
        }
        int used = ReputationMath.coinsForFine(rep, CivicScreens.coins(player));
        if (used <= 0) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.civic.court.no_coins"));
        }
        CivicScreens.takeCoins(player, used);
        VillageService.set(level, t, player.getUUID(), Math.min(0, rep + used * ReputationMath.Change.FINE_PAID.amount));
        t.setLastPardonDay(player.getUUID(), today);
        level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 1.0F, 1.0F);
        return CivicScreens.Result.ok(Component.translatable("lacuna.court.paid", used, t.reputation(player.getUUID())));
    }
}
