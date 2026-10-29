package com.lacuna.mod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config (spec section 6). Documented in docs/CONFIG.md.
 * <p>
 * Values are only read at runtime through the safe getters at the bottom, which fall back to the default when
 * the config is not loaded yet (for example during early world loading on a misconfigured server).
 */
public final class LacunaConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- general
    public static final ModConfigSpec.IntValue BLOCK_CHANGES_PER_TICK;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    // ---------------------------------------------------------------- divine sight
    public static final ModConfigSpec.BooleanValue DIVINE_SIGHT_ENABLED;
    public static final ModConfigSpec.IntValue DIVINE_SIGHT_RANGE;
    public static final ModConfigSpec.IntValue DIVINE_SIGHT_MAX_BOXES;

    // ---------------------------------------------------------------- forgotten land / banishment
    public static final ModConfigSpec.BooleanValue FL_ENABLED;
    public static final ModConfigSpec.DoubleValue FL_SCALE;
    public static final ModConfigSpec.IntValue FL_TIMER_TICKS;
    public static final ModConfigSpec.DoubleValue FL_DRIFT_MIN;
    public static final ModConfigSpec.DoubleValue FL_DRIFT_MAX;
    public static final ModConfigSpec.BooleanValue BANISH_ALLOW_PLAYERS;
    public static final ModConfigSpec.BooleanValue RELICS_ENABLED;

    // ---------------------------------------------------------------- beam
    public static final ModConfigSpec.BooleanValue BEAM_ENABLED;
    public static final ModConfigSpec.DoubleValue BEAM_RANGE;
    public static final ModConfigSpec.DoubleValue BEAM_DAMAGE_PER_SECOND;
    public static final ModConfigSpec.BooleanValue BEAM_EVAPORATE_WATER;
    public static final ModConfigSpec.BooleanValue BEAM_AFFECTS_BLOCKS;

    // ---------------------------------------------------------------- eclipser
    public static final ModConfigSpec.BooleanValue ECLIPSER_ENABLED;
    public static final ModConfigSpec.DoubleValue ECLIPSE_DASH_MAX;
    public static final ModConfigSpec.IntValue ECLIPSE_COOLDOWN;
    public static final ModConfigSpec.BooleanValue SHADOW_WALK_ENABLED;

    // ---------------------------------------------------------------- sun's fury
    public static final ModConfigSpec.BooleanValue SUNS_FURY_ENABLED;
    public static final ModConfigSpec.IntValue SOLAR_DESTRUCTION_RADIUS;
    public static final ModConfigSpec.IntValue SOLAR_MAX_CHANGES_PER_USE;
    public static final ModConfigSpec.BooleanValue SOLAR_CHANGE_TIME;
    public static final ModConfigSpec.IntValue SOLAR_FOCUS_TICKS;

    // ---------------------------------------------------------------- utility items
    public static final ModConfigSpec.BooleanValue PEARL_ENABLED;
    public static final ModConfigSpec.BooleanValue WATCH_ENABLED;
    public static final ModConfigSpec.BooleanValue MIRROR_ENABLED;
    public static final ModConfigSpec.BooleanValue LANTERN_ENABLED;
    public static final ModConfigSpec.IntValue LANTERN_RADIUS;
    public static final ModConfigSpec.BooleanValue FLUTE_ENABLED;
    public static final ModConfigSpec.BooleanValue CHART_ENABLED;
    public static final ModConfigSpec.IntValue CHART_BUDGET_MICROS;

    // ---------------------------------------------------------------- gear
    public static final ModConfigSpec.BooleanValue SPECIAL_WEAPONS_ENABLED;
    public static final ModConfigSpec.BooleanValue STARLACE_GLIDE_ENABLED;
    public static final ModConfigSpec.BooleanValue STARLACE_ABILITIES_ENABLED;
    public static final ModConfigSpec.DoubleValue UMBRAL_DARK_BONUS;

    // ---------------------------------------------------------------- wood
    public static final ModConfigSpec.BooleanValue WOOD_EXTRAS;
    public static final ModConfigSpec.BooleanValue TIDEBLOOM_FISH;

    // ---------------------------------------------------------------- forgotten land biomes & structures
    public static final ModConfigSpec.BooleanValue MARSH_FOG_ENABLED;
    public static final ModConfigSpec.BooleanValue DUNES_SLOW_CROPS;
    public static final ModConfigSpec.BooleanValue UPDRAFT_ENABLED;
    public static final ModConfigSpec.BooleanValue LABYRINTH_ENABLED;
    public static final ModConfigSpec.BooleanValue MERCHANT_ENABLED;
    public static final ModConfigSpec.IntValue MERCHANT_SPAWN_INTERVAL;
    public static final ModConfigSpec.BooleanValue SUNKEN_BELL_ENABLED;

    // ---------------------------------------------------------------- villages (civic system in vanilla villages)
    public static final ModConfigSpec.BooleanValue VILLAGES_ENABLED;
    public static final ModConfigSpec.BooleanValue VILLAGES_LAWS;
    public static final ModConfigSpec.BooleanValue VILLAGES_COUNCIL;
    public static final ModConfigSpec.BooleanValue VILLAGES_CURRENCY;
    public static final ModConfigSpec.BooleanValue VILLAGES_GUILDS;
    public static final ModConfigSpec.BooleanValue VILLAGES_CIVIC_BUILDINGS;

    // ---------------------------------------------------------------- starfall (natural falling stars) and the Starlace Bandage
    public static final ModConfigSpec.BooleanValue NATURAL_STARS;
    public static final ModConfigSpec.DoubleValue NATURAL_STAR_CHANCE;
    public static final ModConfigSpec.BooleanValue STARLACE_BANDAGE_ENABLED;

    // ---------------------------------------------------------------- shadow possession (Shadow Walk)
    public static final ModConfigSpec.BooleanValue SHADOW_POSSESSION_ENABLED;
    public static final ModConfigSpec.BooleanValue SHADOW_POSSESSION_PLAYERS;

    public static final ModConfigSpec SPEC;

    static {
        B.push("general");
        BLOCK_CHANGES_PER_TICK = B.comment("Hard cap of block changes per tick for long block jobs (Sun's Fury).")
                .defineInRange("blockChangesPerTick", Balance.BLOCK_CHANGES_PER_TICK, 1, 4000);
        DEBUG_LOGGING = B.comment("Extra debug logging for Lacuna systems.").define("debugLogging", false);
        B.pop();

        B.push("divineSight");
        DIVINE_SIGHT_ENABLED = B.comment("Client-side Divine Sight through Divine Glass (X-ray window or outlines: see the client config).").define("enabled", true);
        DIVINE_SIGHT_RANGE = B.defineInRange("range", Balance.DIVINE_SIGHT_RANGE, 2, 32);
        DIVINE_SIGHT_MAX_BOXES = B.defineInRange("maxBoxes", Balance.DIVINE_SIGHT_MAX_BOXES, 1, 512);
        B.pop();

        B.push("forgottenLand");
        FL_ENABLED = B.comment("Totem of Banishing, banishment timers and Forgotten Land rules.").define("enabled", true);
        FL_SCALE = B.comment("1 Forgotten Land block = this many overworld blocks.").defineInRange("scale", Balance.FL_SCALE, 1.0, 128.0);
        FL_TIMER_TICKS = B.comment("Base banishment duration in ticks (6 minutes).").defineInRange("timerTicks", Balance.FL_TIMER_TICKS, 200, 720000);
        FL_DRIFT_MIN = B.defineInRange("driftMin", Balance.FL_DRIFT_MIN, 0.1, 10.0);
        FL_DRIFT_MAX = B.defineInRange("driftMax", Balance.FL_DRIFT_MAX, 0.1, 10.0);
        BANISH_ALLOW_PLAYERS = B.comment("Allow banishing other players (also requires PvP to be enabled on the server).")
                .define("banishAllowPlayers", true);
        RELICS_ENABLED = B.comment("Deaths inside the Forgotten Land scatter items as Unreadable Relics in the overworld.")
                .define("relicsEnabled", true);
        B.pop();

        B.push("beam");
        BEAM_ENABLED = B.define("enabled", true);
        BEAM_RANGE = B.defineInRange("range", Balance.BEAM_RANGE, 4.0, 64.0);
        BEAM_DAMAGE_PER_SECOND = B.defineInRange("damagePerSecond", (double) Balance.BEAM_DAMAGE_PER_SECOND, 0.0, 200.0);
        BEAM_EVAPORATE_WATER = B.comment("If true, the beam may remove flowing water it hits.").define("evaporateWater", false);
        BEAM_AFFECTS_BLOCKS = B.comment("Ignite flammable blocks and melt snow/ice.").define("affectsBlocks", true);
        B.pop();

        B.push("eclipser");
        ECLIPSER_ENABLED = B.define("enabled", true);
        ECLIPSE_DASH_MAX = B.defineInRange("dashMaxDistance", Balance.ECLIPSE_DASH_MAX, 1.0, 32.0);
        ECLIPSE_COOLDOWN = B.defineInRange("dashCooldownTicks", Balance.ECLIPSE_COOLDOWN, 0, 72000);
        SHADOW_WALK_ENABLED = B.comment("Shadow Walk temporarily switches the player to spectator mode and restores it safely.")
                .define("shadowWalk", true);
        B.pop();

        B.push("sunsFury");
        SUNS_FURY_ENABLED = B.define("enabled", true);
        SOLAR_DESTRUCTION_RADIUS = B.defineInRange("destructionRadius", Balance.SOLAR_DESTRUCTION_RADIUS, 0, 16);
        SOLAR_MAX_CHANGES_PER_USE = B.defineInRange("maxBlockChangesPerUse", Balance.SOLAR_MAX_CHANGES_PER_USE, 0, 100000);
        SOLAR_CHANGE_TIME = B.comment("Detonation in the overworld sets the time to the next noon and clears weather.")
                .define("changeTime", true);
        SOLAR_FOCUS_TICKS = B.comment("Ticks between the landing of Sun's Fury and the strike (the sun climbs to noon meanwhile).")
                .defineInRange("focusTicks", Balance.SUNS_FURY_FOCUS_TICKS, 20, 600);
        B.pop();

        B.push("items");
        PEARL_ENABLED = B.define("fracturedPearl", true);
        WATCH_ENABLED = B.define("pocketWatch", true);
        MIRROR_ENABLED = B.define("mirrorOfRecursion", true);
        LANTERN_ENABLED = B.define("nullLantern", true);
        LANTERN_RADIUS = B.defineInRange("nullLanternRadius", Balance.LANTERN_RADIUS, 1, 64);
        FLUTE_ENABLED = B.define("whisperboneFlute", true);
        CHART_ENABLED = B.comment("Wayfarer's Chart: a live map of the Overworld around its holder (in other dimensions: around their translated position).")
                .define("wayfarersChart", true);
        CHART_BUDGET_MICROS = B.comment("Server time all held Wayfarer's Charts may spend sampling the Overworld per tick, in microseconds.")
                .defineInRange("wayfarersChartBudgetMicros", Balance.CHART_BUDGET_MICROS, 100, 20000);
        B.pop();

        B.push("gear");
        SPECIAL_WEAPONS_ENABLED = B.define("specialWeapons", true);
        STARLACE_GLIDE_ENABLED = B.define("starlaceGlide", true);
        STARLACE_ABILITIES_ENABLED = B.comment("Starlace set abilities: Constellation Step (double jump, full set), Starfall (softer landings, "
                + "a starlight shockwave with the full set) and Starlit (speed at night under the open sky).").define("starlaceAbilities", true);
        UMBRAL_DARK_BONUS = B.defineInRange("umbralDarkBonus", (double) Balance.UMBRAL_DARK_BONUS, 0.0, 4.0);
        B.pop();

        B.push("wood");
        WOOD_EXTRAS = B.comment("Reserved for signs/boats (M12, currently not implemented).").define("extras", false);
        TIDEBLOOM_FISH = B.comment("Tidebloom roots occasionally spawn fish in adjacent water.").define("tidebloomFish", true);
        B.pop();

        B.push("forgottenBiomes");
        MARSH_FOG_ENABLED = B.comment("Client: dense fog and muffled sound in the Whispering Marsh.").define("marshFog", true);
        DUNES_SLOW_CROPS = B.define("dunesSlowCrops", true);
        UPDRAFT_ENABLED = B.define("updraftSprings", true);
        LABYRINTH_ENABLED = B.define("mirrorLabyrinth", true);
        MERCHANT_ENABLED = B.define("lastMerchant", true);
        MERCHANT_SPAWN_INTERVAL = B.defineInRange("lastMerchantIntervalTicks", Balance.MERCHANT_SPAWN_INTERVAL, 1200, 10000000);
        SUNKEN_BELL_ENABLED = B.define("sunkenBell", true);
        B.pop();

        B.push("villages");
        VILLAGES_ENABLED = B.comment("Civic system in vanilla villages: reputation, laws, council, coins, guilds, schools.")
                .define("enabled", true);
        VILLAGES_LAWS = B.define("laws", true);
        VILLAGES_COUNCIL = B.define("council", true);
        VILLAGES_CURRENCY = B.define("currency", true);
        VILLAGES_GUILDS = B.define("guilds", true);
        VILLAGES_CIVIC_BUILDINGS = B.comment("Add the village hall, mint, guild hall and schoolhouse to newly generated vanilla villages.")
                .define("civicBuildings", true);
        B.pop();

        B.push("starfall");
        NATURAL_STARS = B.comment("On clear nights in the overworld a star now and then falls near a player, leaving a molten Star Rock.")
                .define("naturalStars", true);
        NATURAL_STAR_CHANCE = B.comment("Chance, per player and per clear night, that a star falls near them (0 = never, 1 = every night).")
                .defineInRange("naturalChancePerNight", com.lacuna.mod.star.Starfall.NATURAL_CHANCE_PER_NIGHT, 0.0, 1.0);
        STARLACE_BANDAGE_ENABLED = B.comment("Starlace Bandages can mummify creatures (shears always unwrap them).")
                .define("starlaceBandage", true);
        B.pop();

        B.push("shadowPossession");
        SHADOW_POSSESSION_ENABLED = B.comment("While shadow walking, clicking a creature or player possesses it: you see through its eyes and steer it "
                + "until it fights back and casts you out (off: clicking only spectates, as in vanilla).").define("enabled", true);
        SHADOW_POSSESSION_PLAYERS = B.comment("Players can be possessed too (also requires PvP to be enabled on the server).")
                .define("players", true);
        B.pop();

        SPEC = B.build();
    }

    private LacunaConfig() {}

    public static boolean loaded() {
        return SPEC.isLoaded();
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        try {
            return loaded() ? value.getAsBoolean() : value.getDefault();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static int get(ModConfigSpec.IntValue value) {
        try {
            return loaded() ? value.getAsInt() : value.getDefault();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static double get(ModConfigSpec.DoubleValue value) {
        try {
            return loaded() ? value.getAsDouble() : value.getDefault();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    /** Drift range normalised so min &lt;= max. */
    public static double driftMin() {
        return Math.min(get(FL_DRIFT_MIN), get(FL_DRIFT_MAX));
    }

    public static double driftMax() {
        return Math.max(get(FL_DRIFT_MIN), get(FL_DRIFT_MAX));
    }
}
