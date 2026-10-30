package com.lacuna.mod.illusioner;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * The illusioner's place in raids: an extra {@link Raid.RaiderType} (META-INF/enumextensions.json, LACUNA_ILLUSIONER).
 * Spawns per wave, indexed like vanilla's tables (index 0 unused; a bonus wave uses the index of the last wave): none
 * on easy (3 waves); on normal (5 waves) one in the last wave and one in the bonus wave; on hard (7 waves) one in the
 * fifth, one in the last and one in the bonus wave, next to the evokers.
 * <p>
 * Loaded when the RaiderType enum is, so nothing here may touch a registry: the entity type is a lazy supplier.
 */
public final class IllusionerRaids {
    public static final int[] SPAWNS_PER_WAVE = {0, 0, 0, 0, 0, 1, 0, 1};

    public static final EnumProxy<Raid.RaiderType> ILLUSIONER = new EnumProxy<>(Raid.RaiderType.class,
            (Supplier<EntityType<? extends Raider>>) () -> EntityType.ILLUSIONER, SPAWNS_PER_WAVE);

    private IllusionerRaids() {}
}
