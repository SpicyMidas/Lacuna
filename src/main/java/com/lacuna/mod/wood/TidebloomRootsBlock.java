package com.lacuna.mod.wood;

import com.lacuna.mod.LacunaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Tidebloom roots: waterloggable; splash on break; 1 % random tick chance to spawn a cod/salmon in adjacent water. */
public class TidebloomRootsBlock extends MangroveRootsBlock {
    public TidebloomRootsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!LacunaConfig.get(LacunaConfig.TIDEBLOOM_FISH) || random.nextInt(100) != 0) {
            return;
        }
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) {
                continue;
            }
            BlockPos water = pos.relative(d);
            if (!level.hasChunkAt(water) || !level.getFluidState(water).is(FluidTags.WATER) || !level.getBlockState(water).getCollisionShape(level, water).isEmpty()) {
                continue;
            }
            int fish = level.getEntitiesOfClass(AbstractFish.class, new AABB(pos).inflate(8.0)).size();
            if (fish >= 3) {
                return;
            }
            EntityType<? extends AbstractFish> type = random.nextBoolean() ? EntityType.COD : EntityType.SALMON;
            type.spawn(level, water, MobSpawnType.NATURAL);
            return;
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 16, 0.3, 0.2, 0.3, 0.1);
            server.sendParticles(ParticleTypes.BUBBLE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
