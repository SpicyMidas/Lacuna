package com.lacuna.mod.command;

import com.lacuna.mod.banish.BanishmentSavedData;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModTags;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

/** Milestone-specific selftest checks; grows with each milestone. */
final class ExtraChecks {
    private ExtraChecks() {}

    static void registries(List<String> failures, int[] checks) {}

    static void tags(List<String> failures, int[] checks) {
        checks[0]++;
        if (BuiltInRegistries.BLOCK.getTag(ModTags.Blocks.RUIN_BLOCKS).map(s -> s.size() == 0).orElse(true)) {
            failures.add("empty block tag lacuna:ruin_blocks");
        }
        checks[0]++;
        if (BuiltInRegistries.ENTITY_TYPE.getTag(ModTags.Entities.CANNOT_BE_BANISHED).map(s -> s.size() == 0).orElse(true)) {
            failures.add("empty entity tag lacuna:cannot_be_banished");
        }
    }

    static void world(MinecraftServer server, List<String> failures, int[] checks) {
        checks[0]++;
        if (ModDimensions.forgottenLand(server) == null) {
            failures.add("dimension lacuna:forgotten_land is missing");
        }
        checks[0]++;
        try {
            BanishmentSavedData.get(server);
        } catch (RuntimeException e) {
            failures.add("banishment data unreadable: " + e.getMessage());
        }
    }
}
