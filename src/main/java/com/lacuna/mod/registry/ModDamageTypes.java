package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Datapack damage types (data/lacuna/damage_type/*.json, spec section 21). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> SOLAR_BEAM = key("solar_beam");
    public static final ResourceKey<DamageType> ECLIPSE = key("eclipse");
    public static final ResourceKey<DamageType> ECHO_STRIKE = key("echo_strike");
    public static final ResourceKey<DamageType> SOLAR_RAY = key("solar_ray");
    public static final ResourceKey<DamageType> PYRRHIC_BOLT = key("pyrrhic_bolt");
    public static final ResourceKey<DamageType> GRAVEMAW_WAVE = key("gravemaw_wave");
    public static final ResourceKey<DamageType> REFLECTION_TOUCH = key("reflection_touch");
    /** Umbral Steel Sword "Umbral Rend" detonation (bypasses armor, no knockback). */
    public static final ResourceKey<DamageType> UMBRAL_REND = key("umbral_rend");
    /** Umbral Steel Axe "Eclipse Cleave" shockwave (no knockback: the shockwave pushes victims itself). */
    public static final ResourceKey<DamageType> ECLIPSE_CLEAVE = key("eclipse_cleave");

    private ModDamageTypes() {}

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Lacuna.id(path));
    }

    /**
     * Builds a damage source for a Lacuna damage type. If the datapack entry is missing the generic magic source is
     * used instead of crashing.
     */
    public static DamageSource source(Level level, ResourceKey<DamageType> key, @Nullable Entity direct, @Nullable Entity causing) {
        var registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> holder = registry.getHolder(key).map(h -> (Holder<DamageType>) h)
                .orElseGet(() -> registry.getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MAGIC));
        return new DamageSource(holder, direct, causing);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key, @Nullable Entity attacker) {
        return source(level, key, attacker, attacker);
    }
}
