package com.lacuna.mod.fl;

import com.lacuna.mod.Balance;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Spore Mushroom (Whispering Marsh, spec M10): light 7, drifting spores; every 20 ticks it makes living entities
 * within 6 blocks glow for 60 ticks, which reveals crouching Gloomcrawlers. Worldgen does not run onPlace, so a
 * random tick starts (or restarts) the 20-tick cycle when none is scheduled; the work is skipped when no player is
 * within 48 blocks.
 */
public class SporeMushroomBlock extends BushBlock {
    public static final MapCodec<SporeMushroomBlock> CODEC = simpleCodec(SporeMushroomBlock::new);
    private static final VoxelShape SHAPE = BushBlock.box(4.0, 0.0, 4.0, 12.0, 9.0, 12.0);

    public SporeMushroomBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.LOGS) || state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, Balance.SPORE_INTERVAL);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, Balance.SPORE_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 48.0, false) != null) {
            revealAround(level, pos);
        }
        level.scheduleTick(pos, this, Balance.SPORE_INTERVAL);
    }

    /** Makes every living entity within 6 blocks glow for 60 ticks. Returns how many were affected. */
    public static int revealAround(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(Balance.SPORE_RADIUS);
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, Balance.SPORE_GLOW_TICKS, 0, true, false, true));
            n++;
        }
        return n;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + random.nextDouble(), pos.getY() + 0.6 + random.nextDouble() * 0.6,
                    pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
        }
    }
}
