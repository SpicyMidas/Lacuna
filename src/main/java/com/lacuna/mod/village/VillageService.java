package com.lacuna.mod.village;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.VillageBounds;
import com.lacuna.mod.logic.VillageLaws;
import com.lacuna.mod.util.LacunaAdvancements;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

/**
 * Village queries and reputation changes (all callers run inside SafeRun). A vanilla village (any structure in
 * {@code #minecraft:village}) gets its record lazily the first time a rule asks about a position whose loaded chunk
 * references the village and whose start chunk is loaded too: nothing is ever loaded or generated for a lookup.
 * Player positions are cached for one second.
 */
public final class VillageService {
    public static final String FEATURE = "villages";
    public static final String FRIEND_ADVANCEMENT = "lacuna/village_friend";
    private static final int CACHE_TICKS = 20;
    private static final Map<UUID, Cached> PLAYER_CACHE = new ConcurrentHashMap<>();
    private static volatile RegistryAccess structuresOwner;
    private static volatile Set<Structure> villageStructures = Set.of();

    private record Cached(long until, ResourceKey<Level> dimension, @Nullable String key) {}

    private VillageService() {}

    public static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.VILLAGES_ENABLED);
    }

    public static void clearVolatile() {
        PLAYER_CACHE.clear();
        structuresOwner = null;
        villageStructures = Set.of();
    }

    static void invalidateCaches() {
        PLAYER_CACHE.clear();
    }

    // ------------------------------------------------------------------------------------------ lookups

    /** The village at {@code pos}: an existing record, else the lazily created record of a vanilla village there. */
    public static Optional<VillageRecord> at(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return Optional.empty();
        }
        VillageSavedData data = VillageSavedData.get(level.getServer());
        Optional<VillageRecord> found = data.at(level.dimension(), pos);
        return found.isPresent() ? found : fromReferences(level, data, pos);
    }

    /** {@link #at} for a civic block a player uses: tells the player when the block is not part of any village. */
    public static Optional<VillageRecord> atOrTell(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<VillageRecord> found = at(level, pos);
        if (found.isEmpty() && enabled()) {
            player.displayClientMessage(Component.translatable("lacuna.village.none").withStyle(ChatFormatting.GRAY), true);
        }
        return found;
    }

    /** {@link #at} for a player's position, cached for one second per player. */
    public static Optional<VillageRecord> atPlayer(ServerPlayer player) {
        if (!enabled()) {
            return Optional.empty();
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Cached cached = PLAYER_CACHE.get(player.getUUID());
        if (cached != null && cached.until() > now && cached.dimension() == level.dimension()) {
            return cached.key() == null ? Optional.empty() : VillageSavedData.get(level.getServer()).byKey(cached.key());
        }
        Optional<VillageRecord> found = at(level, player.blockPosition());
        PLAYER_CACHE.put(player.getUUID(), new Cached(now + CACHE_TICKS, level.dimension(), found.map(r -> r.key).orElse(null)));
        return found;
    }

    public static void forget(UUID player) {
        PLAYER_CACHE.remove(player);
    }

    /** Structures of the vanilla tag {@code #minecraft:village} (recomputed when the registries change). */
    public static Set<Structure> villageStructures(ServerLevel level) {
        RegistryAccess access = level.registryAccess();
        if (structuresOwner != access) {
            Set<Structure> set = new HashSet<>();
            access.registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE).ifPresent(tag -> tag.forEach(h -> set.add(h.value())));
            villageStructures = Set.copyOf(set);
            structuresOwner = access;
        }
        return villageStructures;
    }

    /** Creates (once) the record of a vanilla village whose structure references the loaded chunk at {@code pos}. */
    private static Optional<VillageRecord> fromReferences(ServerLevel level, VillageSavedData data, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) {
            return Optional.empty();
        }
        Map<Structure, LongSet> refs = chunk.getAllReferences();
        if (refs.isEmpty()) {
            return Optional.empty();
        }
        Set<Structure> villages = villageStructures(level);
        for (Map.Entry<Structure, LongSet> entry : refs.entrySet()) {
            if (!villages.contains(entry.getKey())) {
                continue;
            }
            for (long start : entry.getValue()) {
                VillageRecord record = data.byKey(VillageRecord.vanillaKey(level.dimension(), start)).orElse(null);
                if (record == null) {
                    LevelChunk startChunk = level.getChunkSource().getChunkNow(ChunkPos.getX(start), ChunkPos.getZ(start));
                    StructureStart structureStart = startChunk == null ? null : startChunk.getStartForStructure(entry.getKey());
                    if (structureStart == null || !structureStart.isValid()) {
                        continue;
                    }
                    record = register(level, structureStart);
                }
                if (record.contains(pos)) {
                    return Optional.of(record);
                }
            }
        }
        return Optional.empty();
    }

    /** Does a vanilla village structure reference the loaded chunk at {@code pos} (whether or not its record exists)? */
    static boolean inVillageChunk(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) {
            return false;
        }
        Set<Structure> villages = villageStructures(level);
        for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
            if (villages.contains(entry.getKey()) && !entry.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The record of a vanilla village structure start (created on first use, idempotent). */
    public static VillageRecord register(ServerLevel level, StructureStart start) {
        VillageSavedData data = VillageSavedData.get(level.getServer());
        long startChunk = start.getChunkPos().toLong();
        String key = VillageRecord.vanillaKey(level.dimension(), startChunk);
        Optional<VillageRecord> existing = data.byKey(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        BoundingBox box = start.getBoundingBox();
        BlockPos center = new BlockPos(VillageBounds.center(box.minX(), box.maxX()), VillageBounds.center(box.minY(), box.maxY()),
                VillageBounds.center(box.minZ(), box.maxZ()));
        int radius = VillageBounds.radius(box.minX(), box.minZ(), box.maxX(), box.maxZ());
        VillageRecord created = new VillageRecord(key, VillageRecord.Kind.VANILLA, typeOf(level, start.getStructure()),
                level.dimension(), center, radius, box.minY() - VillageBounds.BELOW, box.maxY() + VillageBounds.ABOVE,
                VillageLaws.villageSeed(level.getSeed(), startChunk), null);
        if (!start.getPieces().isEmpty()) {
            // the first piece of a jigsaw village is its town centre (fountain / meeting point with the bell)
            BoundingBox plaza = start.getPieces().get(0).getBoundingBox();
            created.plaza = new BlockPos(VillageBounds.center(plaza.minX(), plaza.maxX()), plaza.minY() + 1,
                    VillageBounds.center(plaza.minZ(), plaza.maxZ()));
        }
        VillageRecord record = data.add(created);
        invalidateCaches();
        return record;
    }

    /** The structure start of a vanilla village record, if its start chunk is loaded. */
    public static Optional<StructureStart> structureStart(ServerLevel level, VillageRecord record) {
        if (record.kind != VillageRecord.Kind.VANILLA || !record.dimension.equals(level.dimension())) {
            return Optional.empty();
        }
        long startChunk;
        try {
            startChunk = Long.parseLong(record.key.substring(record.key.lastIndexOf(':') + 1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(startChunk), ChunkPos.getZ(startChunk));
        if (chunk == null) {
            return Optional.empty();
        }
        Set<Structure> villages = villageStructures(level);
        for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
            if (villages.contains(e.getKey()) && e.getValue().isValid()) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    /** "village_plains" -> "plains"; other mods' villages keep their path. */
    static String typeOf(ServerLevel level, Structure structure) {
        var key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        if (key == null) {
            return "other";
        }
        String path = key.getPath();
        return path.startsWith("village_") ? path.substring("village_".length()) : path;
    }

    /**
     * A ledger outside every village founds a player village (radius 48) around itself; inside a village it simply
     * belongs to that village. Returns the village the ledger now belongs to.
     */
    public static Optional<VillageRecord> foundPlayerVillage(ServerLevel level, BlockPos ledger, @Nullable ServerPlayer founder) {
        if (!enabled()) {
            return Optional.empty();
        }
        Optional<VillageRecord> existing = at(level, ledger);
        if (existing.isPresent() || inVillageChunk(level, ledger)) {
            // inside a vanilla village whose centre is not loaded yet: never found a second village on top of it
            return existing;
        }
        VillageSavedData data = VillageSavedData.get(level.getServer());
        String key = VillageRecord.playerKey(level.dimension(), ledger);
        VillageRecord record = data.add(new VillageRecord(key, VillageRecord.Kind.PLAYER, "player", level.dimension(), ledger,
                VillageBounds.PLAYER_RADIUS, ledger.getY() - VillageBounds.BELOW - 8, ledger.getY() + VillageBounds.ABOVE + 8,
                VillageLaws.villageSeed(level.getSeed(), ledger.asLong()), founder == null ? null : founder.getUUID()));
        invalidateCaches();
        return Optional.of(record);
    }

    /** Breaking the ledger of a player village dissolves it. */
    public static boolean dissolvePlayerVillage(ServerLevel level, BlockPos ledger) {
        boolean removed = VillageSavedData.get(level.getServer()).remove(VillageRecord.playerKey(level.dimension(), ledger));
        if (removed) {
            invalidateCaches();
        }
        return removed;
    }

    // ------------------------------------------------------------------------------------------ rules

    public static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    public static ReputationMath.Tier tier(VillageRecord t, UUID player) {
        return ReputationMath.tier(t.reputation(player));
    }

    public static boolean isStranger(VillageRecord t, UUID player) {
        return VillageLaws.isStranger(t.reputation(player)) && !t.isFounder(player);
    }

    /** Gain multiplier of the current policy (Festival doubles gains, M11c). */
    public static double gainMultiplier(VillageRecord t) {
        return "festival".equals(t.policy) ? 2.0 : 1.0;
    }

    /** Villager price factor for a player: reputation x village price index x policy (M11c). */
    public static double priceFactor(VillageRecord t, UUID player) {
        double policy = switch (t.policy) {
            case "free_market" -> 0.90;
            case "austerity" -> 1.10;
            default -> 1.0;
        };
        return ReputationMath.priceMultiplier(t.reputation(player)) * t.priceIndex * policy;
    }

    /** Applies {@code count} times a change, honouring daily caps; returns the applied delta. */
    public static int change(ServerLevel level, VillageRecord t, ServerPlayer player, ReputationMath.Change change, int count) {
        if (player.isCreative() || player.isSpectator()) {
            return 0;
        }
        VillageRecord.Daily daily = t.daily(player.getUUID(), day(level));
        int used = daily.used.getOrDefault(change, 0);
        int allowed = ReputationMath.allowed(change.amount * count, used, change.dailyCap);
        if (allowed == 0) {
            return 0;
        }
        int before = t.reputation(player.getUUID());
        int after = ReputationMath.apply(before, allowed, gainMultiplier(t));
        daily.used.put(change, used + Math.abs(allowed));
        t.setReputation(player.getUUID(), after);
        VillageSavedData.get(level.getServer()).touch();
        if (after != before) {
            ChatFormatting color = after > before ? ChatFormatting.GREEN : ChatFormatting.RED;
            player.displayClientMessage(Component.translatable("lacuna.village.rep_change", signed(after - before), after,
                    tierName(after)).withStyle(color), true);
        }
        awardIfFriendly(player, after);
        return after - before;
    }

    public static void set(ServerLevel level, VillageRecord t, UUID player, int value) {
        t.setReputation(player, value);
        VillageSavedData.get(level.getServer()).touch();
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
        if (online != null) {
            awardIfFriendly(online, t.reputation(player));
        }
    }

    private static void awardIfFriendly(ServerPlayer player, int reputation) {
        if (ReputationMath.tier(reputation).ordinal() >= ReputationMath.Tier.FRIENDLY.ordinal()) {
            LacunaAdvancements.award(player, FRIEND_ADVANCEMENT);
        }
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    public static MutableComponent tierName(int rep) {
        return Component.translatable("lacuna.village.tier." + ReputationMath.tier(rep).name().toLowerCase(java.util.Locale.ROOT));
    }

    public static MutableComponent villageName(VillageRecord t) {
        return Component.translatableWithFallback("lacuna.village.type." + t.type, "Village (" + t.type + ")")
                .append(Component.literal(" " + t.shortId()).withStyle(ChatFormatting.GRAY));
    }

    /** Chat summary shown by the ledger and {@code /lacuna village}. */
    public static void describe(ServerLevel level, VillageRecord t, ServerPlayer player) {
        int rep = t.reputation(player.getUUID());
        long today = day(level);
        player.sendSystemMessage(Component.translatable("lacuna.village.header", villageName(t)).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("lacuna.village.reputation", rep, tierName(rep)));
        player.sendSystemMessage(Component.translatable("lacuna.village.laws",
                Component.translatable("lacuna.village.law." + t.laws[0]), Component.translatable("lacuna.village.law." + t.laws[1])));
        long council = t.nextCouncilDay < 0 ? 0 : Math.max(0, t.nextCouncilDay - today);
        player.sendSystemMessage(Component.translatable("lacuna.village.policy",
                Component.translatable("lacuna.village.policy." + t.policy), council));
        player.sendSystemMessage(Component.translatable("lacuna.village.coin", String.format(java.util.Locale.ROOT, "%.2f", t.coinValue),
                String.format(java.util.Locale.ROOT, "%.2f", t.priceIndex)));
    }
}
