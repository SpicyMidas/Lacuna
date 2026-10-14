package com.lacuna.mod.pearl;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Thrown Arrival Shard: no damage, never teleports the thrower; locks on blocks, otherwise drops back. */
public class ArrivalShardEntity extends ThrowableItemProjectile {
    public ArrivalShardEntity(EntityType<? extends ArrivalShardEntity> type, Level level) {
        super(type, level);
    }

    public ArrivalShardEntity(Level level, LivingEntity shooter) {
        super(ModEntities.ARRIVAL_SHARD.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ARRIVAL_SHARD.get();
    }

    @Override
    protected double getDefaultGravity() {
        return Balance.ARRIVAL_SHARD_GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !isRemoved()) {
            BlockPos pos = blockPosition();
            boolean voidOrUnloaded = getY() < level().getMinBuildHeight() - 8 || !level().hasChunkAt(pos);
            if (voidOrUnloaded) {
                dropBackAt(level().hasChunkAt(pos) ? null : pos);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // no damage: just fall back as an item
        if (!level().isClientSide()) {
            dropBackAt(null);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        ServerLevel server = (ServerLevel) level();
        Player thrower = getOwner() instanceof Player p ? p : null;
        boolean locked = SafeRun.call(PearlLinks.FEATURE,
                () -> PearlLinks.lock(server, result.getBlockPos(), result.getDirection(), getItem(), thrower), false);
        if (locked) {
            discard();
        } else {
            dropBackAt(null);
        }
    }

    /** Returns the shard as an item at the current position, or to the thrower if that position is unusable. */
    private void dropBackAt(BlockPos unusable) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (unusable != null || getY() < level().getMinBuildHeight()) {
            if (getOwner() instanceof Player p && !p.isRemoved()) {
                com.lacuna.mod.util.Give.toPlayer(p, getItem().copy());
            }
        } else {
            PearlLinks.dropBack(server, position(), getItem());
        }
        discard();
    }
}
