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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Forgotten Portal: a flat, see-through surface at 12/16 like the End portal (no collision, unbreakable, no
 * drops). Whatever stands in it long enough is sent through by {@link ForgottenPortals} with the banishment rules;
 * it falls apart as soon as one of its horizontal neighbours is neither portal nor frame.
 */
public class ForgottenPortalBlock extends Block {
    public static final MapCodec<ForgottenPortalBlock> CODEC = simpleCodec(ForgottenPortalBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);
    /** Below the surface: anything whose box reaches in here is standing in the portal. */
    private static final double INSIDE_TOP = 12.0 / 16.0;

    public ForgottenPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity.getBoundingBox().intersects(
                new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + INSIDE_TOP, pos.getZ() + 1.0))) {
            ForgottenPortals.inside(server, entity);
        }
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
            level.addParticle(ModParticles.MIST.get(), x, y + 0.3, z, 0.0, 0.004, 0.0);
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
