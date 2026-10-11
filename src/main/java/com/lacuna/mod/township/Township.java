package com.lacuna.mod.township;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.TownshipLaws;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** One township record (spec 18.2): identity, laws, economy and per-player reputation with daily caps. */
public final class Township {
    public final UUID id;
    public final ResourceKey<Level> dimension;
    public final BlockPos center;
    public final int radius;
    public final long seed;
    public final int[] laws;
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

    Township(UUID id, ResourceKey<Level> dimension, BlockPos center, int radius, long seed) {
        this.id = id;
        this.dimension = dimension;
        this.center = center.immutable();
        this.radius = radius;
        this.seed = seed;
        this.laws = TownshipLaws.choose(seed);
    }

    public boolean hasLaw(int law) {
        return laws[0] == law || laws[1] == law;
    }

    public int reputation(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public Map<UUID, Integer> reputations() {
        return java.util.Collections.unmodifiableMap(reputation);
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

    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        return dimension.equals(dim) && horizontalDistance(pos) <= radius;
    }

    /** Inside the walls: the ledger sits 6 blocks off the 63x63 core's centre, so 30 blocks covers the interior. */
    public boolean insideWalls(BlockPos pos) {
        return Math.max(Math.abs(pos.getX() - center.getX()), Math.abs(pos.getZ() - center.getZ())) <= 30
                && Math.abs(pos.getY() - center.getY()) <= 24;
    }

    public double horizontalDistance(BlockPos pos) {
        double dx = pos.getX() - center.getX();
        double dz = pos.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("dimension", dimension.location().toString());
        tag.putLong("center", center.asLong());
        tag.putInt("radius", radius);
        tag.putLong("seed", seed);
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

    static Township load(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dimension"));
        Township t = new Township(tag.getUUID("id"), ResourceKey.create(Registries.DIMENSION, dim == null ? Level.OVERWORLD.location() : dim),
                BlockPos.of(tag.getLong("center")), tag.getInt("radius"), tag.getLong("seed"));
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
        return t;
    }

    private static java.util.Optional<UUID> parse(String s) {
        try {
            return java.util.Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
