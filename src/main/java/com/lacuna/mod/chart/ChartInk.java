package com.lacuna.mod.chart;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * How the chart sketches land nobody has surveyed: each biome is drawn with the map colour of the ground it is mostly
 * made of, salted with a second colour (trees on grass, cacti in sand, water in swamps...). Biomes are recognised by
 * their {@code c:} tags, so modded biomes find a sensible ink too; anything unknown is drawn as grassland.
 */
public enum ChartInk {
    WATER(MapColor.WATER, null, 0.0F, true),
    FROZEN_WATER(MapColor.ICE, MapColor.WATER, 0.3F, false),
    BEACH(MapColor.SAND, null, 0.0F, false),
    SNOWY_BEACH(MapColor.SNOW, MapColor.SAND, 0.3F, false),
    STONY_SHORE(MapColor.STONE, null, 0.0F, false),
    DESERT(MapColor.SAND, MapColor.PLANT, 0.02F, false),
    BADLANDS(MapColor.COLOR_ORANGE, MapColor.TERRACOTTA_ORANGE, 0.35F, false),
    MUSHROOM(MapColor.COLOR_PURPLE, MapColor.COLOR_RED, 0.08F, false),
    ICE(MapColor.ICE, MapColor.SNOW, 0.4F, false),
    SNOW(MapColor.SNOW, null, 0.0F, false),
    SNOWY_FOREST(MapColor.SNOW, MapColor.PLANT, 0.35F, false),
    PEAKS(MapColor.STONE, MapColor.SNOW, 0.15F, false),
    WINDSWEPT(MapColor.GRASS, MapColor.STONE, 0.35F, false),
    SWAMP(MapColor.PLANT, MapColor.WATER, 0.35F, false),
    JUNGLE(MapColor.PLANT, MapColor.GRASS, 0.12F, false),
    SAVANNA(MapColor.GRASS, MapColor.DIRT, 0.2F, false),
    TAIGA(MapColor.PLANT, MapColor.PODZOL, 0.2F, false),
    FOREST(MapColor.PLANT, MapColor.GRASS, 0.2F, false),
    CHERRY(MapColor.COLOR_PINK, MapColor.GRASS, 0.3F, false),
    MEADOW(MapColor.GRASS, MapColor.COLOR_YELLOW, 0.05F, false),
    CAVE(MapColor.STONE, null, 0.0F, false),
    PLAINS(MapColor.GRASS, MapColor.PLANT, 0.04F, false);

    private static final ChartInk[] VALUES = values();

    public final MapColor base;
    @Nullable
    public final MapColor speckle;
    /** Share of pixels drawn with {@link #speckle} instead of {@link #base}. */
    public final float speckleShare;
    /** Shaded by depth like vanilla map water instead of by slope. */
    public final boolean water;

    ChartInk(MapColor base, @Nullable MapColor speckle, float speckleShare, boolean water) {
        this.base = base;
        this.speckle = speckle;
        this.speckleShare = speckleShare;
        this.water = water;
    }

    public static ChartInk byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PLAINS;
    }

    /** The colour of the pixel whose stable per-pixel hash is {@code hash}. */
    public MapColor colorAt(int hash) {
        return speckle != null && (hash & 0xFFFF) < speckleShare * 65536.0F ? speckle : base;
    }

    /** Resolves biomes to inks once each (biome holders are stable for a server's lifetime; cleared on tag reloads). */
    public static final class Resolver {
        private final Map<Holder<Biome>, ChartInk> cache = new HashMap<>();

        public ChartInk of(Holder<Biome> biome) {
            return cache.computeIfAbsent(biome, ChartInk::classify);
        }

        public void clear() {
            cache.clear();
        }
    }

    static ChartInk classify(Holder<Biome> b) {
        boolean snowy = b.is(Tags.Biomes.IS_SNOWY);
        if (b.is(Tags.Biomes.IS_AQUATIC_ICY)) {
            return FROZEN_WATER;
        }
        if (b.is(Tags.Biomes.IS_OCEAN) || b.is(Tags.Biomes.IS_RIVER)) {
            return WATER;
        }
        if (b.is(Tags.Biomes.IS_BEACH)) {
            return snowy ? SNOWY_BEACH : BEACH;
        }
        if (b.is(Tags.Biomes.IS_STONY_SHORES)) {
            return STONY_SHORE;
        }
        if (b.is(Tags.Biomes.IS_MUSHROOM)) {
            return MUSHROOM;
        }
        if (b.is(Tags.Biomes.IS_BADLANDS)) {
            return BADLANDS;
        }
        if (b.is(Tags.Biomes.IS_DESERT) || b.is(Tags.Biomes.IS_SANDY)) {
            return DESERT;
        }
        if (b.is(Biomes.CHERRY_GROVE)) {
            return CHERRY;
        }
        if (b.is(Tags.Biomes.IS_ICY)) {
            return ICE;
        }
        if (snowy) {
            return b.is(Tags.Biomes.IS_CONIFEROUS_TREE) || b.is(Tags.Biomes.IS_FOREST) ? SNOWY_FOREST : SNOW;
        }
        if (b.is(Tags.Biomes.IS_MOUNTAIN_PEAK)) {
            return PEAKS;
        }
        if (b.is(Tags.Biomes.IS_SWAMP)) {
            return SWAMP;
        }
        if (b.is(Tags.Biomes.IS_JUNGLE)) {
            return JUNGLE;
        }
        if (b.is(Tags.Biomes.IS_SAVANNA)) {
            return SAVANNA;
        }
        if (b.is(Tags.Biomes.IS_WINDSWEPT)) {
            return WINDSWEPT;
        }
        if (b.is(Tags.Biomes.IS_TAIGA) || b.is(Tags.Biomes.IS_CONIFEROUS_TREE)) {
            return TAIGA;
        }
        if (b.is(Tags.Biomes.IS_FOREST)) {
            return FOREST;
        }
        if (b.is(Biomes.MEADOW)) {
            return MEADOW;
        }
        if (b.is(Tags.Biomes.IS_CAVE) || b.is(Tags.Biomes.IS_UNDERGROUND)) {
            return CAVE;
        }
        return PLAINS;
    }
}
