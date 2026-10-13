package com.lacuna.mod.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The solar column that ends a Sun's Fury focus (spec 11.3): a {@link Balance#SOLAR_RAY_TICKS}-tick STRIKE — 5 damage every
 * 5 ticks + 10 s fire within radius 7, and the budgeted block-destruction job (rings outward, <= 400 changes per tick,
 * <= 12000 per use, only with mobGriefing and mayInteract) — then a {@link Balance#SOLAR_FADE_TICKS}-tick harmless FADE.
 * The pillar of light, rings, flash and shake are drawn by the clients (client/solar). Never saved (entity type is
 * noSave): the job lives only in memory.
 */
public class SolarRayEntity extends Entity {
    public static final String FEATURE = "suns_fury";
    private static final EntityDataAccessor<Integer> DATA_START = SynchedEntityData.defineId(SolarRayEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID owner;
    private List<BlockPos> job;
    private int cursor;
    private int changes;
    private boolean dryRun;

    public SolarRayEntity(EntityType<? extends SolarRayEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setOwnerUuid(@Nullable UUID owner) {
        this.owner = owner;
    }

    /** GameTest hook: no damage, no block destruction (the visuals and sounds still play). */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /** Records the game time the strike started (synced, so clients agree on the column's age). */
    public void markStart(long gameTime) {
        entityData.set(DATA_START, (int) gameTime);
    }

    /** Ticks since the strike started, with the partial tick (client and server). */
    public double age(float partialTick) {
        int start = entityData.get(DATA_START);
        if (start == 0) {
            return tickCount + partialTick;
        }
        return Math.max(0, (int) level().getGameTime() - start) + partialTick;
    }

    public SolarMath.RayPhase phase() {
        return SolarMath.rayPhase((int) age(0.0F), Balance.SOLAR_RAY_TICKS, Balance.SOLAR_FADE_TICKS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_START, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            SafeRun.run(FEATURE, () -> serverTick(server), this::discard);
        } else {
            clientParticles();
        }
    }

    private void serverTick(ServerLevel server) {
        if (tickCount == 1) {
            if (entityData.get(DATA_START) == 0) {
                markStart(server.getGameTime() - 1); // summoned directly (debug): starts now
            }
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.75F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 0.6F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.8F);
            LacunaFx.send(server, position(), 256.0, FxPayload.at("solar_strike", position()));
            job = dryRun ? List.of() : buildJob(server);
        }
        SolarMath.RayPhase phase = SolarMath.rayPhase(tickCount, Balance.SOLAR_RAY_TICKS, Balance.SOLAR_FADE_TICKS);
        if (phase == SolarMath.RayPhase.STRIKE) {
            if (!dryRun && tickCount % Balance.SOLAR_RAY_DAMAGE_INTERVAL == 0) {
                burn(server);
            }
            if (job != null && cursor < job.size() && changes < LacunaConfig.get(LacunaConfig.SOLAR_MAX_CHANGES_PER_USE)) {
                destroySome(server);
            }
            if (tickCount % 10 == 5) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2.5F, 0.5F);
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0F, 2.0F);
            }
        } else if (phase == SolarMath.RayPhase.FADE) {
            if (tickCount == Balance.SOLAR_RAY_TICKS + 1) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.5F, 0.7F);
            }
        } else {
            discard();
        }
    }

    /** Client: light motes pouring up the column, sparks at its foot, and the column dissolving into motes as it fades. */
    private void clientParticles() {
        double age = age(0.0F);
        double intensity = SolarMath.columnIntensity(age, Balance.SOLAR_RAY_TICKS, Balance.SOLAR_FADE_TICKS);
        if (intensity <= 0.0) {
            return;
        }
        boolean strike = age <= Balance.SOLAR_RAY_TICKS;
        if (age <= 2) {
            for (int i = 0; i < 40; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double s = 0.4 + random.nextDouble() * 0.9;
                level().addAlwaysVisibleParticle(ModParticles.SOLAR_GLINT.get(), true, getX(), getY() + 0.5, getZ(),
                        Math.cos(a) * s, random.nextDouble() * 0.3, Math.sin(a) * s);
            }
            level().addAlwaysVisibleParticle(ParticleTypes.FLASH, true, getX(), getY() + 1.0, getZ(), 0, 0, 0);
        }
        int motes = strike ? 7 : (int) (10 * intensity);
        for (int i = 0; i < motes; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = strike ? 0.8 + random.nextDouble() * 4.5 : random.nextDouble() * 3.0;
            double y = strike ? getY() + random.nextDouble() * 6.0 : getY() + random.nextDouble() * 70.0;
            double up = strike ? 0.25 + random.nextDouble() * 0.6 : 0.02 + random.nextDouble() * 0.05;
            level().addAlwaysVisibleParticle(ModParticles.LIGHT_MOTE.get(), true, getX() + Math.cos(a) * r, y, getZ() + Math.sin(a) * r,
                    Math.cos(a) * 0.03, up, Math.sin(a) * 0.03);
        }
        if (strike) {
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = random.nextDouble() * 7.0;
                level().addAlwaysVisibleParticle(ModParticles.SOLAR_GLINT.get(), true, getX() + Math.cos(a) * r, getY() + 0.2,
                        getZ() + Math.sin(a) * r, 0.0, 0.05, 0.0);
            }
            if (tickCount % 2 == 0) {
                level().addAlwaysVisibleParticle(ParticleTypes.LAVA, true, getX() + (random.nextDouble() - 0.5) * 6.0, getY() + 0.3,
                        getZ() + (random.nextDouble() - 0.5) * 6.0, 0, 0, 0);
            }
        } else if (tickCount % 3 == 0) {
            level().addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, getX() + (random.nextDouble() - 0.5) * 8.0, getY() + 0.5,
                    getZ() + (random.nextDouble() - 0.5) * 8.0, 0.0, 0.06, 0.0);
        }
    }

    private void burn(ServerLevel server) {
        double r = Balance.SOLAR_RAY_DAMAGE_RADIUS;
        AABB box = new AABB(getX() - r, getY() - 8, getZ() - r, getX() + r, getY() + Math.min(Balance.SOLAR_RAY_HEIGHT, 56), getZ() + r);
        Entity cause = owner == null ? null : server.getEntity(owner);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            double dx = e.getX() - getX();
            double dz = e.getZ() - getZ();
            if (dx * dx + dz * dz > r * r) {
                continue;
            }
            if (e.hurt(ModDamageTypes.source(server, ModDamageTypes.SOLAR_RAY, this, cause), Balance.SOLAR_RAY_DAMAGE)) {
                e.igniteForSeconds(Balance.SOLAR_RAY_FIRE_SECONDS);
            }
        }
    }

    private List<BlockPos> buildJob(ServerLevel server) {
        List<BlockPos> list = new ArrayList<>();
        if (!server.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return list;
        }
        int radius = LacunaConfig.get(LacunaConfig.SOLAR_DESTRUCTION_RADIUS);
        BlockPos center = blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int dy = -Balance.SOLAR_DESTRUCTION_BELOW; dy <= Balance.SOLAR_DESTRUCTION_ABOVE; dy++) {
                    list.add(center.offset(dx, dy, dz));
                }
            }
        }
        // sweep outward in rings, bottom to top inside a ring
        list.sort(Comparator.<BlockPos>comparingInt(p -> (p.getX() - center.getX()) * (p.getX() - center.getX())
                + (p.getZ() - center.getZ()) * (p.getZ() - center.getZ())).thenComparingInt(BlockPos::getY));
        return list;
    }

    private void destroySome(ServerLevel server) {
        int budget = LacunaConfig.get(LacunaConfig.BLOCK_CHANGES_PER_TICK);
        int maxTotal = LacunaConfig.get(LacunaConfig.SOLAR_MAX_CHANGES_PER_USE);
        Entity cause = owner == null ? null : server.getEntity(owner);
        Player player = cause instanceof Player p ? p : null;
        int done = 0;
        int inspected = 0;
        while (cursor < job.size() && done < budget && changes < maxTotal && inspected < budget * 8) {
            BlockPos pos = job.get(cursor++);
            inspected++;
            if (pos.getY() < server.getMinBuildHeight() || pos.getY() >= server.getMaxBuildHeight()
                    || !server.hasChunkAt(pos) || !server.getWorldBorder().isWithinBounds(pos)) {
                continue;
            }
            BlockState state = server.getBlockState(pos);
            if (state.isAir() || state.is(ModTags.Blocks.SOLAR_IMMUNE) || state.is(Blocks.BEDROCK)) {
                continue;
            }
            float hardness = state.getDestroySpeed(server, pos);
            if (hardness < 0 || hardness > Balance.SOLAR_MAX_HARDNESS) {
                continue;
            }
            if (player != null && !server.mayInteract(player, pos)) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                continue;
            }
            server.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            done++;
            changes++;
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 640.0 * 640.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(getX() - 28, getY() - 12, getZ() - 28, getX() + 28, getY() + 420, getZ() + 28);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return true;
    }
}
