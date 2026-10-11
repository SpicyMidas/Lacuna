package com.lacuna.mod.eclipse;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker effect shown while Shadow Walk is active; the game-mode swap is managed by {@link ShadowWalk}. */
public class ShadowWalkEffect extends MobEffect {
    public ShadowWalkEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x1A1A22);
    }
}
