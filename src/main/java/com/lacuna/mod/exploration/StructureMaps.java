package com.lacuna.mod.exploration;

import com.lacuna.mod.Lacuna;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structure maps: vanilla filled maps with a target decoration (like buried treasure and explorer maps) leading to the
 * Overworld's important places: the Sunken Bell Tower, the Obelisk of Unreadable Names and the ancient city, where
 * relics wait. Chests give them through the vanilla {@code exploration_map} loot function (destination tags
 * {@code #lacuna:on_<kind>_maps}, tools/content/m21_structure_maps.py); the Last Merchant sells one ({@link MerchantMaps}).
 * This class registers their map decoration types (8x8 sprites in textures/map/decorations, stitched into the vanilla
 * map_decorations atlas) and makes a map for code.
 */
public final class StructureMaps {
    public static final DeferredRegister<MapDecorationType> DECORATIONS = DeferredRegister.create(Registries.MAP_DECORATION_TYPE, Lacuna.MODID);

    /** The places a structure map leads to: id (decoration, sprite and lang key), map colour and zoom (as in the loot). */
    public enum Kind {
        SUNKEN_BELL_TOWER("sunken_bell_tower", 0x5BAF9A, 2),
        OBELISK("obelisk", 0x3A3446, 1),
        ANCIENT_CITY("ancient_city", 0x0F4D57, 2);

        public final String id;
        /** Structure tag searched for (data/lacuna/tags/worldgen/structure/on_<id>_maps.json). */
        public final TagKey<Structure> destination;
        public final DeferredHolder<MapDecorationType, MapDecorationType> decoration;
        final byte zoom;

        Kind(String id, int mapColor, int zoom) {
            this.id = id;
            this.destination = TagKey.create(Registries.STRUCTURE, Lacuna.id("on_" + id + "_maps"));
            // like the woodland mansion's: shown in item frames, tints the map item, counted against the decoration cap
            this.decoration = DECORATIONS.register(id, () -> new MapDecorationType(Lacuna.id(id), true, mapColor, false, true));
            this.zoom = (byte) zoom;
        }

        public String nameKey() {
            return "filled_map.lacuna." + id;
        }
    }

    private StructureMaps() {}

    public static void register(IEventBus modBus) {
        // class-load the kinds (they register their decorations) before the register is attached
        Kind.values();
        DECORATIONS.register(modBus);
    }

    /**
     * A filled map centred on the nearest {@code kind} structure within {@code radius} chunks of {@code from} that no map
     * has shown yet (vanilla's bounded search, as the cartographer's maps), or null when there is none.
     */
    @Nullable
    public static ItemStack create(ServerLevel level, BlockPos from, Kind kind, int radius) {
        BlockPos found = level.findNearestMapStructure(kind.destination, from, radius, true);
        if (found == null) {
            return null;
        }
        // the start chunk's centre: a chunk corner can fall on the map's very edge, where vanilla drops the decoration
        BlockPos target = new BlockPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(found.getX()), 8), found.getY(),
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(found.getZ()), 8));
        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), kind.zoom, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", kind.decoration);
        map.set(DataComponents.ITEM_NAME, Component.translatable(kind.nameKey()));
        map.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable(kind.nameKey() + ".lore").withStyle(ChatFormatting.GRAY))));
        return map;
    }
}
