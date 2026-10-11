package com.lacuna.mod.township;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.TownshipLaws;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Township queries and reputation changes (all callers run inside SafeRun). */
public final class TownshipService {
    public static final String FEATURE = "townships";

    private TownshipService() {}

    public static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.TOWNSHIP_ENABLED);
    }

    public static Optional<Township> at(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return Optional.empty();
        }
        return TownshipSavedData.get(level.getServer()).at(level.dimension(), pos);
    }

    public static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    public static ReputationMath.Tier tier(Township t, UUID player) {
        return ReputationMath.tier(t.reputation(player));
    }

    /** Gain multiplier of the current policy (Festival doubles gains, M11c). */
    public static double gainMultiplier(Township t) {
        return "festival".equals(t.policy) ? 2.0 : 1.0;
    }

    /** Villager price factor for a player: reputation x township price index x policy (M11c). */
    public static double priceFactor(Township t, UUID player) {
        double policy = switch (t.policy) {
            case "free_market" -> 0.90;
            case "austerity" -> 1.10;
            default -> 1.0;
        };
        return ReputationMath.priceMultiplier(t.reputation(player)) * t.priceIndex * policy;
    }

    /** Applies {@code count} times a change, honouring daily caps; returns the applied delta. */
    public static int change(ServerLevel level, Township t, ServerPlayer player, ReputationMath.Change change, int count) {
        if (player.isCreative() || player.isSpectator()) {
            return 0;
        }
        Township.Daily daily = t.daily(player.getUUID(), day(level));
        int used = daily.used.getOrDefault(change, 0);
        int allowed = ReputationMath.allowed(change.amount * count, used, change.dailyCap);
        if (allowed == 0) {
            return 0;
        }
        int before = t.reputation(player.getUUID());
        int after = ReputationMath.apply(before, allowed, gainMultiplier(t));
        daily.used.put(change, used + Math.abs(allowed));
        t.setReputation(player.getUUID(), after);
        TownshipSavedData.get(level.getServer()).touch();
        if (after != before) {
            ChatFormatting color = after > before ? ChatFormatting.GREEN : ChatFormatting.RED;
            player.displayClientMessage(Component.translatable("lacuna.township.rep_change", signed(after - before), after,
                    Component.translatable("lacuna.township.tier." + ReputationMath.tier(after).name().toLowerCase())).withStyle(color), true);
        }
        return after - before;
    }

    public static void set(ServerLevel level, Township t, UUID player, int value) {
        t.setReputation(player, value);
        TownshipSavedData.get(level.getServer()).touch();
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    /** Chat summary shown by the ledger and {@code /lacuna township info}. */
    public static void describe(ServerLevel level, Township t, ServerPlayer player) {
        int rep = t.reputation(player.getUUID());
        long today = day(level);
        player.sendSystemMessage(Component.translatable("lacuna.township.header", t.id.toString().substring(0, 8)).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("lacuna.township.reputation", rep,
                Component.translatable("lacuna.township.tier." + ReputationMath.tier(rep).name().toLowerCase())));
        player.sendSystemMessage(Component.translatable("lacuna.township.laws",
                Component.translatable("lacuna.township.law." + t.laws[0]), Component.translatable("lacuna.township.law." + t.laws[1])));
        long council = t.nextCouncilDay < 0 ? 0 : Math.max(0, t.nextCouncilDay - today);
        player.sendSystemMessage(Component.translatable("lacuna.township.policy",
                Component.translatable("lacuna.township.policy." + t.policy), council));
        player.sendSystemMessage(Component.translatable("lacuna.township.coin", String.format("%.2f", t.coinValue),
                String.format("%.2f", t.priceIndex)));
    }

    public static boolean isGateOpenTime(Level level) {
        return TownshipLaws.gatesOpen(level.getDayTime());
    }
}
