package com.lacuna.mod.command;

import com.lacuna.mod.banish.BanishmentSavedData;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModTags;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/** Milestone-specific selftest checks; grows with each milestone. */
final class ExtraChecks {
    private ExtraChecks() {}

    static void registries(List<String> failures, int[] checks) {
        // M9: every living Lacuna mob needs an attribute table (a missing one crashes on spawn)
        List<EntityType<?>> living = List.of(ModEntities.ECHO.get(), ModEntities.LANTERN_MOTH.get(), ModEntities.GLOOMCRAWLER.get(),
                ModEntities.CLOCKWORK_HOUND.get(), ModEntities.DECOY.get());
        for (EntityType<?> type : living) {
            checks[0]++;
            if (!DefaultAttributes.hasSupplier(type)) {
                failures.add("no attributes for " + BuiltInRegistries.ENTITY_TYPE.getKey(type));
            }
        }
    }

    static void tags(List<String> failures, int[] checks) {
        checks[0]++;
        if (BuiltInRegistries.BLOCK.getTag(ModTags.Blocks.RUIN_BLOCKS).map(s -> s.size() == 0).orElse(true)) {
            failures.add("empty block tag lacuna:ruin_blocks");
        }
        checks[0]++;
        if (BuiltInRegistries.ENTITY_TYPE.getTag(ModTags.Entities.CANNOT_BE_BANISHED).map(s -> s.size() == 0).orElse(true)) {
            failures.add("empty entity tag lacuna:cannot_be_banished");
        }
        for (var tag : List.of(ModTags.Items.MOTH_ATTRACTOR, ModTags.Items.LIGHT_SOURCES)) {
            checks[0]++;
            if (BuiltInRegistries.ITEM.getTag(tag).map(s -> s.size() == 0).orElse(true)) {
                failures.add("empty item tag " + tag.location());
            }
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
