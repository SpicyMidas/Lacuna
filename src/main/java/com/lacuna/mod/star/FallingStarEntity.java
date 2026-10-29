package com.lacuna.mod.star;

import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.LacunaAdvancements;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A small falling star called by the {@link StarcallerTrumpetItem}.
 * <ol>
 *   <li><b>Appearing</b> ({@link Starfall#APPEAR_TICKS}): it twinkles into being high in the sky, offset to one side of
 *       the target so it comes in at an angle, while the fanfare's last notes ring out at the caller.</li>
 *   <li><b>Falling</b> ({@link Starfall#FALL_TICKS}): it covers the path to its aim in an accelerating fall. Every tick it
 *       re-aims at the locked-on creature (if any), so it bends after it smoothly and ends exactly on it; without a
 *       creature it ends on the block that was looked at. Terrain in the way stops it early.</li>
 *   <li><b>Impact</b>: a small blast (damage with falloff, knockback, burning), a few blocks of crater and some fire
 *       only when mobGriefing is on, then a {@link MoltenStarRockBlock} rests where it fell, to cool through Prime Star
 *       Rock into the Extinguished Block of the Star ({@link StarRock}; or its fragments drop when there is no ground to
 *       hold it).</li>
 * </ol>
 * The server moves it; the client only eases toward the synced positions and draws it (client/star). Never saved.
 * Stars also fall on their own on clear nights ({@link NaturalStarfall}, {@link #fallFromSky}): those come with a far
 * twinkle instead of the fanfare, and no caller.
 */
public class FallingStarEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_FALL_START = SynchedEntityData.defineId(FallingStarEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(FallingStarEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_AIM = SynchedEntityData.defineId(FallingStarEntity.class,
            EntityDataSerializers.VECTOR3);

    /** A locked-on creature that jumps further than this in one tick (teleported) is let go. */
    private static final double LOSE_LOCK_JUMP = 24.0;

    // server state
    private Vec3 aim = Vec3.ZERO;
    @Nullable
    private UUID targetUuid;
    @Nullable
    private LivingEntity target;
    private Vec3 callerPos = Vec3.ZERO;
    private int age;
    private boolean struck;
    /** A star nobody called ({@link NaturalStarfall}): no fanfare, no caller, its own death message. */
    private boolean natural;
    /**
     * Called by a creature that may not grief (an animated trumpet): no crater or fires, and should the caller be gone
     * by the impact, players and pets are spared.
     */
    private boolean tame;

    // client easing toward the server position
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public FallingStarEntity(EntityType<? extends FallingStarEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FALL_START, 0);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_AIM, new Vector3f());
    }

    // ------------------------------------------------------------------ calling a star

    /**
     * Calls a star down on {@code aim}. Returns false (nothing happens) when the spot is outside the simulated area or
     * no star could reach it from the sky.
     */
    public static boolean call(ServerLevel server, LivingEntity caller, StarcallerTrumpetItem.Aim aim) {
        LivingEntity target = aim.target();
        Vec3 point = target != null ? aimPoint(target) : aim.point();
        if (!server.isPositionEntityTicking(BlockPos.containing(point))) {
            return false;
        }
        Vec3 start = startFor(server, caller, point);
        if (start == null) {
            return false;
        }
        FallingStarEntity star = new FallingStarEntity(ModEntities.FALLING_STAR.get(), server);
        star.setOwner(caller);
        star.setPos(start.x, start.y, start.z);
        star.aim = point;
        star.callerPos = caller.getEyePosition();
        star.tame = !(caller instanceof Player) && !net.neoforged.neoforge.event.EventHooks.canEntityGrief(server, caller);
        if (target != null) {
            star.targetUuid = target.getUUID();
            star.target = target;
            star.entityData.set(DATA_TARGET, target.getId());
        }
        star.entityData.set(DATA_AIM, point.toVector3f());
        star.entityData.set(DATA_FALL_START, (int) server.getGameTime() + Starfall.APPEAR_TICKS);
        server.addFreshEntity(star);
        fanfare(server, caller);
        return true;
    }

    /**
     * A star nobody called ({@link NaturalStarfall}): it twinkles into being at {@code start} and falls on
     * {@code point}, with no fanfare. Both spots must already be checked (simulated, open sky between them).
     */
    public static void fallFromSky(ServerLevel server, Vec3 start, Vec3 point) {
        FallingStarEntity star = new FallingStarEntity(ModEntities.FALLING_STAR.get(), server);
        star.natural = true;
        star.setPos(start.x, start.y, start.z);
        star.aim = point;
        star.callerPos = start;
        star.entityData.set(DATA_AIM, point.toVector3f());
        star.entityData.set(DATA_FALL_START, (int) server.getGameTime() + Starfall.APPEAR_TICKS);
        server.addFreshEntity(star);
    }

    /**
     * A starting point high above {@code point} and far out to one side, with open sky all the way down and a simulated
     * chunk under it (so the star ticks from the start), or null when none of a few directions works.
     */
    @Nullable
    public static Vec3 skyStart(ServerLevel server, Vec3 point, RandomSource r) {
        double first = r.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < 6; i++) {
            double a = first + i * (Math.PI / 3.0);
            double slant = Mth.lerp(r.nextDouble(), Starfall.NATURAL_SLANT_MIN, Starfall.NATURAL_SLANT_MAX);
            double height = Mth.lerp(r.nextDouble(), Starfall.NATURAL_HEIGHT_MIN, Starfall.NATURAL_HEIGHT_MAX);
            Vec3 start = point.add(Math.cos(a) * slant, height, Math.sin(a) * slant);
            if (!server.isPositionEntityTicking(BlockPos.containing(start.x, point.y, start.z))) {
                continue;
            }
            Vec3 from = point.add(start.subtract(point).normalize().scale(0.75));
            BlockHitResult hit = server.clip(new ClipContext(from, start, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                return start;
            }
        }
        return null;
    }

    /** Where the star aims on a creature: a little below its centre, so the blast is felt where it stands. */
    private static Vec3 aimPoint(LivingEntity target) {
        return target.position().add(0.0, target.getBbHeight() * 0.4, 0.0);
    }

    /**
     * The starting point high above {@code point}: beyond it and off to one side as seen from the caller, so the star
     * crosses the sky at an angle. Other directions (mirrored side, over the caller's head, straight above) are tried
     * when terrain blocks the way or the spot is not simulated; with a roof overhead the star starts just under it.
     */
    @Nullable
    private static Vec3 startFor(ServerLevel server, LivingEntity caller, Vec3 point) {
        RandomSource r = caller.getRandom();
        Vec3 flat = new Vec3(point.x - caller.getX(), 0.0, point.z - caller.getZ());
        if (flat.lengthSqr() < 1.0E-4) {
            flat = Vec3.directionFromRotation(0.0F, caller.getYRot());
        }
        flat = new Vec3(flat.x, 0.0, flat.z).normalize();
        double height = Mth.lerp(r.nextDouble(), Starfall.HEIGHT_MIN, Starfall.HEIGHT_MAX);
        double slant = Mth.lerp(r.nextDouble(), Starfall.SLANT_MIN, Starfall.SLANT_MAX);
        double side = (r.nextBoolean() ? 1.0 : -1.0) * Math.toRadians(35.0 + r.nextDouble() * 35.0);
        double[] angles = {side, -side, Math.PI, Double.NaN};
        Vec3 best = null;
        double bestFree = 0.0;
        for (double a : angles) {
            Vec3 offset = Double.isNaN(a) ? Vec3.ZERO : flat.yRot((float) a).scale(slant);
            Vec3 start = point.add(offset.x, height, offset.z);
            if (!server.isPositionEntityTicking(BlockPos.containing(start.x, point.y, start.z))) {
                continue;
            }
            Vec3 dir = start.subtract(point).normalize();
            Vec3 from = point.add(dir.scale(0.75));
            BlockHitResult hit = server.clip(new ClipContext(from, start, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                return start;
            }
            double free = hit.getLocation().distanceTo(point) - 1.5;
            if (free > bestFree) {
                bestFree = free;
                best = point.add(dir.scale(free));
            }
        }
        return bestFree >= 2.0 ? best : null;
    }

    /** The trumpet's blast: a bright horn call over a chord of plucked light. */
    private static void fanfare(ServerLevel server, LivingEntity caller) {
        Vec3 at = caller.getEyePosition();
        Sfx.at(server, at, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1), 5.0F, 1.5F);
        Sfx.at(server, at, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5), 2.5F, 1.26F);
        Sfx.at(server, at, SoundEvents.NOTE_BLOCK_PLING, 1.2F, 1.0F);
        Sfx.at(server, at, SoundEvents.NOTE_BLOCK_PLING, 1.0F, 1.5F);
        Sfx.at(server, at, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 1.6F);
        Vec3 bell = at.add(caller.getLookAngle().scale(0.9)).add(0.0, -0.15, 0.0);
        for (int i = 0; i < 4; i++) {
            server.sendParticles(ParticleTypes.NOTE, bell.x + (caller.getRandom().nextDouble() - 0.5) * 0.6,
                    bell.y + caller.getRandom().nextDouble() * 0.4, bell.z + (caller.getRandom().nextDouble() - 0.5) * 0.6,
                    0, 0.12 + i * 0.05, 0.0, 0.0, 1.0);
        }
        server.sendParticles(ModParticles.SOLAR_GLINT.get(), bell.x, bell.y, bell.z, 5, 0.35, 0.3, 0.35, 0.02);
    }

    // ------------------------------------------------------------------ state for the client

    public int fallStart() {
        return entityData.get(DATA_FALL_START);
    }

    /** 0..1 while the star twinkles into being; 1 once it falls. */
    public float appearProgress(float partialTick) {
        float t = (float) (level().getGameTime() - (fallStart() - Starfall.APPEAR_TICKS)) + partialTick;
        return Mth.clamp(t / Starfall.APPEAR_TICKS, 0.0F, 1.0F);
    }

    /** True once the fall has begun (false until the client has the synced start time). */
    public boolean isFalling() {
        return fallStart() != 0 && level().getGameTime() >= fallStart();
    }

    /** 0..1 through the fall. */
    public float fallProgress(float partialTick) {
        float t = (float) (level().getGameTime() - fallStart()) + partialTick;
        return Mth.clamp(t / Starfall.FALL_TICKS, 0.0F, 1.0F);
    }

    /** Network id of the locked-on creature, or -1. */
    public int targetId() {
        return entityData.get(DATA_TARGET);
    }

    /** The point the star is heading for (follows the locked-on creature on the server). */
    public Vec3 aim() {
        return new Vec3(entityData.get(DATA_AIM));
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            SafeRun.run(Starfall.FEATURE, () -> serverTick(server), this::discard);
        } else if (lerpSteps > 0) {
            double f = 1.0 / lerpSteps;
            setPos(getX() + (lerpX - getX()) * f, getY() + (lerpY - getY()) * f, getZ() + (lerpZ - getZ()) * f);
            lerpSteps--;
        }
    }

    private void serverTick(ServerLevel server) {
        age++;
        if (age <= Starfall.APPEAR_TICKS) {
            if (natural) {
                twinkle(server);
            } else {
                fanfareTail(server);
            }
            return;
        }
        int k = age - Starfall.APPEAR_TICKS;
        Vec3 goal = followTarget();
        double e0 = Starfall.ease((k - 1) / (double) Starfall.FALL_TICKS);
        double e1 = Starfall.ease(k / (double) Starfall.FALL_TICKS);
        double frac = k >= Starfall.FALL_TICKS ? 1.0 : (e1 - e0) / Math.max(1.0E-6, 1.0 - e0);
        Vec3 pos = position();
        Vec3 next = pos.add(goal.subtract(pos).scale(frac));
        Vec3 step = next.subtract(pos);
        // look a little past the step so a star ending on a block face still registers the block it strikes
        Vec3 probe = step.lengthSqr() > 1.0E-6 ? next.add(step.normalize().scale(0.6)) : next;
        BlockHitResult hit = server.clip(new ClipContext(pos, probe, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS && (k >= Starfall.FALL_TICKS
                || hit.getLocation().distanceToSqr(pos) <= step.lengthSqr())) {
            setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
            strike(server, hit.getLocation(), hit);
            return;
        }
        setDeltaMovement(step);
        setPos(next.x, next.y, next.z);
        if (k >= Starfall.FALL_TICKS || getY() < server.getMinBuildHeight() - 64) {
            strike(server, next, null);
        }
    }

    /** Re-aims at the locked-on creature; lets go of it when it dies, leaves the dimension or teleports away. */
    private Vec3 followTarget() {
        if (targetUuid == null || !(level() instanceof ServerLevel server)) {
            return aim;
        }
        if (target == null || target.isRemoved()) {
            target = server.getEntity(targetUuid) instanceof LivingEntity living ? living : null;
        }
        if (target != null && target.isAlive() && target.level() == level()) {
            Vec3 now = aimPoint(target);
            if (now.distanceToSqr(aim) < LOSE_LOCK_JUMP * LOSE_LOCK_JUMP) {
                if (now.distanceToSqr(aim) > 0.01) {
                    entityData.set(DATA_AIM, now.toVector3f());
                }
                aim = now;
                return aim;
            }
        }
        targetUuid = null;
        target = null;
        entityData.set(DATA_TARGET, -1);
        return aim;
    }

    /** The fanfare's answer: a rising arpeggio at the caller, then the twinkle of the star appearing. */
    private void fanfareTail(ServerLevel server) {
        Entity owner = getOwner();
        Vec3 at = owner != null && owner.isAlive() && owner.level() == level() ? owner.getEyePosition() : callerPos;
        switch (age) {
            case 4 -> Sfx.at(server, at, SoundEvents.NOTE_BLOCK_BELL, 1.4F, 1.0F);
            case 8 -> Sfx.at(server, at, SoundEvents.NOTE_BLOCK_BELL, 1.4F, 1.26F);
            case 12 -> Sfx.at(server, at, SoundEvents.NOTE_BLOCK_BELL, 1.6F, 1.5F);
            default -> {
            }
        }
        if (age == Starfall.APPEAR_TICKS) {
            Sfx.at(server, at, SoundEvents.NOTE_BLOCK_BELL, 1.8F, 2.0F);
            Sfx.at(server, at, SoundEvents.FIREWORK_ROCKET_TWINKLE, 2.0F, 1.3F);
            Sfx.at(server, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.5F);
        }
    }

    /** A star nobody called: a far twinkle as it appears high in the sky, heard well beyond the landing spot. */
    private void twinkle(ServerLevel server) {
        if (age == 1) {
            Sfx.at(server, position(), SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 8.0F, 1.2F);
        } else if (age == Starfall.APPEAR_TICKS) {
            Sfx.at(server, position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 8.0F, 1.4F);
            Sfx.at(server, position(), SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 8.0F, 0.9F);
        }
    }

    // ------------------------------------------------------------------ impact

    private void strike(ServerLevel server, Vec3 at, @Nullable BlockHitResult hit) {
        if (struck) {
            return;
        }
        struck = true;
        Entity owner = getOwner();
        boolean wet = !server.getFluidState(BlockPos.containing(at)).isEmpty();
        boolean griefing = !tame && server.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        blast(server, at, owner);
        BlockPos ground = groundAt(server, at, hit);
        boolean cratered = false;
        if (griefing && !wet && ground != null) {
            cratered = crater(server, ground, owner);
            fires(server, ground);
        }
        restStar(server, at, ground);
        if (natural) {
            com.lacuna.mod.essence.Quintessence.onNaturalStarLanded(server, at);
        }
        sounds(server, at, wet);
        LacunaFx.send(server, at, 160.0, FxPayload.at(Starfall.FX_IMPACT, at).withEntity(getId())
                .withParams(Starfall.BLAST_RADIUS, cratered ? 1.0F : 0.0F, wet ? 1.0F : 0.0F));
        server.gameEvent(this, GameEvent.EXPLODE, at);
        if (owner instanceof ServerPlayer player) {
            LacunaAdvancements.award(player, Starfall.ADVANCEMENT);
        }
        discard();
    }

    /** Damage with falloff, knockback and burning for every creature in the blast; the locked-on one takes it all. */
    private void blast(ServerLevel server, Vec3 at, @Nullable Entity owner) {
        double reach = Starfall.BLAST_RADIUS + 1.0;
        DamageSource source = natural ? skySource(server, at)
                : ModDamageTypes.source(server, Starfall.DAMAGE, this, owner != null ? owner : this);
        List<LivingEntity> hit = server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach + 1.0),
                e -> e.isAlive() && !e.isSpectator());
        for (LivingEntity e : hit) {
            double d = Math.sqrt(e.getBoundingBox().distanceToSqr(at));
            boolean locked = e == target;
            if (d > reach && !locked) {
                continue;
            }
            if (tame && owner == null && (e instanceof Player || e instanceof net.minecraft.world.entity.OwnableEntity)) {
                continue; // its caller is gone: nothing tells friend from foe any more
            }
            float damage = locked ? Starfall.BLAST_DAMAGE : Starfall.damageAt(d);
            // past the damage ring only the push is left: it spares whoever the blast itself could not hurt
            boolean affected = damage > 0.0F ? e.hurt(source, damage) : !e.isInvulnerableTo(source)
                    && !(e instanceof Player victim && source.getEntity() instanceof Player caller && !victim.canHarmPlayer(caller));
            if (!affected) {
                continue; // invulnerable, creative, or a player protected by PvP rules
            }
            if (damage > 0.0F && (locked || d < Starfall.BLAST_RADIUS * 0.75)) {
                e.igniteForSeconds(Starfall.BURN_SECONDS);
            }
            if (e instanceof Player player && player.getAbilities().flying) {
                continue;
            }
            double resist = Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
            double strength = Starfall.KNOCKBACK * (1.0 - Math.min(d, reach) / reach) * (1.0 - resist);
            if (strength <= 0.01) {
                continue;
            }
            Vec3 away = new Vec3(e.getX() - at.x, 0.0, e.getZ() - at.z);
            if (away.lengthSqr() < 1.0E-4) {
                double a = random.nextDouble() * Math.PI * 2.0;
                away = new Vec3(Math.cos(a), 0.0, Math.sin(a));
            }
            away = away.normalize();
            e.push(away.x * strength, 0.25 + 0.3 * strength, away.z * strength);
            e.hurtMarked = true;
        }
    }

    /** A star nobody called: a source with no entity, so the death message names no caller. */
    private static DamageSource skySource(ServerLevel server, Vec3 at) {
        var registry = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> type = registry.getHolder(Starfall.NATURAL_DAMAGE).map(h -> (Holder<DamageType>) h)
                .orElseGet(() -> registry.getHolderOrThrow(DamageTypes.MAGIC));
        return new DamageSource(type, at);
    }

    /** The solid block the star struck, or the first one up to 3 blocks under the impact (none in mid-air). */
    @Nullable
    private BlockPos groundAt(ServerLevel server, Vec3 at, @Nullable BlockHitResult hit) {
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos();
        }
        BlockHitResult down = server.clip(new ClipContext(at, at.add(0.0, -3.5, 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        return down.getType() == HitResult.Type.BLOCK ? down.getBlockPos() : null;
    }

    /**
     * A small bowl: the struck block and its neighbours (corners only sometimes) and a few blocks beside the centre one
     * level down. Tough blocks (obsidian and up), unbreakable ones, containers and fluids are left alone; grass at the
     * rim is scorched.
     */
    private boolean crater(ServerLevel server, BlockPos centre, @Nullable Entity owner) {
        List<BlockPos> bowl = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean corner = dx != 0 && dz != 0;
                if (!corner || random.nextFloat() < 0.5F) {
                    bowl.add(centre.offset(dx, 0, dz));
                }
                // a few deeper bites around the centre; the centre itself stays, so the star rests in plain view
                if (!corner && (dx != 0 || dz != 0) && random.nextFloat() < 0.35F) {
                    bowl.add(centre.offset(dx, -1, dz));
                }
            }
        }
        boolean any = false;
        Entity breaker = owner != null ? owner : this;
        for (BlockPos p : bowl) {
            BlockState s = server.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.is(StarRock.TAG)
                    || s.getDestroySpeed(server, p) < 0.0F || s.getBlock().getExplosionResistance() > Starfall.CRATER_MAX_RESISTANCE) {
                continue;
            }
            server.destroyBlock(p, random.nextFloat() < 0.35F, breaker);
            any = true;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = centre.offset(dx, dy, dz);
                    BlockState s = server.getBlockState(p);
                    if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) && server.getBlockState(p.above()).isAir()
                            && random.nextFloat() < 0.6F) {
                        server.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                    }
                }
            }
        }
        return any;
    }

    private void fires(ServerLevel server, BlockPos centre) {
        for (int i = 0; i < Starfall.FIRES; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 1.2 + random.nextDouble() * 1.4;
            BlockPos column = centre.offset(Mth.floor(Math.cos(a) * r + 0.5), 0, Mth.floor(Math.sin(a) * r + 0.5));
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos p = column.above(dy);
                if (BaseFireBlock.canBePlacedAt(server, p, Direction.UP)) {
                    server.setBlock(p, BaseFireBlock.getState(server, p), 11);
                    break;
                }
            }
        }
    }

    /**
     * Sets the molten Star Rock down on the first solid surface at or below the impact (looking a few blocks down, e.g.
     * into the crater); with no ground there (a creature struck in mid-air, open water) its fragments drop instead.
     */
    private void restStar(ServerLevel server, Vec3 at, @Nullable BlockPos ground) {
        BlockPos from = ground != null ? ground.above() : BlockPos.containing(at);
        BlockState star = ModBlocks.MOLTEN_STAR_ROCK.get().defaultBlockState();
        for (int dy = 1; dy >= -5; dy--) {
            BlockPos p = from.above(dy);
            BlockState s = server.getBlockState(p);
            if (!s.canBeReplaced()) {
                BlockPos up = p.above();
                BlockState above = server.getBlockState(up);
                if (above.canBeReplaced() && above.getFluidState().isEmpty() && star.canSurvive(server, up)) {
                    server.setBlock(up, star, 3);
                    return;
                }
                break;
            }
        }
        int count = 1 + random.nextInt(3);
        ItemEntity drop = new ItemEntity(server, at.x, at.y + 0.2, at.z, new ItemStack(ModItems.STAR_FRAGMENT.get(), count));
        drop.setDeltaMovement((random.nextDouble() - 0.5) * 0.1, 0.25, (random.nextDouble() - 0.5) * 0.1);
        drop.setDefaultPickUpDelay();
        server.addFreshEntity(drop);
    }

    private void sounds(ServerLevel server, Vec3 at, boolean wet) {
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 1.3F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, 4.0F, 0.6F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 2.0F, 1.5F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 2.5F, 0.5F);
        if (wet) {
            server.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ client easing, misc

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = 2;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 512.0 * 512.0;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return true;
    }
}
