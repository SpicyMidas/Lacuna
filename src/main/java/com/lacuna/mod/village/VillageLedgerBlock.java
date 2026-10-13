package com.lacuna.mod.village;

import com.lacuna.mod.util.SafeRun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The village ledger. Right click: the summary of the village it stands in (reputation, laws, policy, coin).
 * Placed (or first used) outside every village, it founds a player village of radius 48 around itself, so
 * player-built settlements join the civic system; breaking that ledger dissolves the founded village.
 */
public class VillageLedgerBlock extends CivicBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 10, 14), Block.box(0, 10, 0, 16, 13, 16));

    public VillageLedgerBlock(Properties properties) {
        super(properties, SHAPE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer instanceof ServerPlayer sp) {
            SafeRun.run(VillageService.FEATURE, () -> VillageService.foundPlayerVillage(server, pos, sp).ifPresent(v -> {
                boolean founded = v.kind == VillageRecord.Kind.PLAYER && v.center.equals(pos);
                sp.displayClientMessage(Component.translatable(founded ? "lacuna.village.founded" : "lacuna.village.joined",
                        VillageService.villageName(v)).withStyle(ChatFormatting.GOLD), false);
                if (founded) {
                    server.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.6F, 1.2F);
                }
            }));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            SafeRun.run(VillageService.FEATURE, () -> VillageService.foundPlayerVillage(server, pos, null)
                    .ifPresent(t -> VillageService.describe(server, t, sp)));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            SafeRun.run(VillageService.FEATURE, () -> VillageService.dissolvePlayerVillage(server, pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
