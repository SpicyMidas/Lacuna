package com.lacuna.mod.village;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Guild board (spec 18.4): one per guild in the Guild Hall. Right click opens the guild screen: 20 coins (reputation
 * ≥ 10, no other guild) to join; members take one contract at a time and hand it in there. Creative players cycle the
 * guild with sneak + empty hand.
 */
public class GuildBoardBlock extends CivicBlock {
    public static final EnumProperty<GuildType> GUILD = EnumProperty.create("guild", GuildType.class);

    public GuildBoardBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
        registerDefaultState(defaultBlockState().setValue(GUILD, GuildType.SMITHS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GUILD);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isCreative() && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                GuildType next = GuildType.values()[(state.getValue(GUILD).ordinal() + 1) % GuildType.values().length];
                level.setBlock(pos, state.setValue(GUILD, next), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
