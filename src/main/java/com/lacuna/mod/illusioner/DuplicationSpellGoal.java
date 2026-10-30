package com.lacuna.mod.illusioner;

import com.lacuna.mod.util.SafeRun;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.SpellcasterIllager;

/**
 * The illusioner's duplication spell, in place of the vanilla mirror spell (invisibility with client-only mirror
 * images): cast when it engages a target it can see, then again every {@link Illusioners#CAST_INTERVAL_MIN}-
 * {@link Illusioners#CAST_INTERVAL_MAX} ticks. It casts like vanilla spells do, arms raised for
 * {@link Illusioners#CAST_TIME} ticks with the vanilla mirror spell's sounds and swirls; after
 * {@link Illusioners#CAST_WARMUP} ticks the duplicates appear ({@link Illusioners#duplicate}). No control flags, like
 * vanilla's spell goals: the vanilla casting goal holds it still and keeps it facing the target while it casts.
 */
final class DuplicationSpellGoal extends Goal {
    private final Illusioner mob;
    private int warmup;
    private int nextCastTick;

    DuplicationSpellGoal(Illusioner mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && !mob.isCastingSpell() && mob.tickCount >= nextCastTick
                && mob.getSensing().hasLineOfSight(target) && !SafeRun.isDisabled(Illusioners.FEATURE);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && warmup > 0;
    }

    @Override
    public void start() {
        warmup = adjustedTickDelay(Illusioners.CAST_WARMUP);
        mob.spellCastingTickCount = Illusioners.CAST_TIME;
        nextCastTick = mob.tickCount + Illusioners.CAST_INTERVAL_MIN
                + mob.getRandom().nextInt(Illusioners.CAST_INTERVAL_MAX - Illusioners.CAST_INTERVAL_MIN + 1);
        mob.playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 1.0F);
        mob.setIsCastingSpell(SpellcasterIllager.IllagerSpell.DISAPPEAR);
    }

    @Override
    public void tick() {
        if (--warmup == 0) {
            SafeRun.run(Illusioners.FEATURE, () -> Illusioners.duplicate(mob));
            mob.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.0F);
        }
    }
}
