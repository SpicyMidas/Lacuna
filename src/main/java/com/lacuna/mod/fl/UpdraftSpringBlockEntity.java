package com.lacuna.mod.fl;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lifts entities in the spring's column. Server side every 4 ticks for non-player entities (a 4-tick impulse, capped
 * at 0.4 blocks/tick); client side every tick for the local player only, because players move client-side. The
 * column height is re-scanned every 40 ticks; only the column AABB is queried.
 */
public class UpdraftSpringBlockEntity extends BlockEntity {
    private int height = -1;

    public UpdraftSpringBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UPDRAFT_SPRING.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, UpdraftSpringBlockEntity spring) {
        if (SafeRun.isDisabled("updraft_spring")) {
            return;
        }
        SafeRun.run("updraft_spring", () -> spring.doTick(level, pos, state));
    }

    private void doTick(Level level, BlockPos pos, BlockState state) {
        boolean client = level.isClientSide();
        long time = level.getGameTime();
        if (!client && (time + pos.asLong()) % Balance.UPDRAFT_INTERVAL != 0) {
            return;
        }
        if (!LacunaConfig.get(LacunaConfig.UPDRAFT_ENABLED)) {
            return;
        }
        if (height < 0 || time % 40 == 0) {
            height = scan(level, pos, state.getValue(UpdraftSpringBlock.RANGE));
        }
        AABB column = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1 + height, pos.getZ() + 1);
        for (Entity e : level.getEntities((Entity) null, column, e -> !e.isSpectator() && !e.isPassenger())) {
            if (e instanceof Player p) {
                if (p.getAbilities().flying) {
                    continue;
                }
                if (client && p.isLocalPlayer()) {
                    lift(e, 1);
                }
                if (!client) {
                    p.resetFallDistance();
                }
            } else if (!client) {
                lift(e, Balance.UPDRAFT_INTERVAL);
                e.resetFallDistance();
                e.hurtMarked = true;
            }
        }
    }

    /** Net upward acceleration of 0.08 per tick (gravity is compensated), capped at 0.4. */
    private static void lift(Entity e, int ticks) {
        Vec3 v = e.getDeltaMovement();
        double vy = Math.min(Balance.UPDRAFT_MAX, v.y + (e.getGravity() + Balance.UPDRAFT_ACCEL) * ticks);
        if (vy > v.y) {
            e.setDeltaMovement(v.x, vy, v.z);
        }
    }

    static int scan(Level level, BlockPos pos, int range) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int dy = 1; dy <= range; dy++) {
            p.setY(pos.getY() + dy);
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof UpdraftSpringBlock) && s.blocksMotion()) {
                return dy - 1;
            }
        }
        return range;
    }
}
