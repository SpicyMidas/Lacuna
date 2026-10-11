package com.lacuna.mod.fl;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/** Keys of the five Forgotten Land biomes (datapack JSON in data/lacuna/worldgen/biome). */
public final class ForgottenBiomes {
    public static final ResourceKey<Biome> FORGOTTEN_PLAINS = key("forgotten_plains");
    public static final ResourceKey<Biome> CINDERFROST_FLATS = key("cinderfrost_flats");
    public static final ResourceKey<Biome> INVERTED_FALLS = key("inverted_falls");
    public static final ResourceKey<Biome> WHISPERING_MARSH = key("whispering_marsh");
    public static final ResourceKey<Biome> CLOCKWORK_DUNES = key("clockwork_dunes");

    private ForgottenBiomes() {}

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, Lacuna.id(path));
    }

    /** True if {@code pos} lies in the given Forgotten Land biome (always false in other dimensions). */
    public static boolean in(Level level, BlockPos pos, ResourceKey<Biome> biome) {
        return ModDimensions.isForgottenLand(level) && level.getBiome(pos).is(biome);
    }

    public static boolean is(LevelReader level, BlockPos pos, ResourceKey<Biome> biome) {
        return level.getBiome(pos).is(biome);
    }
}
