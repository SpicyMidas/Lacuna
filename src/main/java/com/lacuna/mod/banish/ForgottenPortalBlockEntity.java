package com.lacuna.mod.banish;

import com.lacuna.mod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Forgotten Portal's block entity, used only for rendering (like vanilla's {@code TheEndPortalBlockEntity}): it
 * stores nothing and never ticks. Its renderer draws the surface at 12/16 and the underside at 6/16 with one
 * screen-space field, so the whole 2x2 opening reads as a single seamless surface.
 */
public class ForgottenPortalBlockEntity extends BlockEntity {
    public ForgottenPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORGOTTEN_PORTAL.get(), pos, state);
    }

    /** Only the horizontal faces are drawn; the underside is skipped when a full block hides it. */
    public boolean shouldRenderFace(Direction direction) {
        if (direction == Direction.UP) {
            return true;
        }
        if (direction != Direction.DOWN) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return true;
        }
        BlockPos below = getBlockPos().below();
        return !level.getBlockState(below).isSolidRender(level, below);
    }

    /** An empty update, so a surface restored on the server ({@link ForgottenPortals#restoreSurface}) reaches clients. */
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Client: a surface that turns up after its chunk section was built (a restored portal) is drawn at once. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
