package com.lacuna.mod.star;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.util.LacunaAdvancements;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Falling stars on their own: on a clear night in the overworld a star now and then streaks down near a player and
 * leaves a molten Star Rock ({@link StarRock}) to find, the same star and impact the Starcaller Trumpet calls (crater and fire only
 * with mobGriefing).
 * <ul>
 *   <li><b>When</b>: during the dark of the night ({@link Starfall#NIGHT_FROM}..{@link Starfall#NIGHT_TO}), with no
 *       rain, never on the world's first night. Every {@link Starfall#NATURAL_CHECK_INTERVAL} ticks each player is
 *       rolled so that, over a whole clear night, a star falls for them with the configured chance
 *       ({@code starfall.naturalChancePerNight}); at most one per player per night, and stars are spaced out in the
 *       world.</li>
 *   <li><b>For whom</b>: a player at or near the surface (inside a house counts, down a mine does not).</li>
 *   <li><b>Where</b>: {@link Starfall#NATURAL_MIN_DISTANCE}-{@link Starfall#NATURAL_MAX_DISTANCE} blocks from them, on
 *       natural open ground ({@code #lacuna:fallen_star_ground}) under the open sky, in a simulated chunk, never in a
 *       village or a structure, and away from beds, workstations and block entities (a base).</li>
 *   <li><b>Finding it</b>: players around are told which way it fell (action bar); the star itself twinkles, whistles
 *       as it falls, the molten Star Rock smokes and sizzles, and once cooled it chimes softly.</li>
 * </ul>
 */
public final class NaturalStarfall {
    /** Natural ground a star may rest on (dirt, sand, stone, snow...). */
    public static final TagKey<Block> GROUND = TagKey.create(Registries.BLOCK, Lacuna.id("fallen_star_ground"));
    private static final String[] COMPASS = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};

    /** The night (day index) each player last had a star fall for them. */
    private static final Map<UUID, Long> LAST_NIGHT = new HashMap<>();
    private static long lastStar = Long.MIN_VALUE;

    private NaturalStarfall() {}

    public static void register(IEventBus bus) {
        bus.addListener(NaturalStarfall::onLevelTick);
        bus.addListener(NaturalStarfall::onServerStopped);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        LAST_NIGHT.clear();
        lastStar = Long.MIN_VALUE;
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % Starfall.NATURAL_CHECK_INTERVAL != 0) {
            return;
        }
        if (!LacunaConfig.get(LacunaConfig.NATURAL_STARS) || SafeRun.isDisabled(Starfall.NATURAL_FEATURE)) {
            return;
        }
        SafeRun.run(Starfall.NATURAL_FEATURE, () -> roll(level));
    }

    /** A clear, dark night in a world older than its first day. */
    public static boolean clearNight(ServerLevel level) {
        if (level.getGameTime() < Starfall.NATURAL_GRACE || !level.dimensionType().hasSkyLight() || level.isRaining()
                || level.isThundering()) {
            return false;
        }
        long time = Math.floorMod(level.getDayTime(), 24000L);
        return time >= Starfall.NIGHT_FROM && time < Starfall.NIGHT_TO;
    }

    private static void roll(ServerLevel level) {
        double chance = Mth.clamp(LacunaConfig.get(LacunaConfig.NATURAL_STAR_CHANCE), 0.0, 1.0);
        if (chance <= 0.0 || !clearNight(level) || level.getGameTime() - lastStar < Starfall.NATURAL_SPACING) {
            return;
        }
        // per-check chance so that the whole night adds up to the configured chance
        double checks = (Starfall.NIGHT_TO - Starfall.NIGHT_FROM) / (double) Starfall.NATURAL_CHECK_INTERVAL;
        double perCheck = chance >= 1.0 ? 1.0 : 1.0 - Math.pow(1.0 - chance, 1.0 / checks);
        long night = Math.floorDiv(level.getDayTime(), 24000L);
        List<ServerPlayer> players = new ArrayList<>(level.players());
        RandomSource r = level.getRandom();
        for (int i = players.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            ServerPlayer t = players.get(i);
            players.set(i, players.get(j));
            players.set(j, t);
        }
        for (ServerPlayer player : players) {
            if (player.isSpectator() || !player.isAlive() || Long.valueOf(night).equals(LAST_NIGHT.get(player.getUUID()))
                    || r.nextDouble() >= perCheck || !nearSurface(level, player)) {
                continue;
            }
            if (fallNear(level, player)) {
                LAST_NIGHT.put(player.getUUID(), night);
                return;
            }
        }
    }

    /** At most {@link Starfall#NATURAL_SURFACE_DEPTH} blocks under the surface above the player. */
    private static boolean nearSurface(ServerLevel level, ServerPlayer player) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
        return player.getY() >= top - Starfall.NATURAL_SURFACE_DEPTH;
    }

    /**
     * Drops a star somewhere near {@code player} (a fitting spot and an open way down from the sky permitting).
     * Returns true when a star is on its way. Public so a debug command can call one.
     */
    public static boolean fallNear(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.getRandom();
        for (int attempt = 0; attempt < Starfall.NATURAL_ATTEMPTS; attempt++) {
            BlockPos spot = landing(level, player, r);
            if (spot == null) {
                continue;
            }
            Vec3 point = Vec3.atBottomCenterOf(spot).add(0.0, 0.05, 0.0);
            Vec3 start = FallingStarEntity.skyStart(level, point, r);
            if (start == null) {
                continue;
            }
            FallingStarEntity.fallFromSky(level, start, point);
            lastStar = level.getGameTime();
            hint(level, point);
            return true;
        }
        return false;
    }

    /** A candidate spot (the air block the star rests in) near the player, or null when this try does not fit. */
    @Nullable
    private static BlockPos landing(ServerLevel level, ServerPlayer player, RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2.0;
        double d = Mth.lerp(r.nextDouble(), Starfall.NATURAL_MIN_DISTANCE, Starfall.NATURAL_MAX_DISTANCE);
        int x = Mth.floor(player.getX() + Math.cos(a) * d);
        int z = Mth.floor(player.getZ() + Math.sin(a) * d);
        BlockPos column = new BlockPos(x, player.getBlockY(), z);
        if (!level.isPositionEntityTicking(column)) {
            return null; // also covers chunks that are not loaded
        }
        BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        BlockPos below = at.below();
        BlockState ground = level.getBlockState(below);
        if (at.getY() <= level.getMinBuildHeight() || !ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)
                || !ground.is(GROUND) || !Block.canSupportCenter(level, below, Direction.UP)) {
            return null;
        }
        BlockState here = level.getBlockState(at);
        if (!here.canBeReplaced() || !here.getFluidState().isEmpty() || !level.canSeeSky(at)) {
            return null;
        }
        if (level.hasNearbyAlivePlayer(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Starfall.NATURAL_MIN_DISTANCE)) {
            return null; // never on (or right next to) anyone
        }
        if (level.isVillage(at) || level.structureManager().hasAnyStructureAt(at) || nearBase(level, at)) {
            return null;
        }
        return at;
    }

    /** Beds, workstations, bells, hives... or chests, furnaces, signs nearby: somebody lives here. */
    private static boolean nearBase(ServerLevel level, BlockPos at) {
        if (level.getPoiManager().getInRange(p -> true, at, Starfall.NATURAL_CLEARANCE, PoiManager.Occupancy.ANY).findAny().isPresent()) {
            return true;
        }
        int reach = Starfall.NATURAL_BLOCK_ENTITY_CLEARANCE;
        ChunkPos min = new ChunkPos(at.offset(-reach, 0, -reach));
        ChunkPos max = new ChunkPos(at.offset(reach, 0, reach));
        double reachSqr = (double) reach * reach;
        for (int cx = min.x; cx <= max.x; cx++) {
            for (int cz = min.z; cz <= max.z; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockPos p : level.getChunk(cx, cz).getBlockEntitiesPos()) {
                    if (p.distSqr(at) <= reachSqr) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Tells the players around which way the star fell, and marks the sight for them. */
    private static void hint(ServerLevel level, Vec3 point) {
        double range = Starfall.NATURAL_HINT_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(point.x, p.getY(), point.z) > range * range) {
                continue;
            }
            p.displayClientMessage(Component.translatable("lacuna.starfall.natural",
                    Component.translatable("lacuna.starfall.dir." + compass(p.position(), point))), true);
            LacunaAdvancements.award(p, Starfall.NATURAL_ADVANCEMENT);
        }
    }

    /** One of eight compass points from {@code from} toward {@code to} (north is -Z). */
    static String compass(Vec3 from, Vec3 to) {
        double yaw = Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z)); // 0 = south, 90 = west (vanilla yaw)
        int i = Math.floorMod((int) Math.round(yaw / 45.0), 8);
        return COMPASS[i];
    }
}
