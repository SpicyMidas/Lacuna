package com.lacuna.mod.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import org.joml.Vector3f;

/**
 * The solar column left by Sun's Fury: 60 ticks of light, 5 damage every 5 ticks + 10 s fire within radius 7, and a
 * budgeted block-destruction job (rings outward, <= 400 changes per tick, <= 12000 per use, only with mobGriefing
 * and mayInteract). Never saved: the job lives only in memory (entity type is noSave).
 */
public class SolarRayEntity extends Entity {
    public static final String FEATURE = "suns_fury";
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.24F), 2.0F);

    @Nullable
    private UUID owner;
    private List<BlockPos> job;
    private int cursor;
    private int changes;

    public SolarRayEntity(EntityType<? extends SolarRayEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setOwnerUuid(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        SafeRun.run(FEATURE, () -> serverTick(server), this::discard);
    }

    private void serverTick(ServerLevel server) {
        if (tickCount == 1) {
            server.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 0.7F);
            server.playSound(null, blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.8F, 1.2F);
            job = buildJob(server);
        }
        boolean rayActive = tickCount <= Balance.SOLAR_RAY_TICKS;
        if (rayActive) {
            column(server);
            if (tickCount % Balance.SOLAR_RAY_DAMAGE_INTERVAL == 0) {
                burn(server);
            }
        }
        boolean jobActive = job != null && cursor < job.size() && changes < LacunaConfig.get(LacunaConfig.SOLAR_MAX_CHANGES_PER_USE);
        if (jobActive) {
            destroySome(server);
        }
        if (!rayActive && !jobActive) {
            discard();
        }
    }

    private void column(ServerLevel server) {
        double x = getX();
        double z = getZ();
        for (int i = 0; i < 8; i++) { // 8 particle packets per tick (budget 40)
            double y = getY() + random.nextDouble() * Balance.SOLAR_RAY_HEIGHT;
            server.sendParticles(ParticleTypes.END_ROD, x, y, z, 3, 1.0, 2.0, 1.0, 0.02);
            if (i % 2 == 0) {
                server.sendParticles(GOLD, x, y, z, 3, 1.0, 2.0, 1.0, 0.0);
            }
            if (i % 4 == 0) {
                server.sendParticles(ParticleTypes.FLAME, x, getY() + random.nextDouble() * 6, z, 4, 1.5, 0.5, 1.5, 0.02);
            }
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
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
