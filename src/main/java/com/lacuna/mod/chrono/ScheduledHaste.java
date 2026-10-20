package com.lacuna.mod.chrono;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModParticles;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickContainerAccess;

/**
 * Scheduled ticks inside a running Chrono Hourglass's reach come sooner: water and lava spread
 * {@link Balance#HOURGLASS_FLUID_SPEED} times faster, and other scheduled block ticks (fire, falling sand and concrete
 * powder, frogspawn and sniffer eggs hatching, coral drying out...) {@link Balance#HOURGLASS_SCHEDULED_SPEED} times
 * faster.
 * <p>
 * No mixin: every tick the chunk tick queues around the hourglass ({@link LevelChunkTicks}, the same containers the
 * level drains) are searched for ticks in reach that are still at least two ticks away. Each one is taken out and put
 * back with its remaining delay divided by the speed (randomly rounded, so the average is exact), keeping its priority
 * and order; the level then runs it at the new time as usual. A tick moved once is remembered and never moved again, so
 * overlapping hourglasses do not stack.
 * <p>
 * Redstone is never hurried, so circuits keep their timing: signal sources (repeaters, comparators, observers, torches,
 * buttons, pressure plates, tripwire hooks, sculk sensors...), lamps, dispensers, droppers, crafters, command blocks,
 * tripwire and big dripleaves, plus the {@code lacuna:chrono_hourglass_immune} block tag (portals, spawners, hourglasses).
 */
final class ScheduledHaste {
    /** Ticks this hourglass code put back sooner (by identity), per level: never moved a second time. */
    private static final Map<ServerLevel, Set<ScheduledTick<?>>> HASTENED = new WeakHashMap<>();
    private static final int MAX_REMEMBERED = 16384;

    private ScheduledHaste() {
    }

    static void tick(ServerLevel level, BlockPos base) {
        if (Balance.HOURGLASS_FLUID_SPEED <= 1 && Balance.HOURGLASS_SCHEDULED_SPEED <= 1) {
            return;
        }
        long now = level.getGameTime();
        Set<ScheduledTick<?>> hastened = HASTENED.computeIfAbsent(level, l -> new ReferenceOpenHashSet<>());
        if (now % 20 == 0 || hastened.size() > MAX_REMEMBERED) {
            hastened.removeIf(t -> t.triggerTick() < now);
        }
        int budget = Balance.HOURGLASS_MAX_SCHEDULED_TICKS;
        int r = Balance.HOURGLASS_RADIUS;
        for (int cx = (base.getX() - r) >> 4; cx <= (base.getX() + r) >> 4 && budget > 0; cx++) {
            for (int cz = (base.getZ() - r) >> 4; cz <= (base.getZ() + r) >> 4 && budget > 0; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null || !level.shouldTickBlocksAt(ChunkPos.asLong(cx, cz))) {
                    continue;
                }
                budget -= hasten(level, base, chunk.getFluidTicks(), Balance.HOURGLASS_FLUID_SPEED, false, hastened, now, budget);
                budget -= hasten(level, base, chunk.getBlockTicks(), Balance.HOURGLASS_SCHEDULED_SPEED, true, hastened, now, budget);
            }
        }
    }

    private static <T> int hasten(ServerLevel level, BlockPos base, TickContainerAccess<T> access, int speed, boolean blocks,
                                  Set<ScheduledTick<?>> hastened, long now, int budget) {
        if (speed <= 1 || budget <= 0 || !(access instanceof LevelChunkTicks<T> ticks)) {
            return 0;
        }
        int queued = ticks.count();
        if (queued == 0 || queued > Balance.HOURGLASS_MAX_TICK_QUEUE) {
            return 0;
        }
        List<ScheduledTick<T>> picked = null;
        for (Iterator<ScheduledTick<T>> it = ticks.getAll().iterator(); it.hasNext(); ) {
            ScheduledTick<T> tick = it.next();
            if (tick.triggerTick() - now < 2 || !TimeAccelerator.inBox(tick.pos(), base) || hastened.contains(tick)
                    || blocks && !hastenable(level, (Block) tick.type(), tick.pos())) {
                continue;
            }
            if (picked == null) {
                picked = new ArrayList<>();
            }
            picked.add(tick);
            if (picked.size() >= budget) {
                break;
            }
        }
        if (picked == null) {
            return 0;
        }
        Set<ScheduledTick<T>> moving = new ReferenceOpenHashSet<>(picked);
        ticks.removeIf(moving::contains);
        RandomSource random = level.getRandom();
        for (ScheduledTick<T> tick : picked) {
            double left = (double) (tick.triggerTick() - now) / speed;
            long soon = Math.max(1L, (long) left + (random.nextDouble() < left - Math.floor(left) ? 1L : 0L));
            ScheduledTick<T> sooner = new ScheduledTick<>(tick.type(), tick.pos(), now + soon, tick.priority(), tick.subTickOrder());
            hastened.add(sooner);
            ticks.schedule(sooner);
        }
        if (random.nextInt(blocks ? 6 : 14) == 0) {
            BlockPos p = picked.get(random.nextInt(picked.size())).pos();
            level.sendParticles(ModParticles.CHRONO_TICK.get(), p.getX() + 0.5, p.getY() + (blocks ? 0.9 : 0.75), p.getZ() + 0.5,
                    1, 0.25, 0.05, 0.25, 0.004);
        }
        return picked.size();
    }

    /** Scheduled block ticks that keep their timing: redstone (so circuits never break), portals, spawners, hourglasses. */
    private static boolean hastenable(ServerLevel level, Block block, BlockPos pos) {
        if (block instanceof ChronoHourglassBlock || block instanceof DispenserBlock || block instanceof CrafterBlock
                || block instanceof RedstoneLampBlock || block instanceof CommandBlock || block instanceof TripWireBlock
                || block instanceof BigDripleafBlock) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(block)) {
            state = block.defaultBlockState();
        }
        return !state.isSignalSource() && !block.defaultBlockState().isSignalSource() && !state.is(TimeAccelerator.IMMUNE_BLOCKS);
    }
}
