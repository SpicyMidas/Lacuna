package com.lacuna.mod.util;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server-side layered sound cues: several vanilla sounds played together read as one designed effect. */
public final class Sfx {
    private Sfx() {}

    public static void at(Level level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        if (!level.isClientSide()) {
            level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    public static void at(Level level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        at(level, pos, sound.value(), volume, pitch);
    }

    public static void at(Entity entity, SoundEvent sound, float volume, float pitch) {
        at(entity.level(), entity.position(), sound, volume, pitch);
    }

    public static void at(Entity entity, Holder<SoundEvent> sound, float volume, float pitch) {
        at(entity.level(), entity.position(), sound.value(), volume, pitch);
    }

    /** {@code pitch} randomised by +-{@code spread}. */
    public static void varied(Entity entity, SoundEvent sound, float volume, float pitch, float spread) {
        at(entity, sound, volume, pitch + (entity.getRandom().nextFloat() * 2.0F - 1.0F) * spread);
    }
}
