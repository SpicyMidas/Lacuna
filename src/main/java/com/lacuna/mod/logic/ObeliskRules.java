package com.lacuna.mod.logic;

/** Pure rules of the Obelisk of Unreadable Names (spec M10), unit-tested. */
public final class ObeliskRules {
    private ObeliskRules() {}

    /** Lore line index chosen by position hash: stable for a position, spread over all lines. */
    public static int loreIndex(int x, int y, int z, int lines) {
        long h = x * 73_856_093L ^ y * 19_349_663L ^ z * 83_492_791L;
        h ^= (h >>> 17);
        h *= 0x9E3779B97F4A7C15L;
        h ^= (h >>> 29);
        return (int) Math.floorMod(h, (long) Math.max(1, lines));
    }

    /** Which reward a successful roll gives: 0 = 1-3 rubies, 1 = 1-2 divine glass shards, 2 = 1 chronal shard. */
    public static int rewardKind(double roll) {
        if (roll < 0.45) {
            return 0;
        }
        return roll < 0.85 ? 1 : 2;
    }

    /** May this player receive a reward here? Once per player per obelisk, and not while the obelisk cools down. */
    public static boolean mayReward(boolean alreadyClaimed, long now, long cooldownUntil) {
        return !alreadyClaimed && now >= cooldownUntil;
    }
}
