package com.lacuna.mod.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The thrown Sun's Fury, a spear of sunlight (spec 11.3, revision 2).
 * <ol>
 *   <li><b>Flying</b>: thrown like a trident (speed up to 2.5, gravity 0.03, rotation follows the velocity). Hitting a
 *       creature deals the impact damage and drops the spear; hitting a block embeds it; after 10 s in the air it
 *       stops where it is.</li>
 *   <li><b>Focus</b> ({@link LacunaConfig#SOLAR_FOCUS_TICKS}, default 100 ticks): the spear stays embedded at its landing
 *       angle and glows brighter and brighter. In the Overworld (config {@code changeTime}) the day time is eased to the
 *       next noon (never backwards, day count kept) and sent to every player of the dimension each tick, and the
 *       weather clears. Nothing is damaged yet: it is the warning.</li>
 *   <li>The spear then becomes a {@link SolarRayEntity} (the strike and its fade) and disappears.</li>
 * </ol>
 * Everything visual is drawn by the client from the synced state (client/solar). Never saved (the entity type is
 * noSave), so no half-finished focus survives a restart.
 */
public class SunsFuryEntity extends Projectile {
    public static final byte FLYING = 0;
    public static final byte FOCUS = 1;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(SunsFuryEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_FOCUS_START =
            SynchedEntityData.defineId(SunsFuryEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FOCUS_TICKS =
            SynchedEntityData.defineId(SunsFuryEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_DIR = SynchedEntityData.defineId(SunsFuryEntity.class, EntityDataSerializers.VECTOR3);
    /** The day-time sweep of the focus (0, 0 when the time is not swept), so clients can move the sun every frame. */
    private static final EntityDataAccessor<Long> DATA_SWEEP_FROM = SynchedEntityData.defineId(SunsFuryEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_SWEEP_TO = SynchedEntityData.defineId(SunsFuryEntity.class, EntityDataSerializers.LONG);

    /** Client only: recent positions of the flying spear (oldest first), for the light trail. */
    public final Deque<Vec3> trail = new ArrayDeque<>();

    private int focusAge;
    private long startDay;
    private long targetDay;
    private boolean sweepTime;
    private boolean dealtDamage;
    private boolean dryRun;
    private boolean landedLocally;

    public SunsFuryEntity(EntityType<? extends SunsFuryEntity> type, Level level) {
        super(type, level);
    }

    public SunsFuryEntity(Level level, LivingEntity shooter) {
        this(ModEntities.SUNS_FURY.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, FLYING);
        builder.define(DATA_FOCUS_START, 0);
        builder.define(DATA_FOCUS_TICKS, Balance.SUNS_FURY_FOCUS_TICKS);
        builder.define(DATA_DIR, new Vector3f(0.0F, -1.0F, 0.0F));
        builder.define(DATA_SWEEP_FROM, 0L);
        builder.define(DATA_SWEEP_TO, 0L);
    }

    // ------------------------------------------------------------------ state (read by the client renderer)

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    public boolean isFocusing() {
        return state() == FOCUS;
    }

    public int focusTicks() {
        return Math.max(1, entityData.get(DATA_FOCUS_TICKS));
    }

    /** Focus progress 0..1, from the synced start (level game time) so late trackers see the right moment. */
    public double focusProgress(float partialTick) {
        if (!isFocusing()) {
            return 0.0;
        }
        if (!level().isClientSide()) {
            return SolarMath.progress(focusAge, focusTicks());
        }
        int age = (int) level().getGameTime() - entityData.get(DATA_FOCUS_START);
        return SolarMath.progress(Math.max(0, age) + partialTick, focusTicks());
    }

    /**
     * Client: the day time the focus sweep has reached at this frame, following the server's schedule
     * ({@link SolarMath#sweep}) between the once-per-tick time packets; -1 when this spear does not sweep the time.
     */
    public long smoothDayTime(float partialTick) {
        long from = entityData.get(DATA_SWEEP_FROM);
        long to = entityData.get(DATA_SWEEP_TO);
        if (!isFocusing() || to <= from) {
            return -1L;
        }
        return SolarMath.sweep(from, to, focusProgress(partialTick));
    }

    /** Unit direction the spear points along: its velocity in flight, its landing direction once embedded. */
    public Vec3 pointing(float partialTick) {
        if (isFocusing() || landedLocally) {
            return new Vec3(entityData.get(DATA_DIR)).normalize();
        }
        float yaw = Mth.lerp(partialTick, yRotO, getYRot()) * Mth.DEG_TO_RAD;
        float pitch = Mth.lerp(partialTick, xRotO, getXRot()) * Mth.DEG_TO_RAD;
        return new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }

    /** GameTest hook: the strike that follows this focus deals no damage and breaks nothing. */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected double getDefaultGravity() {
        return Balance.SUNS_FURY_GRAVITY;
    }

    @Override
    public void tick() {
        if (isFocusing()) {
            focusTick();
        } else {
            flyTick();
        }
    }

    private void flyTick() {
        super.tick();
        if (landedLocally) {
            return; // client: embedded locally, waiting for the server's focus state
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
            hitTargetOrDeflectSelf(hit);
        }
        if (isRemoved() || landedLocally || isFocusing()) {
            return;
        }
        checkInsideBlocks();
        Vec3 v = getDeltaMovement();
        double nx = getX() + v.x;
        double ny = getY() + v.y;
        double nz = getZ() + v.z;
        updateRotation();
        setDeltaMovement(v.scale(isInWater() ? 0.95 : 0.99));
        applyGravity();
        setPos(nx, ny, nz);
        if (level().isClientSide()) {
            flightParticles(v);
        } else if (tickCount >= Balance.SUNS_FURY_FUSE) {
            land(position(), v);
        }
    }

    private void flightParticles(Vec3 v) {
        trail.addLast(position());
        while (trail.size() > 10) {
            trail.removeFirst();
        }
        if (tickCount < 3) {
            return; // still at the thrower's eyes: motes there would fill their screen
        }
        for (int i = 0; i < 3; i++) {
            double f = random.nextDouble();
            level().addAlwaysVisibleParticle(ModParticles.LIGHT_MOTE.get(), true, getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f,
                    (random.nextDouble() - 0.5) * 0.06, (random.nextDouble() - 0.3) * 0.06, (random.nextDouble() - 0.5) * 0.06);
        }
        if (tickCount % 2 == 0) {
            level().addAlwaysVisibleParticle(ModParticles.SOLAR_GLINT.get(), true, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    private void focusTick() {
        super.tick();
        xRotO = getXRot();
        yRotO = getYRot();
        if (level() instanceof ServerLevel server) {
            SafeRun.run(SolarRayEntity.FEATURE, () -> serverFocus(server), this::discard);
        } else {
            if (!trail.isEmpty()) {
                trail.removeFirst(); // the trail retracts into the embedded spear
            }
            focusParticles();
        }
    }

    private void serverFocus(ServerLevel server) {
        focusAge++;
        int total = focusTicks();
        double p = SolarMath.progress(focusAge, total);
        if (sweepTime) {
            long now = server.getDayTime();
            long next = SolarMath.nextDayTime(now, startDay, targetDay, p);
            if (next != now) {
                server.setDayTime(next);
            }
            // every tick, so the sun visibly sweeps up the sky for everyone in the dimension
            server.getServer().getPlayerList().broadcastAll(new ClientboundSetTimePacket(server.getGameTime(), server.getDayTime(),
                    server.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)), server.dimension());
        }
        hum(server, focusAge, total, p);
        if (focusAge >= total) {
            strike(server);
        }
    }

    /** A rising hum while the light gathers: the beacon drone climbs in pitch, then a swell right before the strike. */
    private void hum(ServerLevel server, int age, int total, double p) {
        if (age % 6 == 0) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                    (float) (1.2 + 1.8 * p), (float) (0.5 + 1.4 * p));
        }
        if (age == Math.max(1, total - 32)) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.9F);
        }
        if (age == Math.max(1, total - 14)) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2.0F, 0.6F);
        }
    }

    private void focusParticles() {
        double e = SolarMath.ease(focusProgress(0.0F));
        double ring = ringRadius(e);
        int motes = 1 + (int) (e * 4.0);
        for (int i = 0; i < motes; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = ring * Math.sqrt(random.nextDouble());
            level().addAlwaysVisibleParticle(ModParticles.LIGHT_MOTE.get(), true, getX() + Math.cos(a) * r, getY() + 0.1,
                    getZ() + Math.sin(a) * r, 0.0, 0.04 + 0.12 * e + random.nextDouble() * 0.05, 0.0);
        }
        if (random.nextDouble() < 0.25 + 0.6 * e) {
            Vec3 d = pointing(0.0F);
            double f = random.nextDouble() * 1.2;
            level().addAlwaysVisibleParticle(ModParticles.SOLAR_GLINT.get(), true, getX() - d.x * f + (random.nextDouble() - 0.5) * 0.4,
                    getY() - d.y * f + (random.nextDouble() - 0.5) * 0.4, getZ() - d.z * f + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
        }
        if (e > 0.6 && random.nextDouble() < e - 0.5) {
            level().addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, getX() + (random.nextDouble() - 0.5) * ring,
                    getY() + 0.2, getZ() + (random.nextDouble() - 0.5) * ring, 0.0, 0.15 + 0.2 * e, 0.0);
        }
    }

    /** Radius of the target ring for eased focus progress {@code e}: it shrinks as the light concentrates. */
    public static double ringRadius(double e) {
        return SolarMath.lerp(e, 9.0, 1.5);
    }

    // ------------------------------------------------------------------ impacts

    @Override
    protected boolean canHitEntity(Entity target) {
        return !dealtDamage && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        dealtDamage = true;
        Entity target = result.getEntity();
        if (level() instanceof ServerLevel server) {
            SafeRun.run(SolarRayEntity.FEATURE, () -> {
                Entity owner = getOwner();
                if (target.hurt(ModDamageTypes.source(server, ModDamageTypes.SOLAR_RAY, this, owner), Balance.SUNS_FURY_IMPACT_DAMAGE)) {
                    target.igniteForSeconds(4.0F);
                }
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.8F);
            });
        }
        // like a trident: bounce off and fall, the focus starts where it lands
        setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01));
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        land(result.getLocation(), getDeltaMovement());
    }

    private void land(Vec3 at, Vec3 velocity) {
        if (isFocusing() || landedLocally) {
            return;
        }
        Vec3 dir = velocity.lengthSqr() > 1.0E-6 ? velocity.normalize() : new Vec3(0.0, -1.0, 0.0);
        landedLocally = true;
        entityData.set(DATA_DIR, dir.toVector3f());
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        if (level() instanceof ServerLevel server) {
            SafeRun.run(SolarRayEntity.FEATURE, () -> beginFocus(server, dir), this::discard);
        }
    }

    /** Starts the focus phase at the current position (also used by GameTests). */
    public void beginFocus(ServerLevel server, Vec3 dir) {
        landedLocally = true;
        entityData.set(DATA_DIR, dir.normalize().toVector3f());
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        focusAge = 0;
        entityData.set(DATA_FOCUS_TICKS, LacunaConfig.get(LacunaConfig.SOLAR_FOCUS_TICKS));
        entityData.set(DATA_FOCUS_START, (int) server.getGameTime());
        entityData.set(DATA_STATE, FOCUS);
        sweepTime = server.dimension() == Level.OVERWORLD && LacunaConfig.get(LacunaConfig.SOLAR_CHANGE_TIME);
        startDay = server.getDayTime();
        targetDay = SolarMath.nextNoon(startDay);
        if (sweepTime) {
            entityData.set(DATA_SWEEP_FROM, startDay);
            entityData.set(DATA_SWEEP_TO, targetDay);
        }
        if (sweepTime) {
            server.setWeatherParameters(12000, 0, false, false); // rain fades out naturally
        }
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.2F, 0.7F);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.5F, 0.6F);
        server.sendParticles(ModParticles.SOLAR_GLINT.get(), getX(), getY() + 0.2, getZ(), 12, 0.6, 0.3, 0.6, 0.05);
        hasImpulse = true; // push the final position to the clients right away
    }

    private void strike(ServerLevel server) {
        SolarRayEntity ray = new SolarRayEntity(ModEntities.SOLAR_RAY.get(), server);
        ray.setPos(getX(), getY(), getZ());
        Entity owner = getOwner();
        ray.setOwnerUuid(owner == null ? null : owner.getUUID());
        ray.setDryRun(dryRun);
        ray.markStart(server.getGameTime());
        server.addFreshEntity(ray);
        discard();
    }

    // ------------------------------------------------------------------ misc

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double max = isFocusing() ? 512.0 : 256.0;
        return distanceSqr < max * max;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (isFocusing()) {
            // the light cone leans toward the sun (up to ~40 degrees) and its shafts start on a ring 42 blocks wide
            return new AABB(getX() - 130, getY() - 4, getZ() - 130, getX() + 130, getY() + 270, getZ() + 130);
        }
        return getBoundingBox().inflate(2.0);
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
