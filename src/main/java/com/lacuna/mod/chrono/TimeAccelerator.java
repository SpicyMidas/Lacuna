package com.lacuna.mod.chrono;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.registry.ModParticles;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * The Chrono Hourglass's local tick speed-up (server side, called every tick while the sand runs). Inside a box of
 * {@link Balance#HOURGLASS_RADIUS} blocks around the hourglass and {@link Balance#HOURGLASS_VERTICAL} above and below it,
 * with {@code extra = HOURGLASS_SPEED - 1}:
 * <ol>
 *   <li>every ticking block entity (furnaces, smokers, brewing stands, hoppers, campfires...) runs its own ticker
 *   {@code extra} more times per tick: the nearest {@link Balance#HOURGLASS_MAX_BLOCK_ENTITIES}, looked up again every
 *   {@link Balance#HOURGLASS_SCAN_INTERVAL} ticks. Hourglasses and the {@code lacuna:chrono_hourglass_immune} block entity
 *   tag are never sped up, a block entity is sped up by one hourglass at most per tick, and a type whose ticker throws
 *   is left alone for the rest of the session;</li>
 *   <li>random ticks (crops, saplings, sugar cane...) come {@code extra} times as often: the box gets the same random tick
 *   density as vanilla's {@code randomTickSpeed} per 16x16x16 section, times {@code extra};</li>
 *   <li>young animals grow up (and breeding cooldowns run out) {@code extra} times faster. Players are never touched.</li>
 * </ol>
 */
public final class TimeAccelerator {
    public static final TagKey<BlockEntityType<?>> IMMUNE = TagKey.create(Registries.BLOCK_ENTITY_TYPE, Lacuna.id("chrono_hourglass_immune"));

    private static final Set<BlockEntityType<?>> BROKEN = ConcurrentHashMap.newKeySet();
    /** Block entities and mobs already sped up this game tick (by any hourglass): stacking hourglasses never goes past x4. */
    private static final LongOpenHashSet SPED_THIS_TICK = new LongOpenHashSet();
    private static final IntOpenHashSet AGED_THIS_TICK = new IntOpenHashSet();
    private static ResourceKey<Level> stampLevel;
    private static long stampTime = Long.MIN_VALUE;

    private final List<BlockPos> tickers = new ArrayList<>();
    private long nextScan = Long.MIN_VALUE;
    private double randomTickCarry;

    void invalidate() {
        nextScan = Long.MIN_VALUE;
    }

    void tick(ServerLevel level, BlockPos base) {
        int extra = Math.max(0, Balance.HOURGLASS_SPEED - 1);
        if (extra == 0) {
            return;
        }
        long now = level.getGameTime();
        if (stampTime != now || stampLevel != level.dimension()) {
            stampTime = now;
            stampLevel = level.dimension();
            SPED_THIS_TICK.clear();
            AGED_THIS_TICK.clear();
        }
        if (now >= nextScan) {
            scan(level, base);
            nextScan = now + Balance.HOURGLASS_SCAN_INTERVAL;
        }
        tickBlockEntities(level, extra);
        randomTicks(level, base, extra);
        if (now % 20 == 0) {
            ageMobs(level, base, extra * 20);
        }
        if (!tickers.isEmpty() && Math.floorMod(now + base.asLong(), 8L) == 0) {
            BlockPos p = tickers.get(level.getRandom().nextInt(tickers.size()));
            level.sendParticles(ModParticles.CHRONO_TICK.get(), p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 1, 0.25, 0.05, 0.25, 0.004);
        }
    }

    // ------------------------------------------------------------------ block entities

    private static boolean inBox(BlockPos p, BlockPos base) {
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
            if (!state.isRandomlyTicking() || state.getBlock() instanceof ChronoHourglassBlock) {
                continue;
            }
            BlockPos at = p.immutable();
            state.randomTick(level, at, random);
            if (state.getBlock() instanceof BonemealableBlock && level.getBlockState(at) != state) {
                level.sendParticles(ModParticles.CHRONO_TICK.get(), at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 2, 0.25, 0.2, 0.25, 0.004);
            }
        }
    }

    // ------------------------------------------------------------------ young animals

    private static void ageMobs(ServerLevel level, BlockPos base, int ticks) {
        int r = Balance.HOURGLASS_RADIUS;
        int v = Balance.HOURGLASS_VERTICAL;
        AABB box = new AABB(base.getX() - r, base.getY() - v, base.getZ() - r, base.getX() + r + 1, base.getY() + 2 + v, base.getZ() + r + 1);
        for (AgeableMob mob : level.getEntitiesOfClass(AgeableMob.class, box, AgeableMob::isAlive)) {
            int age = mob.getAge();
            if (age == 0 || !AGED_THIS_TICK.add(mob.getId())) {
                continue;
            }
            mob.setAge(age < 0 ? Math.min(0, age + ticks) : Math.max(0, age - ticks));
            if (age < 0 && level.getRandom().nextInt(3) == 0) {
                level.sendParticles(ModParticles.CHRONO_TICK.get(), mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(),
                        1, 0.2, 0.1, 0.2, 0.004);
            }
        }
    }
}
