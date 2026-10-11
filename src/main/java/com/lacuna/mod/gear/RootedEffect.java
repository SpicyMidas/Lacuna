package com.lacuna.mod.gear;

import com.lacuna.mod.Lacuna;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Rooted (spec M7): movement speed -100 %, jump strength -100 %. */
public class RootedEffect extends MobEffect {
    public RootedEffect() {
        super(MobEffectCategory.HARMFUL, 0x4FA84A);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, Lacuna.id("effect.rooted_speed"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, Lacuna.id("effect.rooted_jump"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
