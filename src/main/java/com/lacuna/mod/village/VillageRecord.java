package com.lacuna.mod.village;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.VillageBounds;
import com.lacuna.mod.logic.VillageLaws;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One village record (spec 18.2, revision 2). A record exists for every vanilla village structure start a rule has
 * touched (keyed by dimension + start chunk) and for every player-founded village (keyed by dimension + ledger
 * position). It holds the laws, the economy and the per-player reputation with daily caps.
 */
public final class VillageRecord {
    public enum Kind { VANILLA, PLAYER }

    public final String key;
    public final UUID id;
    public final Kind kind;
    /** Village style: plains, desert, savanna, taiga, snowy (or another mod's village path), "player" for founded ones. */
    public final String type;
    public final ResourceKey<Level> dimension;
    public final BlockPos center;
    public final int radius;
    public final int minY;
    public final int maxY;
    public final long seed;
    public final int[] laws;
    @Nullable
    public final UUID founder;
    /** Where festivals happen: the village's town centre piece (vanilla) or the ledger (player villages). */
    public BlockPos plaza;
    public double coinValue = 1.0;
    public double priceIndex = 1.0;
    public String policy = "none";
    public long policyEndDay;
    public long lastDailyDay = -1;
    public long nextCouncilDay = -1;
    public int oreDelivered;
    public double weekRepSum;
    public int weekRepSamples;
    final Map<UUID, Integer> reputation = new HashMap<>();
    final Map<UUID, Daily> daily = new HashMap<>();
    final Map<UUID, Long> lastPardonDay = new HashMap<>();

    static final class Daily {
        long day;
        final EnumMap<ReputationMath.Change, Integer> used = new EnumMap<>(ReputationMath.Change.class);
        int bellRings;
    }

    VillageRecord(String key, Kind kind, String type, ResourceKey<Level> dimension, BlockPos center, int radius, int minY, int maxY,
                  long seed, @Nullable UUID founder) {
        this.key = key;
        this.id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        this.kind = kind;
        this.type = type;
        this.dimension = dimension;
        this.center = center.immutable();
        this.radius = radius;
        this.minY = minY;
        this.maxY = maxY;
        this.seed = seed;
        this.founder = founder;
        this.plaza = this.center;
        this.laws = VillageLaws.choose(seed);
    }

    /** Key of a vanilla village: dimension + structure start chunk. */
    public static String vanillaKey(ResourceKey<Level> dimension, long startChunk) {
        return dimension.location() + "|start:" + startChunk;
    }

    /** Key of a player-founded village: dimension + ledger position. */
    public static String playerKey(ResourceKey<Level> dimension, BlockPos ledger) {
        return dimension.location() + "|ledger:" + ledger.asLong();
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public boolean hasLaw(int law) {
        return laws[0] == law || laws[1] == law;
    }

    public boolean isFounder(UUID player) {
        return founder != null && founder.equals(player);
    }

    public int reputation(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public Map<UUID, Integer> reputations() {
        return Collections.unmodifiableMap(reputation);
    }

    void setReputation(UUID player, int value) {
        reputation.put(player, ReputationMath.clamp(value));
    }

    Daily daily(UUID player, long day) {
        Daily d = daily.computeIfAbsent(player, k -> new Daily());
        if (d.day != day) {
            d.day = day;
            d.used.clear();
            d.bellRings = 0;
        }
        return d;
    }

    public long lastPardonDay(UUID player) {
        return lastPardonDay.getOrDefault(player, Long.MIN_VALUE / 2);
    }

    void setLastPardonDay(UUID player, long day) {
        lastPardonDay.put(player, day);
    }

    /** Inside the village: within the radius around the centre and inside the vertical band. */
    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        return dimension.equals(dim) && contains(pos);
    }

    public boolean contains(BlockPos pos) {
        return VillageBounds.contains(center.getX(), center.getZ(), radius, minY, maxY, pos.getX(), pos.getY(), pos.getZ());
    }

    public double horizontalDistance(BlockPos pos) {
        double dx = pos.getX() - center.getX();
        double dz = pos.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putString("kind", kind.name());
        tag.putString("type", type);
        tag.putString("dimension", dimension.location().toString());
        tag.putLong("center", center.asLong());
        tag.putInt("radius", radius);
        tag.putInt("min_y", minY);
        tag.putInt("max_y", maxY);
        tag.putLong("seed", seed);
        if (founder != null) {
            tag.putUUID("founder", founder);
        }
        tag.putLong("plaza", plaza.asLong());
        tag.putDouble("coin_value", coinValue);
        tag.putDouble("price_index", priceIndex);
        tag.putString("policy", policy);
        tag.putLong("policy_end_day", policyEndDay);
        tag.putLong("last_daily_day", lastDailyDay);
        tag.putLong("next_council_day", nextCouncilDay);
        tag.putInt("ore_delivered", oreDelivered);
        tag.putDouble("week_rep_sum", weekRepSum);
        tag.putInt("week_rep_samples", weekRepSamples);
        CompoundTag rep = new CompoundTag();
        reputation.forEach((k, v) -> rep.putInt(k.toString(), v));
        tag.put("reputation", rep);
        CompoundTag pardons = new CompoundTag();
        lastPardonDay.forEach((k, v) -> pardons.putLong(k.toString(), v));
        tag.put("pardons", pardons);
        CompoundTag days = new CompoundTag();
        daily.forEach((k, d) -> {
            CompoundTag dt = new CompoundTag();
            dt.putLong("day", d.day);
            dt.putInt("bell", d.bellRings);
            d.used.forEach((c, v) -> dt.putInt(c.name(), v));
            days.put(k.toString(), dt);
        });
        tag.put("daily", days);
        return tag;
    }

    static Optional<VillageRecord> load(CompoundTag tag) {
        String key = tag.getString("key");
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dimension"));
        if (key.isEmpty() || dim == null) {
            return Optional.empty();
        }
        Kind kind = "PLAYER".equals(tag.getString("kind")) ? Kind.PLAYER : Kind.VANILLA;
        VillageRecord t = new VillageRecord(key, kind, tag.getString("type"), ResourceKey.create(Registries.DIMENSION, dim),
                BlockPos.of(tag.getLong("center")), VillageBounds.clampRadius(tag.getInt("radius")), tag.getInt("min_y"),
                tag.getInt("max_y"), tag.getLong("seed"), tag.hasUUID("founder") ? tag.getUUID("founder") : null);
        if (tag.contains("plaza")) {
            t.plaza = BlockPos.of(tag.getLong("plaza"));
        }
        t.coinValue = tag.contains("coin_value") ? tag.getDouble("coin_value") : 1.0;
        t.priceIndex = tag.contains("price_index") ? tag.getDouble("price_index") : 1.0;
        t.policy = tag.getString("policy").isEmpty() ? "none" : tag.getString("policy");
        t.policyEndDay = tag.getLong("policy_end_day");
        t.lastDailyDay = tag.getLong("last_daily_day");
        t.nextCouncilDay = tag.getLong("next_council_day");
        t.oreDelivered = tag.getInt("ore_delivered");
        t.weekRepSum = tag.getDouble("week_rep_sum");
        t.weekRepSamples = tag.getInt("week_rep_samples");
        CompoundTag rep = tag.getCompound("reputation");
        for (String k : rep.getAllKeys()) {
            parse(k).ifPresent(u -> t.reputation.put(u, ReputationMath.clamp(rep.getInt(k))));
        }
        CompoundTag pardons = tag.getCompound("pardons");
        for (String k : pardons.getAllKeys()) {
            parse(k).ifPresent(u -> t.lastPardonDay.put(u, pardons.getLong(k)));
        }
        CompoundTag days = tag.getCompound("daily");
        for (String k : days.getAllKeys()) {
            parse(k).ifPresent(u -> {
                CompoundTag dt = days.getCompound(k);
                Daily d = new Daily();
                d.day = dt.getLong("day");
                d.bellRings = dt.getInt("bell");
                for (ReputationMath.Change c : ReputationMath.Change.values()) {
                    if (dt.contains(c.name())) {
                        d.used.put(c, dt.getInt(c.name()));
                    }
                }
                t.daily.put(u, d);
            });
        }
        return Optional.of(t);
    }

    private static Optional<UUID> parse(String s) {
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
