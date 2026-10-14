package com.lacuna.mod.eclipse;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Shadow Walk: while a player has this effect they are a shadow spectator (see {@link ShadowWalk}, which reacts to the
 * effect being added, expiring or being removed), however the effect was given.
 */
public class ShadowWalkEffect extends MobEffect {
    public ShadowWalkEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x1A1A22);
    }
}
