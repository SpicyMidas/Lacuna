package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.eclipse.ShadowWalkEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Lacuna.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> SHADOW_WALK = EFFECTS.register("shadow_walk", ShadowWalkEffect::new);

    private ModEffects() {}
}
