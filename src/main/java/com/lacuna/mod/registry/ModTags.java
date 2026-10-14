package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Tag keys (spec section 21). Contents are written by tools/gen_resources.py. */
public final class ModTags {
    private ModTags() {}

    public static final class Blocks {
        public static final TagKey<Block> DIVINE_SIGHT_TARGETS = tag("divine_sight_targets");
        public static final TagKey<Block> DIVINE_GLASS = tag("divine_glass");
        public static final TagKey<Block> RUIN_BLOCKS = tag("ruin_blocks");
        public static final TagKey<Block> SOLAR_IMMUNE = tag("solar_immune");
        public static final TagKey<Block> NOT_MELTABLE = tag("not_meltable");
        public static final TagKey<Block> VILLAGE_PROTECTED = tag("village_protected");
        public static final TagKey<Block> C_ORES = TagKey.create(Registries.BLOCK,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ores"));

        private static TagKey<Block> tag(String path) {
            return TagKey.create(Registries.BLOCK, Lacuna.id(path));
        }

        private Blocks() {}
    }

    public static final class Items {
        public static final TagKey<Item> MOTH_ATTRACTOR = tag("moth_attractor");
        public static final TagKey<Item> DRAWN_WEAPONS = tag("drawn_weapons");
        public static final TagKey<Item> UMBRAL_GEAR = tag("umbral_gear");
        public static final TagKey<Item> HEARTWOOD_REPAIR = tag("heartwood_bow_repair");

        private static TagKey<Item> tag(String path) {
            return TagKey.create(Registries.ITEM, Lacuna.id(path));
        }

        private Items() {}
    }

    public static final class Entities {
        public static final TagKey<EntityType<?>> CANNOT_BE_BANISHED = tag("cannot_be_banished");
        public static final TagKey<EntityType<?>> REFLECTABLE = tag("reflectable");
        public static final TagKey<EntityType<?>> UNREFLECTABLE = tag("unreflectable");

        private static TagKey<EntityType<?>> tag(String path) {
            return TagKey.create(Registries.ENTITY_TYPE, Lacuna.id(path));
        }

        private Entities() {}
    }
}
