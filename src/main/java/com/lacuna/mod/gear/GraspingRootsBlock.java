package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModEffects;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * Grasping Roots (Heartwood Bow): a temporary tangle of roots that sprouts where a heartwood arrow lands. No
 * collision; anything inside is held like in a cobweb, only harder, and living entities are kept Rooted. The roots
 * live 6 s around a hit target ({@code sprouted=false}) or 10 s on terrain ({@code sprouted=true}); their last second
 * is a withering stage ({@code withering=true}: dry texture, looser grip) before they crumble away. Instant to break,
 * no drops, no item; they only grow into air, short grass, ferns or thin snow above a sturdy top face, never into
 * fluids. The lifetime runs on scheduled ticks (saved with the chunk); a random tick re-schedules a lost one.
 */
public class GraspingRootsBlock extends Block {
    public static final MapCodec<GraspingRootsBlock> CODEC = simpleCodec(GraspingRootsBlock::new);
    public static final BooleanProperty SPROUTED = BooleanProperty.create("sprouted");
    public static final BooleanProperty WITHERING = BooleanProperty.create("withering");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);
    private static final Vec3 GRIP = new Vec3(0.15, 0.05, 0.15);
    private static final Vec3 LOOSE_GRIP = new Vec3(0.45, 0.3, 0.45);
    private static final DustParticleOptions MOSS = new DustParticleOptions(new Vector3f(0.36F, 0.6F, 0.24F), 1.0F);
    private static final DustParticleOptions BARK = new DustParticleOptions(new Vector3f(0.4F, 0.26F, 0.13F), 1.2F);

    public GraspingRootsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SPROUTED, false).setValue(WITHERING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPROUTED, WITHERING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ------------------------------------------------------------------------------------------ survival

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    // ------------------------------------------------------------------------------------------ lifetime

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            int life = state.getValue(SPROUTED) ? Balance.GRASPING_ROOTS_SPROUT_TICKS : Balance.GRASPING_ROOTS_TARGET_TICKS;
            // a little jitter so a patch withers root by root instead of all at once
            level.scheduleTick(pos, this, Math.max(1, life - Balance.GRASPING_ROOTS_WITHER_TICKS + level.random.nextInt(9) - 4));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WITHERING)) {
            level.setBlock(pos, state.setValue(WITHERING, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, Balance.GRASPING_ROOTS_WITHER_TICKS + random.nextInt(6));
            level.playSound(null, pos, SoundEvents.HANGING_ROOTS_STEP, SoundSource.BLOCKS, 0.5F, 0.6F + random.nextFloat() * 0.2F);
            return;
        }
        crumble(level, pos, random);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, Balance.GRASPING_ROOTS_WITHER_TICKS);
        }
    }

    /** The roots let go: a puff of dry earth and root splinters, and a soft crumbling sound. */
    private static void crumble(ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.3;
        double z = pos.getZ() + 0.5;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), x, y, z, 8, 0.3, 0.2, 0.3, 0.05);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HANGING_ROOTS.defaultBlockState()), x, y + 0.2, z, 5, 0.25, 0.3, 0.25, 0.05);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 2, 0.2, 0.1, 0.2, 0.01);
        level.playSound(null, pos, random.nextBoolean() ? SoundEvents.ROOTED_DIRT_BREAK : SoundEvents.HANGING_ROOTS_BREAK,
                SoundSource.BLOCKS, 0.6F, 0.8F + random.nextFloat() * 0.3F);
    }

    // ------------------------------------------------------------------------------------------ the grip

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator()
                || player.getMainHandItem().getItem() instanceof HeartwoodBowItem
                || player.getOffhandItem().getItem() instanceof HeartwoodBowItem)) {
            return; // the roots know the archer: whoever holds a Heartwood Bow walks through them
        }
        boolean withering = state.getValue(WITHERING);
        entity.makeStuckInBlock(state, withering ? LOOSE_GRIP : GRIP);
        if (!withering && !level.isClientSide() && entity instanceof LivingEntity living && living.isAlive()) {
            MobEffectInstance rooted = living.getEffect(ModEffects.ROOTED);
            if (rooted == null || rooted.getDuration() < 4) {
                living.addEffect(new MobEffectInstance(ModEffects.ROOTED, Balance.GRASPING_ROOTS_HOLD_TICKS, 0, false, false, true));
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean withering = state.getValue(WITHERING);
        if (random.nextInt(withering ? 2 : 9) != 0) {
            return;
        }
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double y = pos.getY() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        if (withering) {
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.ROOTED_DIRT.defaultBlockState()), x, y, z, 0, 0, 0);
        } else {
            level.addParticle(random.nextBoolean() ? MOSS : BARK, x, y, z, 0, 0.01, 0);
        }
    }

    // ------------------------------------------------------------------------------------------ growing

    /** Air, short grass, a fern or thin snow without fluid, above a sturdy top face: roots may grow here. */
    public static boolean canGrowAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        BlockState here = level.getBlockState(pos);
        if (!here.getFluidState().isEmpty()) {
            return false;
        }
        boolean free = here.isAir() || here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN)
                || (here.is(Blocks.SNOW) && here.getValue(SnowLayerBlock.LAYERS) <= 2);
        if (!free) {
            return false;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** Grows one root tangle at {@code pos} if allowed; returns whether it did. */
    public static boolean grow(ServerLevel level, BlockPos pos, boolean sprouted) {
        if (!canGrowAt(level, pos)) {
            return false;
        }
        BlockState roots = ModBlocks.GRASPING_ROOTS.get().defaultBlockState().setValue(SPROUTED, sprouted);
        if (!level.setBlock(pos, roots, Block.UPDATE_ALL)) {
            return false;
        }
        burst(level, pos);
        return true;
    }

    /**
     * Finds the ground under {@code start}: {@code start} itself or up to two air blocks below it. Returns null when
     * no spot is found (or an existing tangle when {@code acceptRoots} and one is in the way).
     */
    private static BlockPos ground(ServerLevel level, BlockPos start, boolean acceptRoots) {
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos p = start.below(dy);
            if (canGrowAt(level, p) || (acceptRoots && level.getBlockState(p).is(ModBlocks.GRASPING_ROOTS.get()))) {
                return p;
            }
            if (!level.getBlockState(p).isAir()) {
                return null;
            }
        }
        return null;
    }

    /**
     * Entity hit: roots burst up in the target's feet block (or the ground just under a jumping target) and the four
     * blocks around it. Returns the number of tangles grown (at most {@code cap}).
     */
    public static int entangle(ServerLevel level, LivingEntity target, int cap) {
        BlockPos base = ground(level, target.blockPosition(), true);
        if (base == null) {
            return 0;
        }
        int placed = 0;
        List<BlockPos> spots = new ArrayList<>(5);
        spots.add(base);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            spots.add(base.relative(d));
        }
        for (BlockPos spot : spots) {
            if (placed >= cap) {
                break;
            }
            if (grow(level, spot, false)) {
                placed++;
            }
        }
        if (placed > 0) {
            sounds(level, base, true);
        }
        return placed;
    }

    /**
     * Block hit: a small patch sprouts on the struck face's side (falling to the ground below it if needed), plus 2-4
     * neighbouring tangles. Returns the number of tangles grown (at most {@code cap}).
     */
    public static int sprout(ServerLevel level, BlockPos struck, Direction face, int cap) {
        BlockPos base = ground(level, struck.relative(face), false);
        if (base == null || !grow(level, base, true)) {
            return 0;
        }
        RandomSource random = level.random;
        int placed = 1;
        int extra = 2 + random.nextInt(3);
        List<BlockPos> around = new ArrayList<>(8);
        for (Direction d : Util.toShuffledList(Direction.Plane.HORIZONTAL.stream(), random)) {
            around.add(base.relative(d));
        }
        // diagonals only fill in for blocked neighbours
        for (Direction d : Util.toShuffledList(Direction.Plane.HORIZONTAL.stream(), random)) {
            around.add(base.relative(d).relative(d.getClockWise()));
        }
        for (BlockPos spot : around) {
            if (extra <= 0 || placed >= cap) {
                break;
            }
            if (grow(level, spot, true)) {
                placed++;
                extra--;
            }
        }
        sounds(level, base, false);
        return placed;
    }

    /** Earth and root splinters burst from the ground, with a little green. */
    private static void burst(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.1;
        double z = pos.getZ() + 0.5;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), x, y, z, 10, 0.3, 0.05, 0.3, 0.15);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MANGROVE_ROOTS.defaultBlockState()), x, y + 0.3, z, 6, 0.25, 0.2, 0.25, 0.1);
        level.sendParticles(MOSS, x, y + 0.4, z, 5, 0.3, 0.35, 0.3, 0.0);
        level.sendParticles(BARK, x, y + 0.2, z, 4, 0.3, 0.2, 0.3, 0.0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.5, z, 2, 0.3, 0.3, 0.3, 0.0);
    }

    /** Layered root sounds: a low earthy crunch, a woody creak and a high rustle (one set per arrow). */
    private static void sounds(ServerLevel level, BlockPos pos, boolean onTarget) {
        RandomSource r = level.random;
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 1.0F, 0.75F + r.nextFloat() * 0.2F);
        level.playSound(null, pos, SoundEvents.MANGROVE_ROOTS_PLACE, SoundSource.PLAYERS, 0.9F, 0.55F + r.nextFloat() * 0.15F);
        level.playSound(null, pos, SoundEvents.HANGING_ROOTS_PLACE, SoundSource.PLAYERS, 0.8F, 1.2F + r.nextFloat() * 0.3F);
        if (onTarget) {
            // the snap of roots closing around legs
            level.playSound(null, pos, SoundEvents.ROOTS_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F + r.nextFloat() * 0.1F);
        }
    }
}
