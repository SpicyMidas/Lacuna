package com.lacuna.mod.fl;

import com.lacuna.mod.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Updraft Spring (spec M10, Inverted Falls): an upward "waterfall" without any custom fluid. Its block entity lifts
 * entities inside the 1x1 column above it, up to RANGE blocks (default 40) or the first motion-blocking block. The
 * block has no collision so stacked springs never trap anything rising through them.
 */
public class UpdraftSpringBlock extends BaseEntityBlock {
    public static final MapCodec<UpdraftSpringBlock> CODEC = simpleCodec(UpdraftSpringBlock::new);
    public static final IntegerProperty RANGE = IntegerProperty.create("range", 1, 40);

    public UpdraftSpringBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RANGE, 40));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RANGE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UpdraftSpringBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.UPDRAFT_SPRING.get(), UpdraftSpringBlockEntity::tick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 0.9, z, 0.0, 0.25 + random.nextDouble() * 0.15, 0.0);
        level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 1.0, z, 0.0, 0.1, 0.0);
        int range = state.getValue(RANGE);
        double h = random.nextDouble() * range;
        level.addParticle(ParticleTypes.SPLASH, x, pos.getY() + 1.0 + h, z, 0.0, 0.3, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FALLING_WATER, x, pos.getY() + 1.0 + random.nextDouble() * range, z, 0.0, 0.0, 0.0);
        }
    }
}
