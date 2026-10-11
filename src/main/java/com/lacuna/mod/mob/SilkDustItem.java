package com.lacuna.mod.mob;

import com.lacuna.mod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Silk Dust: right click a block face to leave a temporary glow decal there. */
public class SilkDustItem extends Item {
    public SilkDustItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos().relative(face);
        BlockState existing = level.getBlockState(pos);
        if (!existing.canBeReplaced() || !existing.getFluidState().isEmpty()) {
            return InteractionResult.PASS;
        }
        BlockState glow = ModBlocks.SILK_DUST_GLOW.get().defaultBlockState().setValue(SilkDustGlowBlock.FACING, face);
        if (!glow.canSurvive(level, pos)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, glow, 3);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.6F);
            Player player = context.getPlayer();
            context.getItemInHand().consume(1, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
