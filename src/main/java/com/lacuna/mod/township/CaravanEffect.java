package com.lacuna.mod.township;

import com.lacuna.mod.merchant.LastMerchant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Caravan effect (spec 18.4): a Last Merchant killed within 128 blocks of a township raises that township's price
 * index by 0.2 (max 1.5); it decays by 0.03 per day back toward 1.0.
 */
public final class CaravanEffect {
    public static final double RANGE = 128.0;

    private CaravanEffect() {}

    static void onDeath(ServerLevel level, Entity victim) {
        if (!(victim instanceof LastMerchant)) {
            return;
        }
        TownshipSavedData data = TownshipSavedData.get(level.getServer());
        for (Township t : data.all()) {
            if (t.dimension.equals(level.dimension()) && t.horizontalDistance(victim.blockPosition()) <= RANGE) {
                t.priceIndex = raise(t.priceIndex);
                data.touch();
            }
        }
    }

    public static double raise(double index) {
        return Math.min(1.5, index + 0.2);
    }

    public static double decay(double index) {
        if (index <= 1.0) {
            return 1.0;
        }
        return Math.max(1.0, index - 0.03);
    }

    /** Price index for a Last Merchant spawned near a township (1.0 when none is within range). */
    public static double indexNear(ServerLevel level, net.minecraft.core.BlockPos pos) {
        double best = 1.0;
        double bestDist = Double.MAX_VALUE;
        for (Township t : TownshipSavedData.get(level.getServer()).all()) {
            double d = t.horizontalDistance(pos);
            if (t.dimension.equals(level.dimension()) && d <= RANGE && d < bestDist) {
                bestDist = d;
                best = t.priceIndex;
            }
        }
        return best;
    }
}
