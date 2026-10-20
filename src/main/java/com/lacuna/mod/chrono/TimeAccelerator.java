package com.lacuna.mod.chrono;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.registry.ModParticles;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * The Chrono Hourglass's local tick speed-up (server side, called every tick while the sand runs): a local, bounded
 * version of the tick rate. Inside a box of {@link Balance#HOURGLASS_RADIUS} blocks around the hourglass and
 * {@link Balance#HOURGLASS_VERTICAL} above and below it, with {@code extra = HOURGLASS_SPEED - 1}:
 * <ol>
 *   <li>every ticking block entity (furnaces, smokers, brewing stands, hoppers, campfires...) runs its own ticker
 *   {@code extra} more times per tick: the nearest {@link Balance#HOURGLASS_MAX_BLOCK_ENTITIES}, looked up again every
 *   {@link Balance#HOURGLASS_SCAN_INTERVAL} ticks. Hourglasses, pistons, sculk sensors, spawners, portals and the rest of
 *   the {@code lacuna:chrono_hourglass_immune} block entity tag are never sped up, a block entity is sped up by one
 *   hourglass at most per tick, and a type whose ticker throws is left alone for the rest of the session;</li>
 *   <li>random ticks come {@code extra} times as often, for blocks and fluids alike, exactly as vanilla picks them
 *   (crops, saplings, grass and mycelium spreading, leaves decaying, ice and snow melting, cactus, sugar cane, bamboo,
 *   vines, kelp, copper weathering, budding amethyst, lava setting fire...): the box gets vanilla's {@code randomTickSpeed}
 *   density per 16x16x16 section, times {@code extra}, capped by {@link Balance#HOURGLASS_MAX_RANDOM_TICKS}. Weather
 *   ticks (water freezing, snow piling up, cauldrons filling in the rain) come {@code extra} times as often too, for
 *   columns whose surface is inside the box. Blocks in the {@code lacuna:chrono_hourglass_immune} block tag (portals,
 *   which spawn piglins on random ticks) are skipped;</li>
 *   <li>scheduled ticks come sooner: fluids flow faster and fire, falling blocks, frogspawn... too, never redstone
 *   ({@link ScheduledHaste});</li>
 *   <li>players heal and shake off harmful effects faster, young animals grow up, chickens lay eggs... faster
 *   ({@link LivingHaste}).</li>
 * </ol>
 */
public final class TimeAccelerator {
    public static final TagKey<BlockEntityType<?>> IMMUNE = TagKey.create(Registries.BLOCK_ENTITY_TYPE, Lacuna.id("chrono_hourglass_immune"));
    /** Blocks whose random and scheduled ticks are never hurried (portals, spawners, hourglasses, redstone parts). */
    public static final TagKey<Block> IMMUNE_BLOCKS = TagKey.create(Registries.BLOCK, Lacuna.id("chrono_hourglass_immune"));

    private static final Set<BlockEntityType<?>> BROKEN = ConcurrentHashMap.newKeySet();
    /** Block entities already sped up this game tick (by any hourglass): stacking hourglasses never goes past x4. */
    private static final LongOpenHashSet SPED_THIS_TICK = new LongOpenHashSet();
    private static ResourceKey<Level> stampLevel;
    private static long stampTime = Long.MIN_VALUE;

    private final List<BlockPos> tickers = new ArrayList<>();
    private long nextScan = Long.MIN_VALUE;
    private double randomTickCarry;
    private double weatherTickCarry;

    void invalidate() {
        nextScan = Long.MIN_VALUE;
    }

    void tick(ServerLevel level, BlockPos base) {
        int extra = Math.max(0, Balance.HOURGLASS_SPEED - 1);
        long now = level.getGameTime();
        if (stampTime != now || stampLevel != level.dimension()) {
            stampTime = now;
            stampLevel = level.dimension();
            SPED_THIS_TICK.clear();
            LivingHaste.newTick();
        }
        if (now >= nextScan) {
            scan(level, base);
            nextScan = now + Balance.HOURGLASS_SCAN_INTERVAL;
        }
        if (extra > 0) {
            tickBlockEntities(level, extra);
            randomTicks(level, base, extra);
            weatherTicks(level, base, extra);
        }
        ScheduledHaste.tick(level, base);
        AABB box = box(base);
        LivingHaste.players(level, box, now);
        if (extra > 0 && now % 20 == 0) {
            LivingHaste.mobs(level, box, extra * 20);
        }
        if (!tickers.isEmpty() && Math.floorMod(now + base.asLong(), 8L) == 0) {
            BlockPos p = tickers.get(level.getRandom().nextInt(tickers.size()));
            level.sendParticles(ModParticles.CHRONO_TICK.get(), p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 1, 0.25, 0.05, 0.25, 0.004);
        }
    }

    // ------------------------------------------------------------------ block entities

    static AABB box(BlockPos base) {
        int r = Balance.HOURGLASS_RADIUS;
        int v = Balance.HOURGLASS_VERTICAL;
        return new AABB(base.getX() - r, base.getY() - v, base.getZ() - r, base.getX() + r + 1, base.getY() + 2 + v, base.getZ() + r + 1);
    }

    static boolean inBox(BlockPos p, BlockPos base) {
        int r = Balance.HOURGLASS_RADIUS;
        int dy = p.getY() - base.getY();
        return Math.abs(p.getX() - base.getX()) <= r && Math.abs(p.getZ() - base.getZ()) <= r
                && dy >= -Balance.HOURGLASS_VERTICAL && dy <= 1 + Balance.HOURGLASS_VERTICAL;
    }

    private static boolean speedable(BlockEntityType<?> type) {
        return type != ModBlockEntities.CHRONO_HOURGLASS.get() && !BROKEN.contains(type)
                && !BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(type).is(IMMUNE);
    }

    private void scan(ServerLevel level, BlockPos base) {
        tickers.clear();
        int r = Balance.HOURGLASS_RADIUS;
        List<BlockPos> found = new ArrayList<>();
        for (int cx = (base.getX() - r) >> 4; cx <= (base.getX() + r) >> 4; cx++) {
            for (int cz = (base.getZ() - r) >> 4; cz <= (base.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (be.isRemoved() || !inBox(p, base) || !speedable(be.getType())) {
                        continue;
                    }
                    if (be.getBlockState().getTicker(level, be.getType()) != null) {
                        found.add(p.immutable());
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(base)));
        tickers.addAll(found.subList(0, Math.min(found.size(), Balance.HOURGLASS_MAX_BLOCK_ENTITIES)));
    }

    private void tickBlockEntities(ServerLevel level, int extra) {
        for (BlockPos pos : tickers) {
            if (!level.shouldTickBlocksAt(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || be.isRemoved() || !speedable(be.getType()) || !SPED_THIS_TICK.add(pos.asLong())) {
                continue;
            }
            try {
                tickExtra(level, pos, be, extra);
            } catch (Throwable t) {
                BROKEN.add(be.getType());
                Lacuna.LOGGER.warn("Chrono Hourglass: block entity {} at {} failed to tick faster; it will not be sped up again",
                        BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()), pos, t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickExtra(ServerLevel level, BlockPos pos, T be, int times) {
        BlockEntityType<T> type = (BlockEntityType<T>) be.getType();
        for (int i = 0; i < times; i++) {
            BlockState state = level.getBlockState(pos);
            if (be.isRemoved() || level.getBlockEntity(pos) != be || !type.isValid(state)) {
                return;
            }
            BlockEntityTicker<T> ticker = state.getTicker(level, type);
            if (ticker == null) {
                return;
            }
            ticker.tick(level, pos, state, be);
        }
    }

    // ------------------------------------------------------------------ random ticks

    private void randomTicks(ServerLevel level, BlockPos base, int extra) {
        int r = Balance.HOURGLASS_RADIUS;
        int v = Balance.HOURGLASS_VERTICAL;
        int side = 2 * r + 1;
        int height = 2 * v + 2;
        int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (speed <= 0) {
            return;
        }
        // vanilla: randomTickSpeed picks per 4096-block section per tick
        randomTickCarry += (double) side * side * height * speed * extra / 4096.0;
        int count = (int) randomTickCarry;
        randomTickCarry -= count;
        count = Math.min(count, Balance.HOURGLASS_MAX_RANDOM_TICKS);
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            p.set(base.getX() - r + random.nextInt(side), base.getY() - v + random.nextInt(height), base.getZ() - r + random.nextInt(side));
            if (p.getX() == base.getX() && p.getZ() == base.getZ() && (p.getY() == base.getY() || p.getY() == base.getY() + 1)) {
                continue;
            }
            if (level.isOutsideBuildHeight(p) || !level.shouldTickBlocksAt(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            // vanilla order: the block's random tick, then the random tick of the fluid it held when picked
            FluidState fluid = state.getFluidState();
            if (!state.isRandomlyTicking() && !fluid.isRandomlyTicking() || state.getBlock() instanceof ChronoHourglassBlock
                    || state.is(IMMUNE_BLOCKS)) {
                continue;
            }
            BlockPos at = p.immutable();
            if (state.isRandomlyTicking()) {
                state.randomTick(level, at, random);
                if (level.getBlockState(at) != state && (state.getBlock() instanceof BonemealableBlock || random.nextInt(4) == 0)) {
                    level.sendParticles(ModParticles.CHRONO_TICK.get(), at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 2, 0.25, 0.2, 0.25, 0.004);
                }
            }
            if (fluid.isRandomlyTicking()) {
                fluid.randomTick(level, at, random);
            }
        }
    }

    /** Vanilla: per chunk and tick, {@code randomTickSpeed} tries at 1 in 48 of a weather tick on a random column. */
    private void weatherTicks(ServerLevel level, BlockPos base, int extra) {
        int r = Balance.HOURGLASS_RADIUS;
        int v = Balance.HOURGLASS_VERTICAL;
        int side = 2 * r + 1;
        int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (speed <= 0) {
            return;
        }
        weatherTickCarry += (double) side * side * speed * extra / (48.0 * 256.0);
        int count = (int) weatherTickCarry;
        weatherTickCarry -= count;
        count = Math.min(count, 4);
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            int x = base.getX() - r + random.nextInt(side);
            int z = base.getZ() - r + random.nextInt(side);
            BlockPos column = new BlockPos(x, base.getY(), z);
            if (!level.shouldTickBlocksAt(column)) {
                continue;
            }
            int dy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1 - base.getY();
            if (dy >= -v && dy <= 1 + v) {
                level.tickPrecipitation(column);
            }
        }
    }
}
