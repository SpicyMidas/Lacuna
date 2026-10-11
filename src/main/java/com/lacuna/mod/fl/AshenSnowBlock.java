package com.lacuna.mod.fl;

import com.lacuna.mod.Balance;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Ashen snow (Cinderfrost Flats surface, spec M10): stepping on it leaves a dark footprint (footprint = 3) that fades
 * one stage every 100 ticks through a scheduled tick.
 */
public class AshenSnowBlock extends Block {
    public static final MapCodec<AshenSnowBlock> CODEC = simpleCodec(AshenSnowBlock::new);
    public static final IntegerProperty FOOTPRINT = IntegerProperty.create("footprint", 0, 3);

    public AshenSnowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FOOTPRINT, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FOOTPRINT);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide() && entity instanceof LivingEntity && state.getValue(FOOTPRINT) < 3) {
            level.setBlock(pos, state.setValue(FOOTPRINT, 3), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, Balance.FOOTPRINT_FADE_TICKS);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int footprint = state.getValue(FOOTPRINT);
        if (footprint > 0) {
            level.setBlock(pos, state.setValue(FOOTPRINT, footprint - 1), Block.UPDATE_CLIENTS);
            if (footprint - 1 > 0) {
                level.scheduleTick(pos, this, Balance.FOOTPRINT_FADE_TICKS);
            }
        }
    }
}
