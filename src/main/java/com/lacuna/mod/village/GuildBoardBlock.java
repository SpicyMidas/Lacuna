package com.lacuna.mod.village;

import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Guild board (spec 18.4): one per guild in the Guild Hall. 20 coins (reputation ≥ 10, no other guild) to join;
 * members take one contract at a time and hand it in here. Creative players cycle the guild with sneak + empty hand.
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        boolean coins = stack.is(ModItems.VILLAGE_COIN.get());
        boolean contract = stack.is(ModItems.GUILD_CONTRACT.get());
        if (!coins && !contract) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            GuildType guild = state.getValue(GUILD);
            SafeRun.run(VillageService.FEATURE, () -> VillageService.atOrTell(server, pos, sp).ifPresent(t -> {
                if (coins) {
                    Guilds.join(server, t, sp, guild, stack);
                } else {
                    Guilds.handIn(server, t, sp, guild, stack);
                }
            }));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
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
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            SafeRun.run(VillageService.FEATURE, () -> VillageService.atOrTell(server, pos, sp)
                    .ifPresent(t -> Guilds.offerContract(server, t, sp, state.getValue(GUILD))));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
