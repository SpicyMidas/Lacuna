package com.lacuna.mod.structure;

import com.lacuna.mod.util.SafeRun;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pressure-plate-like ring at the centre of a Mirror Labyrinth (no collision, unbreakable in survival). */
public class LabyrinthTriggerBlock extends Block {
    public static final MapCodec<LabyrinthTriggerBlock> CODEC = simpleCodec(LabyrinthTriggerBlock::new);
    public static final BooleanProperty SOLVED = BooleanProperty.create("solved");
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0);

    public LabyrinthTriggerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SOLVED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOLVED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof ServerPlayer player && level instanceof ServerLevel server && !state.getValue(SOLVED)) {
            SafeRun.run("mirror_labyrinth", () -> LabyrinthHandler.onTrigger(server, pos, state, player));
        }
    }
}
