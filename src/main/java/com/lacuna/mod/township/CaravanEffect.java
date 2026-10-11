package com.lacuna.mod.township;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** M11d hook: a Last Merchant killed near a township raises its price index (filled in by M11d). */
final class CaravanEffect {
    private CaravanEffect() {}

    static void onDeath(ServerLevel level, Entity victim) {
        // implemented in M11d
    }
}
