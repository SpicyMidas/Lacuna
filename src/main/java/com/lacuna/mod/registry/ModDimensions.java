package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** The Forgotten Land is a datapack dimension (data/lacuna/dimension/forgotten_land.json). */
public final class ModDimensions {
    public static final ResourceKey<Level> FORGOTTEN_LAND = ResourceKey.create(Registries.DIMENSION, Lacuna.id("forgotten_land"));

    private ModDimensions() {}

    public static boolean isForgottenLand(Level level) {
        return level.dimension() == FORGOTTEN_LAND;
    }

    /** Null when the dimension is missing (e.g. removed from the datapack); callers must then do nothing. */
    @Nullable
    public static ServerLevel forgottenLand(MinecraftServer server) {
        return server.getLevel(FORGOTTEN_LAND);
    }
}
