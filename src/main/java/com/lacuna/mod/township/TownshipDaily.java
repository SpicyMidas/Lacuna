package com.lacuna.mod.township;

import com.lacuna.mod.logic.TownshipLaws;
import net.minecraft.server.level.ServerLevel;

/**
 * Once per in-game day at dawn (checked every 100 ticks by the ledger): samples the reputation of nearby players for
 * the council; council, coin value, price index and schools hook in here (M11c/M11d).
 */
public final class TownshipDaily {
    private TownshipDaily() {}

    static void tick(ServerLevel level, Township t) {
        long day = TownshipService.day(level);
        if (t.lastDailyDay == day || !TownshipLaws.isDawn(level.getDayTime())) {
            return;
        }
        t.lastDailyDay = day;
        sampleReputation(level, t);
        TownshipSavedData.get(level.getServer()).touch();
    }

    static void sampleReputation(ServerLevel level, Township t) {
        var players = level.getPlayers(p -> t.contains(level.dimension(), p.blockPosition()));
        if (players.isEmpty()) {
            return;
        }
        double avg = players.stream().mapToInt(p -> t.reputation(p.getUUID())).average().orElse(0);
        t.weekRepSum += avg;
        t.weekRepSamples++;
    }
}
