package com.lacuna.mod.illusioner;

import com.lacuna.mod.util.SafeRun;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One duplicate an illusioner conjures with its duplication spell ({@link DuplicationSpellGoal}): drawn exactly like the
 * real one (client/illusioner: the illusioner model, texture, hood and eyes), named like it, holding the same bow, at
 * the same health. It hunts the real one's target with the illusioner's own bow AI (strafing, drawing, loosing), but its
 * arrows are illusions ({@link IllusoryArrow}); it raises its arms whenever the real one casts.
 * <p>
 * Any hit, of any strength, pops it in a puff of the illusioner's particles; it fades after
 * {@link Illusioners#DOUBLE_LIFETIME} ticks, or at once when the real one dies, leaves, is unloaded or wanders more than
 * {@link Illusioners#LEASH} blocks away. Server-authoritative; it never drops anything, gives no experience, never joins a
 * raid and is never saved (its entity type is {@code noSave}).
 */
public class IllusionerDouble extends SpellcasterIllager implements RangedAttackMob {
    @Nullable
    private UUID casterId;
    private int life;

    public IllusionerDouble(EntityType<? extends IllusionerDouble> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Illusioner.createAttributes();
    }

    /** The illusioner's own goals, minus its spells and target finding: the real one picks the target. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SpellcasterCastingSpellGoal());
        goalSelector.addGoal(6, new RangedBowAttackGoal<>(this, 0.5, 20, 15.0F));
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    /** Server: a copy of {@code caster} standing at {@code pos}. */
    void setup(Illusioner caster, Vec3 pos) {
        casterId = caster.getUUID();
        moveTo(pos.x, pos.y, pos.z, caster.getYRot(), caster.getXRot());
        setYHeadRot(caster.getYHeadRot());
        setYBodyRot(caster.yBodyRot);
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD}) {
            ItemStack held = caster.getItemBySlot(slot);
            setItemSlot(slot, slot == EquipmentSlot.MAINHAND && held.isEmpty() ? new ItemStack(Items.BOW) : held.copy());
            setDropChance(slot, 0.0F);
        }
        setLeftHanded(caster.isLeftHanded());
        setHealth(Math.min(getMaxHealth(), caster.getHealth()));
        if (caster.hasCustomName()) {
            setCustomName(caster.getCustomName());
            setCustomNameVisible(caster.isCustomNameVisible());
        }
        setCanPickUpLoot(false);
        setTarget(caster.getTarget());
        setAggressive(caster.isAggressive());
    }

    @Nullable
    public UUID casterId() {
        return casterId;
    }

    @Nullable
    private Illusioner caster(ServerLevel level) {
        return casterId != null && level.getEntity(casterId) instanceof Illusioner illusioner ? illusioner : null;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server) {
            Illusioner caster = caster(server);
            if (caster == null || !caster.isAlive() || ++life > Illusioners.DOUBLE_LIFETIME
                    || distanceToSqr(caster) > Illusioners.LEASH * Illusioners.LEASH || SafeRun.isDisabled(Illusioners.FEATURE)) {
                vanish(false);
                return;
            }
            SafeRun.run(Illusioners.FEATURE, () -> mimic(caster));
            if (isRemoved()) {
                return;
            }
        }
        super.tick();
    }

    /** The real one's target, and its spell animation (raised arms, the spell's swirls) whenever it casts. */
    private void mimic(Illusioner caster) {
        LivingEntity target = caster.getTarget();
        if (target != getTarget() && (target == null || target.isAlive())) {
            setTarget(target);
        }
        if (caster.isCastingSpell() && caster.spellCastingTickCount > spellCastingTickCount) {
            spellCastingTickCount = caster.spellCastingTickCount;
            setIsCastingSpell(caster.getCurrentSpell());
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        IllusoryArrow arrow = new IllusoryArrow(level(), this);
        double dx = target.getX() - getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2F, dz, 1.6F, 14 - level().getDifficulty().getId() * 4);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        if (isCastingSpell()) {
            return AbstractIllager.IllagerArmPose.SPELLCASTING;
        }
        return isAggressive() ? AbstractIllager.IllagerArmPose.BOW_AND_ARROW : AbstractIllager.IllagerArmPose.CROSSED;
    }

    // ------------------------------------------------------------------ one hit and gone

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            vanish(true); // /kill, the void
            return true;
        }
        if (isInvulnerableTo(source) || Illusioners.sameCircle(source.getEntity(), this)) {
            return false; // the real one and its other duplicates never break it
        }
        vanish(true);
        return true;
    }

    @Override
    public void die(DamageSource source) {
        vanish(true);
    }

    /** Removes the duplicate in a puff of the illusioner's particles ({@code popped}: broken by a hit, else faded). */
    void vanish(boolean popped) {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            Illusioners.puff(server, position(), popped ? 1.0F : 0.6F);
        }
        discard();
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        // an illusion leaves nothing behind
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        return super.isAlliedTo(entity) || Illusioners.sameCircle(entity, this);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canJoinRaid() {
        return false;
    }

    // ------------------------------------------------------------------ looking and sounding like the real one

    /** "Illusioner", in name tags, death messages and info overlays alike. */
    @Override
    protected Component getTypeName() {
        return EntityType.ILLUSIONER.getDescription();
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean unused) {
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.ILLUSIONER_AMBIENT;
    }

    @Override
    protected SoundEvent getCastingSoundEvent() {
        return SoundEvents.ILLUSIONER_CAST_SPELL;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ILLUSIONER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ILLUSIONER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ILLUSIONER_DEATH;
    }
}
