package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.beam.BeamHandler;
import com.lacuna.mod.beam.BeamTrace;
import com.lacuna.mod.beam.DivineMagnifyingGlassItem;
import com.lacuna.mod.eclipse.EclipseCharge;
import com.lacuna.mod.eclipse.EclipserItem;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.logic.ChargeMath;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.solar.SunsFuryEntity;
import com.lacuna.mod.solar.SunsFuryItem;
import com.lacuna.mod.star.FallingStarEntity;
import com.lacuna.mod.star.StarcallerTrumpetItem;
import com.lacuna.mod.star.Starfall;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Special powers of animated items (Wand of Quickening). An item with a right-click power of its own sometimes uses
 * it on its target: once a second while it fights (an "opportunity": the target in the power's reach and in sight,
 * its cooldown over and its own rules met) it rolls the power's chance. On a success the item rears up and glows for
 * {@link #CUE_TICKS} ticks (the cue, see AnimatedItemRenderer), then the power goes off through the same code the
 * player's use runs, with the item as the caster: its friends (owner, pets, villagers, other animated items of the
 * owner) are spared, its kills count as the owner's, and it pays the same wear the player would (nothing while the
 * owner is in creative), so a worn-out item can break and fall asleep for good.
 * <ul>
 *   <li>Gravemaw Greatsword: the crescent wave (a short charge) - Eclipser: the dash through its target, only with
 *       enough charge (built by its own hits), spending all of it - Tidecaller Sickle: the Tidal Wave, or the
 *       Maelstrom on its target at full tide (its hits in water or rain raise the tide) - Starcaller Trumpet: a star
 *       on its target (long cooldown, no crater) - Sun's Fury: a fleeting echo of the spear (the impact only: the
 *       spear is never spent and no solar ray falls) - Pyrrhic Scepter: a homing bolt paid with its own health -
 *       Ember Triangle: a smaller thermal wave, rarely, and only among several foes - Divine Magnifying Glass: a
 *       two-second sunbeam (daylight only, never on blocks).</li>
 *   <li>Bows and crossbows (Heartwood Bow included, with its rooting arrows) already shoot as their attack; the Echo
 *       Dagger echoes every hit. Totems and lanterns never act on their own.</li>
 * </ul>
 */
public final class AnimatedAbilities {
    public static final String FEATURE = "animated_abilities";
    /** The cue: it rears up and glows this long before the power goes off. */
    public static final int CUE_TICKS = 12;
    /** One roll per opportunity, an opportunity at most once a second. */
    public static final int OPPORTUNITY_INTERVAL = 20;
    /** No power in its first moments awake. */
    public static final int FIRST_DELAY_MIN = 40;
    public static final int FIRST_DELAY_MAX = 100;
    /** A power that found nothing to do is tried again after this long. */
    public static final int RETRY = 40;
    public static final double EMBER_RADIUS = 5.0;
    public static final int BEAM_TICKS = 40;
    /** Client effect: the animated glass's sunbeam (client/gear/QuickeningFx). */
    public static final String BEAM_FX = "animated_beam";
    private static final double DASH_STEP = 0.25;

    @FunctionalInterface
    interface Ready {
        boolean test(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target);
    }

    @FunctionalInterface
    interface Cue {
        void run(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target);
    }

    /** Uses the power; returns the cooldown that follows, or 0 when nothing happened. */
    @FunctionalInterface
    interface Act {
        int run(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target);
    }

    /** A power lasting several ticks (the dash, the beam); returns false when it is over. */
    interface Channel {
        boolean tick(ServerLevel level, AnimatedItemEntity user);
    }

    record Ability(String id, double minRange, double maxRange, float chance, Ready ready, Cue cue, Act act) {}

    private static final Cue NO_CUE = (level, user, stack, target) -> {};
    private static final Ready ALWAYS = (level, user, stack, target) -> true;

    private static final Ability GRAVEMAW = new Ability("gravemaw", 1.5, Balance.GRAVEMAW_RANGE - 1.0, 0.3F, ALWAYS,
            AnimatedAbilities::gravemawCue, AnimatedAbilities::gravemaw);
    private static final Ability ECLIPSE = new Ability("eclipse", 2.5, 9.0, 0.35F,
            (level, user, stack, target) -> LacunaConfig.get(LacunaConfig.ECLIPSER_ENABLED) && ChargeMath.canDash(EclipserItem.charge(stack)),
            AnimatedAbilities::eclipseCue, AnimatedAbilities::eclipse);
    private static final Ability TIDE = new Ability("tide", 2.0, 12.0, 0.3F, ALWAYS, AnimatedAbilities::tideCue, AnimatedAbilities::tide);
    private static final Ability STAR = new Ability("star", 4.0, 48.0, 0.5F, ALWAYS, NO_CUE, AnimatedAbilities::star);
    private static final Ability SUN = new Ability("sun", 3.0, 18.0, 0.3F,
            (level, user, stack, target) -> LacunaConfig.get(LacunaConfig.SUNS_FURY_ENABLED) && !ModDimensions.isForgottenLand(level),
            AnimatedAbilities::sunCue, AnimatedAbilities::sun);
    private static final Ability PYRRHIC = new Ability("pyrrhic", 2.0, 16.0, 0.45F,
            (level, user, stack, target) -> user.getHealth() > Balance.SCEPTER_MIN_HEALTH, NO_CUE, AnimatedAbilities::pyrrhic);
    private static final Ability EMBER = new Ability("ember", 0.0, EMBER_RADIUS - 0.5, 0.12F, AnimatedAbilities::emberReady,
            NO_CUE, AnimatedAbilities::ember);
    private static final Ability BEAM = new Ability("beam", 2.0, 14.0, 0.35F, AnimatedAbilities::beamReady, AnimatedAbilities::beamCue,
            AnimatedAbilities::beam);

    private AnimatedAbilities() {}

    /** The power of this stack, or null for items without one (and when special weapons are off). */
    @Nullable
    static Ability of(ItemStack stack) {
        if (stack.isEmpty() || !LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof GravemawGreatswordItem) {
            return GRAVEMAW;
        }
        if (item instanceof EclipserItem) {
            return ECLIPSE;
        }
        if (item instanceof TidecallerSickleItem) {
            return TIDE;
        }
        if (item instanceof StarcallerTrumpetItem) {
            return STAR;
        }
        if (item instanceof SunsFuryItem) {
            return SUN;
        }
        if (item instanceof PyrrhicScepterItem) {
            return PYRRHIC;
        }
        if (item instanceof EmberTriangleItem) {
            return EMBER;
        }
        if (item instanceof DivineMagnifyingGlassItem && LacunaConfig.get(LacunaConfig.BEAM_ENABLED)) {
            return BEAM;
        }
        return null;
    }

    // ------------------------------------------------------------------ the roll and the power

    /** An opportunity: true when the roll succeeded and the cue should start. */
    static boolean roll(ServerLevel level, AnimatedItemEntity user, LivingEntity target) {
        ItemStack stack = user.getMainHandItem();
        Ability a = of(stack);
        if (a == null || !target.isAlive() || !user.canTarget(target)) {
            return false;
        }
        double d = user.distanceTo(target);
        if (d < a.minRange() || d > a.maxRange() || !user.getSensing().hasLineOfSight(target)) {
            return false;
        }
        if (!a.ready().test(level, user, stack, target) || user.getRandom().nextFloat() >= a.chance()) {
            return false;
        }
        Vec3 c = user.position().add(0.0, 0.5, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.03);
        Sfx.at(user, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.8F);
        a.cue().run(level, user, stack, target);
        return true;
    }

    /** The cue is over: the power goes off. Returns the cooldown that follows. */
    static int perform(ServerLevel level, AnimatedItemEntity user, @Nullable LivingEntity target) {
        ItemStack stack = user.getMainHandItem();
        Ability a = of(stack);
        if (a == null || target == null || !target.isAlive() || !user.canTarget(target) || target.level() != level
                || !a.ready().test(level, user, stack, target)) {
            return RETRY;
        }
        face(user, target);
        int cooldown = SafeRun.call(FEATURE + "_" + a.id(), () -> a.act().run(level, user, stack, target), 0);
        return cooldown > 0 ? cooldown : RETRY;
    }

    /** After each of its melee hits (with the item's own hurtEnemy already done). */
    static void afterStrike(AnimatedItemEntity user, ItemStack stack, LivingEntity target, float damage) {
        if (!LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return;
        }
        Item item = stack.getItem();
        if (item instanceof EchoDaggerItem) {
            // the dagger's own echo, as for a player's hit
            EchoDaggerItem.scheduleEcho(user, target, damage);
        } else if (item instanceof TidecallerSickleItem && user.isInWaterOrRain()) {
            // the tide is with it: a charge for the sickle, as for a player's hit
            int tide = TidecallerSickleItem.tide(stack);
            if (tide < Balance.SICKLE_MAX_TIDE) {
                stack.set(ModComponents.TIDE_CHARGES.get(), tide + 1);
                Sfx.at(user, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.8F, 0.9F + 0.2F * (tide + 1));
            }
        }
    }

    /** The same wear the player pays (none while its owner is in creative); a worn-out item breaks. */
    private static void wear(AnimatedItemEntity user, ItemStack stack, int amount) {
        if (amount <= 0 || user.getOwner() instanceof Player owner && owner.hasInfiniteMaterials()) {
            return;
        }
        stack.hurtAndBreak(amount, user, EquipmentSlot.MAINHAND);
    }

    private static void face(AnimatedItemEntity user, LivingEntity target) {
        user.lookAt(target, 360.0F, 360.0F);
        user.setYHeadRot(user.getYRot());
        user.setYBodyRot(user.getYRot());
    }

    /** Horizontal unit vector from the user to the target (its facing when they overlap). */
    private static Vec3 flat(AnimatedItemEntity user, LivingEntity target) {
        Vec3 d = new Vec3(target.getX() - user.getX(), 0.0, target.getZ() - user.getZ());
        return d.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, user.getYRot()) : d.normalize();
    }

    // ------------------------------------------------------------------ Gravemaw Greatsword

    private static void gravemawCue(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        LacunaFx.send(level, user.position(), FxPayload.at("gravemaw_ready", user.position()).withEntity(user.getId()));
        Sfx.at(user, SoundEvents.BEACON_AMBIENT, 0.8F, 1.2F);
    }

    private static int gravemaw(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        int charge = Balance.GRAVEMAW_MIN_CHARGE + user.getRandom().nextInt((Balance.GRAVEMAW_MAX_CHARGE - Balance.GRAVEMAW_MIN_CHARGE) / 2 + 1);
        GravemawGreatswordItem.wave(level, user, charge, flat(user, target));
        wear(user, stack, 3);
        return Math.max(200, Balance.GRAVEMAW_COOLDOWN);
    }

    // ------------------------------------------------------------------ Eclipser

    private static void eclipseCue(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        level.sendParticles(ModParticles.SHADOW_MOTE.get(), user.getX(), user.getY() + 0.5, user.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        Sfx.at(user, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.7F, 0.7F);
    }

    /**
     * The dash through its target, with the Eclipser's own rules (charge, distance, damage, wear: logic/ChargeMath),
     * moved by the item itself ({@link Dash}) since the player's dash is driven by the dashing client.
     */
    private static int eclipse(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        int charge = EclipserItem.charge(stack);
        if (!ChargeMath.canDash(charge)) {
            return 0;
        }
        Vec3 dir = flat(user, target);
        double reach = Math.sqrt(user.distanceToSqr(target.getX(), user.getY(), target.getZ())) + 2.5;
        double distance = Math.min(reach, ChargeMath.dashDistance(charge, LacunaConfig.get(LacunaConfig.ECLIPSE_DASH_MAX)));
        Vec3 start = user.position();
        Vec3 end = start;
        AABB box = user.getBoundingBox();
        for (double t = DASH_STEP; t <= distance + 1.0E-6; t += DASH_STEP) {
            Vec3 p = start.add(dir.scale(t));
            BlockPos bp = BlockPos.containing(p);
            if (!level.hasChunkAt(bp) || !level.getWorldBorder().isWithinBounds(bp) || !level.noCollision(user, box.move(p.subtract(start)))) {
                break; // stops before solid blocks, like the player's dash
            }
            end = p;
        }
        if (start.distanceTo(end) < 1.0) {
            return 0;
        }
        EclipserItem.setState(stack, new EclipseCharge(0, EclipserItem.state(stack).lastHit()));
        wear(user, stack, ChargeMath.durabilityCost(charge));
        user.startChannel(new Dash(start, end, dir, charge));
        float pitch = 0.9F + level.random.nextFloat() * 0.15F;
        level.playSound(null, start.x, start.y + 0.5, start.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.NEUTRAL, 0.8F, 0.75F * pitch);
        level.playSound(null, start.x, start.y + 0.5, start.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.9F, 0.55F * pitch);
        level.playSound(null, start.x, start.y + 0.5, start.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.7F, 0.6F * pitch);
        return Math.max(160, LacunaConfig.get(LacunaConfig.ECLIPSE_COOLDOWN));
    }

    /** The animated Eclipser's dash: it slides along the path, cutting every foe it passes once. */
    private static final class Dash implements Channel {
        final Vec3 start;
        final Vec3 end;
        final Vec3 dir;
        final int ticks;
        final int charge;
        final float damage;
        final IntSet hit = new IntOpenHashSet();
        double progress;
        int age;

        Dash(Vec3 start, Vec3 end, Vec3 dir, int charge) {
            this.start = start;
            this.end = end;
            this.dir = dir;
            this.ticks = ChargeMath.dashTicks(start.distanceTo(end));
            this.charge = charge;
            this.damage = ChargeMath.dashDamage(charge);
        }

        @Override
        public boolean tick(ServerLevel level, AnimatedItemEntity user) {
            age++;
            double f = Math.min(1.0, age / (double) ticks);
            Vec3 from = start.lerp(end, progress);
            Vec3 to = start.lerp(end, f);
            if (!level.noCollision(user, user.getBoundingBox().move(to.subtract(user.position())))) {
                f = progress; // something moved into the way: the dash ends here
                to = from;
            }
            user.setPos(to.x, to.y, to.z);
            user.setDeltaMovement(Vec3.ZERO);
            user.resetFallDistance();
            user.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            user.setYHeadRot(user.getYRot());
            user.setYBodyRot(user.getYRot());
            cut(level, user, from, to);
            for (int i = 0; i < 4; i++) {
                Vec3 p = from.lerp(to, i / 4.0).add(0.0, 0.45, 0.0);
                level.sendParticles(ModParticles.SHADOW_MOTE.get(), p.x, p.y, p.z, 2, 0.12, 0.18, 0.12, 0.01);
                level.sendParticles(ModParticles.CRIMSON_SPARK.get(), p.x, p.y, p.z, 1, 0.1, 0.12, 0.1, 0.05);
            }
            progress = f;
            if (f >= 1.0 || to == from && age > 1) {
                Vec3 pos = user.position();
                LacunaFx.send(level, pos, new FxPayload("eclipse_end", user.getId(), pos, dir, charge, 0, 0, 0xFFFFFFFF));
                level.playSound(null, pos.x, pos.y + 0.5, pos.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.NEUTRAL, 0.7F, 0.65F);
                level.playSound(null, pos.x, pos.y + 0.5, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8F, 0.6F);
                return false;
            }
            return true;
        }

        private void cut(ServerLevel level, AnimatedItemEntity user, Vec3 from, Vec3 to) {
            double half = user.getBbHeight() / 2.0;
            Vec3 a = from.add(0, half, 0);
            Vec3 b = to.add(0, half, 0);
            AABB area = new AABB(a, b).inflate(Balance.ECLIPSE_DASH_RADIUS + 1.5);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != user && e.isAlive()
                    && !e.isSpectator() && !(e instanceof ArmorStand) && !user.isAlly(e) && !hit.contains(e.getId()))) {
                Vec3 c = target.getBoundingBox().getCenter();
                double d = ChargeMath.segmentDistance(c.x, c.y, c.z, a.x, a.y, a.z, b.x, b.y, b.z);
                if (d > Balance.ECLIPSE_DASH_RADIUS + target.getBbWidth() / 2.0) {
                    continue;
                }
                hit.add(target.getId());
                target.hurt(ModDamageTypes.source(level, ModDamageTypes.ECLIPSE, user), damage);
                LacunaFx.send(level, c, new FxPayload("eclipse_hit", target.getId(), c, dir, target.getBbHeight(), target.getBbWidth(),
                        hit.size(), 0xFFFFFFFF));
                float pitch = 0.85F + level.random.nextFloat() * 0.3F;
                level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.9F, 0.7F * pitch);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 0.8F, 0.6F * pitch);
            }
        }
    }

    // ------------------------------------------------------------------ Tidecaller Sickle

    private static void tideCue(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        level.sendParticles(ParticleTypes.SPLASH, user.getX(), user.getY() + 0.6, user.getZ(), 14, 0.3, 0.3, 0.3, 0.1);
        Sfx.at(user, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.8F, 1.3F);
    }

    private static int tide(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        if (TidecallerSickleItem.tide(stack) >= Balance.SICKLE_MAX_TIDE) {
            // full tide: the whirlpool opens under its target and spends the charges
            TideCurrents.startMaelstrom(level, user, user.getOwnerUUID(), target.position().add(0.0, 0.5, 0.0));
            stack.set(ModComponents.TIDE_CHARGES.get(), 0);
            wear(user, stack, Balance.SICKLE_MAELSTROM_DURABILITY);
            return Math.max(400, Balance.SICKLE_MAELSTROM_COOLDOWN);
        }
        TideCurrents.startWave(level, user, user.getOwnerUUID(), flat(user, target));
        wear(user, stack, Balance.SICKLE_WAVE_DURABILITY);
        return Math.max(140, Balance.SICKLE_WAVE_COOLDOWN);
    }

    // ------------------------------------------------------------------ Starcaller Trumpet

    private static int star(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        boolean called = SafeRun.call(Starfall.FEATURE, () -> FallingStarEntity.call(level, user, new StarcallerTrumpetItem.Aim(target.position(), target)),
                false);
        if (!called) {
            return 0;
        }
        wear(user, stack, 1);
        level.gameEvent(GameEvent.INSTRUMENT_PLAY, user.position(), GameEvent.Context.of(user));
        return Math.max(600, Starfall.COOLDOWN);
    }

    // ------------------------------------------------------------------ Sun's Fury

    private static void sunCue(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        level.sendParticles(ModParticles.SOLAR_GLINT.get(), user.getX(), user.getY() + 0.7, user.getZ(), 6, 0.25, 0.3, 0.25, 0.02);
        Sfx.at(user, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.8F);
    }

    private static int sun(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        SunsFuryEntity spear = new SunsFuryEntity(level, user);
        spear.setFleeting(true);
        double dx = target.getX() - user.getX();
        double dy = target.getY(0.5) - spear.getY();
        double dz = target.getZ() - user.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        spear.shoot(dx, dy + horizontal * 0.12, dz, 2.0F, 1.0F);
        level.addFreshEntity(spear);
        level.playSound(null, spear, SoundEvents.TRIDENT_THROW.value(), SoundSource.NEUTRAL, 1.0F, 0.9F);
        Sfx.at(user, SoundEvents.FIRECHARGE_USE, 0.5F, 1.4F);
        return 200;
    }

    // ------------------------------------------------------------------ Pyrrhic Scepter

    private static int pyrrhic(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        if (user.getHealth() <= Balance.SCEPTER_MIN_HEALTH) {
            return 0;
        }
        // the price, as for the player: its own life
        user.setHealth(user.getHealth() - Balance.SCEPTER_HEALTH_COST);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, user.getX(), user.getY() + 0.6, user.getZ(), 2, 0.15, 0.1, 0.15, 0.0);
        PyrrhicBoltEntity bolt = new PyrrhicBoltEntity(level, user, PyrrhicScepterItem.boltDamage(user));
        Vec3 aim = target.getEyePosition().subtract(bolt.position());
        bolt.shoot(aim.x, aim.y, aim.z, Balance.BOLT_SPEED, 0.0F);
        level.addFreshEntity(bolt);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 0.8F, 1.3F);
        wear(user, stack, 1);
        return Math.max(60, Balance.SCEPTER_COOLDOWN);
    }

    // ------------------------------------------------------------------ Ember Triangle

    /** Devastating, so only among several foes close by (and its reach is half the player's). */
    private static boolean emberReady(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        double r = EMBER_RADIUS;
        return level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(r, 2.0, r),
                e -> e instanceof Enemy && user.canTarget(e) && e.distanceToSqr(user) < r * r).size() >= 2;
    }

    private static int ember(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        SafeRun.run(EmberWave.FEATURE, () -> EmberWave.start(level, user, user.getOwnerUUID(), EMBER_RADIUS));
        wear(user, stack, 1);
        return Math.max(1200, Balance.EMBER_TRIANGLE_COOLDOWN);
    }

    // ------------------------------------------------------------------ Divine Magnifying Glass

    private static Vec3 beamDir(AnimatedItemEntity user, LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(user.getEyePosition());
        return d.lengthSqr() < 1.0E-6 ? user.getViewVector(1.0F) : d.normalize();
    }

    /** Only while the sun can feed it (the beam's own day/underwater rules). */
    private static boolean beamReady(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        return BeamTrace.of(level, user, user.getEyePosition(), beamDir(user, target), 1.0F).beam().damageMultiplier() > 0.0;
    }

    private static void beamCue(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        Sfx.at(user, SoundEvents.BEACON_ACTIVATE, 0.45F, 1.9F);
    }

    private static int beam(ServerLevel level, AnimatedItemEntity user, ItemStack stack, LivingEntity target) {
        user.startChannel(new Beam(target));
        LacunaFx.send(level, user.position(), FxPayload.at(BEAM_FX, user.getEyePosition()).withEntity(user.getId())
                .withParams(target.getId(), BEAM_TICKS, 0.0F));
        return 160;
    }

    /** The glass focuses sunlight on its target for two seconds through the real beam code; it never scorches blocks. */
    private static final class Beam implements Channel {
        final LivingEntity target;
        int age;
        int lost;

        Beam(LivingEntity target) {
            this.target = target;
        }

        @Override
        public boolean tick(ServerLevel level, AnimatedItemEntity user) {
            ItemStack stack = user.getMainHandItem();
            if (++age > BEAM_TICKS || !(stack.getItem() instanceof DivineMagnifyingGlassItem) || !target.isAlive() || target.level() != level) {
                return false;
            }
            face(user, target);
            Vec3 dir = beamDir(user, target);
            user.setXRot((float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0))));
            BeamTrace trace = BeamTrace.of(level, user, user.getEyePosition(), user.getViewVector(1.0F), 1.0F);
            EntityHitResult hit = trace.entityHit();
            if (hit == null) {
                return ++lost < 6; // the beam slipped off: it never burns the world behind
            }
            lost = 0;
            BeamHandler.tick(level, user, stack, age);
            if (age % 30 == 0) {
                Sfx.at(user, SoundEvents.BEACON_AMBIENT, 0.35F, 1.8F);
            }
            return true;
        }
    }
}
