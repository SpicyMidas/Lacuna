package com.lacuna.mod.client.star;

import com.lacuna.mod.star.FallingStarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A looping sound that rides on a falling star: it swells as the star nears the listener and its pitch drops through
 * the fall, the classic whistle of something coming down. Heard up to {@link #RANGE} blocks away; stops with the star.
 */
final class StarWhistleSound extends AbstractTickableSoundInstance {
    private static final double RANGE = 128.0;

    private final FallingStarEntity star;
    private final float pitchFrom;
    private final float pitchTo;
    private final float loudness;

    StarWhistleSound(FallingStarEntity star, SoundEvent sound, float pitchFrom, float pitchTo, float loudness) {
        super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.star = star;
        this.pitchFrom = pitchFrom;
        this.pitchTo = pitchTo;
        this.loudness = loudness;
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.01F;
        this.pitch = pitchFrom;
        this.x = star.getX();
        this.y = star.getY();
        this.z = star.getZ();
    }

    @Override
    public void tick() {
        if (star.isRemoved()) {
            stop();
            return;
        }
        x = star.getX();
        y = star.getY();
        z = star.getZ();
        float p = star.fallProgress(0.0F);
        Vec3 ear = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double near = Mth.clamp(1.0 - ear.distanceTo(star.position()) / RANGE, 0.0, 1.0);
        volume = (float) (near * near) * loudness * (0.35F + 0.65F * p);
        pitch = Mth.lerp(p, pitchFrom, pitchTo);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
