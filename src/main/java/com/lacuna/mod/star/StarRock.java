package com.lacuna.mod.star;

import com.lacuna.mod.Lacuna;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;

/**
 * Star Rock: what a falling star leaves where it struck, a full block that cools through three stages, each its own
 * block (own name, loot table and Silk Touch item):
 * <ol>
 *   <li>{@link MoltenStarRockBlock} (light 15, drawn full bright): placed by the impact. A hot floor like magma, it smokes,
 *       spits lava and sizzles. After {@link #MOLTEN_MIN}-{@code +}{@link #MOLTEN_SPREAD} ticks (1-2 minutes) it cools to
 *       Prime; water touching it (or rain falling on it) quenches it at once with a hiss of steam. Mined: 1-2 Star
 *       Fragments, rarely a Solar Flare Fragment.</li>
 *   <li>{@link PrimeStarRockBlock} (light 10, its crystals drawn full bright): the star at its best, glinting and chiming
 *       now and then so it can be found by ear. Mined: 1-3 Star Fragments (+Fortune). After about three days
 *       ({@link #PRIME_MIN}-{@code +}{@link #PRIME_SPREAD} ticks) it burns out.</li>
 *   <li>Extinguished Block of the Star (a plain block, final): grey cratered moon rock, dark and silent. Mined: 0-1 Star
 *       Fragment.</li>
 * </ol>
 * The timers are scheduled block ticks (saved with the chunk, so they only run while the chunk is loaded, like crops);
 * a random tick re-arms a missing timer (a block moved by a piston, or the legacy {@code lacuna:fallen_star}, which is
 * aliased to Prime Star Rock). Placed again from a Silk Touch item, a stage starts its timer over.
 */
public final class StarRock {
    /** All three stages (the falling star's crater never digs one up). */
    public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK, Lacuna.id("star_rocks"));

    /** Molten -> Prime: 1-2 minutes. */
    public static final int MOLTEN_MIN = 1200;
    public static final int MOLTEN_SPREAD = 1200;
    /** Molten placed against water still glows for a moment before it hisses out. */
    public static final int QUENCH_MIN = 10;
    public static final int QUENCH_SPREAD = 20;
    /** Prime -> Extinguished: 2.5-3.5 days (72000 ticks = three days). */
    public static final int PRIME_MIN = 60000;
    public static final int PRIME_SPREAD = 24000;

    private StarRock() {}

    static void schedule(Level level, BlockPos pos, Block block, int min, int spread) {
        level.scheduleTick(pos, block, min + level.random.nextInt(spread + 1));
    }

    /** Re-arms the stage timer if it went missing (random tick fallback). */
    static void ensureScheduled(ServerLevel level, BlockPos pos, Block block, int min, int spread) {
        if (!level.getBlockTicks().hasScheduledTick(pos, block)) {
            schedule(level, pos, block, min, spread);
        }
    }

    static boolean touchesWater(LevelReader level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** A client particle just outside a random open face of the block (like redstone ore's dust). */
    static void faceParticle(Level level, BlockPos pos, RandomSource random, ParticleOptions particle, double vx, double vy, double vz) {
        Direction d = Direction.getRandom(random);
        BlockPos side = pos.relative(d);
        if (level.getBlockState(side).isSolidRender(level, side)) {
            return;
        }
        Direction.Axis axis = d.getAxis();
        double x = axis == Direction.Axis.X ? 0.5 + 0.55 * d.getStepX() : random.nextDouble();
        double y = axis == Direction.Axis.Y ? 0.5 + 0.55 * d.getStepY() : random.nextDouble();
        double z = axis == Direction.Axis.Z ? 0.5 + 0.55 * d.getStepZ() : random.nextDouble();
        level.addParticle(particle, pos.getX() + x, pos.getY() + y, pos.getZ() + z, vx, vy, vz);
    }
}
