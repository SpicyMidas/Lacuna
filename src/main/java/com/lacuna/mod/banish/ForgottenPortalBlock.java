package com.lacuna.mod.banish;

import com.lacuna.mod.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Forgotten Portal: a flat surface at 12/16 like the End portal (no collision, unbreakable, no drops). The block
 * model is invisible: its {@link ForgottenPortalBlockEntity} renderer draws one screen-space field across the whole
 * opening, so the four blocks merge into a single seamless surface. Whatever touches the portal plane is sent
 * through by {@link ForgottenPortals} at the end of that server tick, with the banishment rules. The portal falls
 * apart as soon as one of its horizontal neighbours is neither portal nor frame.
 */
public class ForgottenPortalBlock extends Block implements EntityBlock {
    public static final MapCodec<ForgottenPortalBlock> CODEC = simpleCodec(ForgottenPortalBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);

    public ForgottenPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgottenPortalBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // the block entity renderer draws the surface
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** True when the entity's box reaches into the portal plane (EndPortalBlock's test); shared with the client. */
    public static boolean touches(Entity entity, BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.joinIsNotEmpty(Shapes.create(entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ())),
                state.getShape(level, pos), BooleanOp.AND);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity.canUsePortal(false) && touches(entity, state, level, pos)) {
            ForgottenPortals.entered(server, entity, pos);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** Portals lit before the surface had a block entity draw nothing: restore it (touching a frame with the totem does too). */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ForgottenPortals.restoreSurface(level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        // only the neighbour that changed is checked, so the four portal blocks can be placed one after the other
        if (direction.getAxis().isHorizontal() && !ForgottenPortals.isPortal(neighborState) && !ForgottenPortals.isFrame(neighborState)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + 0.8;
        double z = pos.getZ() + random.nextDouble();
        int roll = random.nextInt(12);
        if (roll < 6) {
            level.addParticle(ModParticles.SHADOW_MOTE.get(), x, y, z, 0.0, 0.01 + random.nextDouble() * 0.02, 0.0);
        } else if (roll < 11) {
            level.addParticle(ModParticles.LIGHT_MOTE.get(), x, y, z, (random.nextDouble() - 0.5) * 0.01,
                    0.008 + random.nextDouble() * 0.015, (random.nextDouble() - 0.5) * 0.01);
        } else {
            // now and then a pale rift spark of the totem rises off the surface (Lacuna's own, no vanilla portal motes)
            level.addParticle(ModParticles.RIFT_SPARK.get(), x, y, z, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
        }
        if (random.nextInt(100) == 0) {
            // the void hums, low
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS,
                    0.45F, 0.45F + random.nextFloat() * 0.1F, false);
        }
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    /** Mobs never walk into it on their own (pushed in, they are banished like anything else). */
    @Nullable
    @Override
    public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        return PathType.DAMAGE_OTHER;
    }
}
