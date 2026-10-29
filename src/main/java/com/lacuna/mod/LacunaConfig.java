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

    // ---------------------------------------------------------------- the Lacuna Almanac (in-game guide)
    public static final ModConfigSpec.BooleanValue GIVE_ALMANAC_ON_FIRST_JOIN;

    public static final ModConfigSpec SPEC;

    static {
        B.push("general");
        BLOCK_CHANGES_PER_TICK = B.comment("Hard cap of block changes per tick for long block jobs (Sun's Fury).")
                .defineInRange("blockChangesPerTick", Balance.BLOCK_CHANGES_PER_TICK, 1, 4000);
        B.pop();

        B.push("divineSight");
        DIVINE_SIGHT_ENABLED = B.comment("Client-side Divine Sight through Divine Glass (X-ray window or outlines: see the client config).").define("enabled", true);
        DIVINE_SIGHT_RANGE = B.comment("Outline mode: how far (blocks) Divine Sight reaches through Divine Glass for ores, containers, spawners and mobs.")
                .defineInRange("range", Balance.DIVINE_SIGHT_RANGE, 2, 32);
        DIVINE_SIGHT_MAX_BOXES = B.comment("Outline mode: most outline boxes drawn at once (nearest first).")
                .defineInRange("maxBoxes", Balance.DIVINE_SIGHT_MAX_BOXES, 1, 512);
        B.pop();

        B.push("forgottenLand");
        FL_ENABLED = B.comment("Totem of Banishing, banishment timers and Forgotten Land rules.").define("enabled", true);
        FL_SCALE = B.comment("1 Forgotten Land block = this many overworld blocks.").defineInRange("scale", Balance.FL_SCALE, 1.0, 128.0);
        FL_TIMER_TICKS = B.comment("Base banishment duration in ticks (6 minutes).").defineInRange("timerTicks", Balance.FL_TIMER_TICKS, 200, 720000);
        FL_DRIFT_MIN = B.comment("Each banishment lasts timerTicks times a hidden random factor between driftMin and driftMax.")
                .defineInRange("driftMin", Balance.FL_DRIFT_MIN, 0.1, 10.0);
        FL_DRIFT_MAX = B.comment("Upper end of the hidden banishment time factor (see driftMin; swapped if lower).")
                .defineInRange("driftMax", Balance.FL_DRIFT_MAX, 0.1, 10.0);
        BANISH_ALLOW_PLAYERS = B.comment("Allow banishing other players (also requires PvP to be enabled on the server).")
                .define("banishAllowPlayers", true);
        RELICS_ENABLED = B.comment("Deaths inside the Forgotten Land scatter items as Unreadable Relics in the overworld.")
                .define("relicsEnabled", true);
        B.pop();

        B.push("beam");
        BEAM_ENABLED = B.comment("Divine Magnifying Glass: the focused sunlight beam.").define("enabled", true);
        BEAM_RANGE = B.comment("Reach of the beam in blocks.").defineInRange("range", Balance.BEAM_RANGE, 4.0, 64.0);
        BEAM_DAMAGE_PER_SECOND = B.comment("Damage per second the beam deals to creatures in it (0 = harmless light).")
                .defineInRange("damagePerSecond", (double) Balance.BEAM_DAMAGE_PER_SECOND, 0.0, 200.0);
        BEAM_EVAPORATE_WATER = B.comment("If true, the beam may remove flowing water it hits.").define("evaporateWater", false);
        BEAM_AFFECTS_BLOCKS = B.comment("Ignite flammable blocks and melt snow/ice.").define("affectsBlocks", true);
        B.pop();

        B.push("eclipser");
        ECLIPSER_ENABLED = B.comment("Eclipser: the charged shadow dash and its abilities.").define("enabled", true);
        ECLIPSE_DASH_MAX = B.comment("Length (blocks) of a fully charged Eclipser dash.")
                .defineInRange("dashMaxDistance", Balance.ECLIPSE_DASH_MAX, 1.0, 32.0);
        ECLIPSE_COOLDOWN = B.comment("Cooldown after an Eclipser dash, in ticks (20 ticks = 1 second).")
                .defineInRange("dashCooldownTicks", Balance.ECLIPSE_COOLDOWN, 0, 72000);
        SHADOW_WALK_ENABLED = B.comment("Shadow Walk temporarily switches the player to spectator mode and restores it safely.")
                .define("shadowWalk", true);
        B.pop();

        B.push("sunsFury");
        SUNS_FURY_ENABLED = B.comment("Sun's Fury: the thrown spear of sunlight and its strike.").define("enabled", true);
        SOLAR_DESTRUCTION_RADIUS = B.comment("Radius (blocks) of the ground a Sun's Fury strike scorches and breaks (needs mobGriefing; 0 = none).")
                .defineInRange("destructionRadius", Balance.SOLAR_DESTRUCTION_RADIUS, 0, 16);
        SOLAR_MAX_CHANGES_PER_USE = B.comment("Most blocks a single Sun's Fury strike may change (0 = none).")
                .defineInRange("maxBlockChangesPerUse", Balance.SOLAR_MAX_CHANGES_PER_USE, 0, 100000);
        SOLAR_CHANGE_TIME = B.comment("Detonation in the overworld sets the time to the next noon and clears weather.")
                .define("changeTime", true);
        SOLAR_FOCUS_TICKS = B.comment("Ticks between the landing of Sun's Fury and the strike (the sun climbs to noon meanwhile).")
                .defineInRange("focusTicks", Balance.SUNS_FURY_FOCUS_TICKS, 20, 600);
        B.pop();

        B.push("items");
        PEARL_ENABLED = B.comment("Fractured Pearl with its Arrival and Departure Shards (linked teleports).").define("fracturedPearl", true);
        WATCH_ENABLED = B.comment("Chronoshard Pocket Watch (rewinds you to where and how you were a little while ago).")
                .define("pocketWatch", true);
        MIRROR_ENABLED = B.comment("Mirror of Recursion (raised like a shield, it sends projectiles back and keeps echoes of them).").define("mirrorOfRecursion", true);
        LANTERN_ENABLED = B.comment("Null Lantern (held or placed and lit: hostile mobs cannot target you and monsters do not spawn nearby).").define("nullLantern", true);
        LANTERN_RADIUS = B.comment("Radius (blocks) in which mobs already hunting a Null Lantern holder lose track of them.").defineInRange("nullLanternRadius", Balance.LANTERN_RADIUS, 1, 64);
        FLUTE_ENABLED = B.comment("Whisperbone Flute (calls up the Echoes of creatures that died nearby in the last minute).").define("whisperboneFlute", true);
        CHART_ENABLED = B.comment("Wayfarer's Chart: a live map of the Overworld around its holder (in other dimensions: around their translated position).")
                .define("wayfarersChart", true);
        CHART_BUDGET_MICROS = B.comment("Server time all held Wayfarer's Charts may spend sampling the Overworld per tick, in microseconds.")
                .defineInRange("wayfarersChartBudgetMicros", Balance.CHART_BUDGET_MICROS, 100, 20000);
        B.pop();

        B.push("gear");
        SPECIAL_WEAPONS_ENABLED = B.comment("Abilities of the special weapons and relic gear (Gravemaw Greatsword, Tidecaller Sickle, Heartwood Bow,",
                "Echo Dagger, Pyrrhic Scepter, Ember Triangle, Wand of Quickening, Starcaller Trumpet) and of the Umbral Steel tools;",
                "off: they work as plain items.").define("specialWeapons", true);
        STARLACE_GLIDE_ENABLED = B.comment("Starlace Boots: sneak while falling to drift down slowly (once per fall).").define("starlaceGlide", true);
        STARLACE_ABILITIES_ENABLED = B.comment("Starlace set abilities: Constellation Step (double jump, full set), Starfall (softer landings, "
                + "a starlight shockwave with the full set) and Starlit (speed at night under the open sky).").define("starlaceAbilities", true);
        UMBRAL_DARK_BONUS = B.comment("Extra damage fraction of Umbral Steel tools and weapons in darkness (0.25 = +25 %).")
                .defineInRange("umbralDarkBonus", (double) Balance.UMBRAL_DARK_BONUS, 0.0, 4.0);
        B.pop();

        B.push("wood");
        TIDEBLOOM_FISH = B.comment("Tidebloom roots occasionally spawn fish in adjacent water.").define("tidebloomFish", true);
        B.pop();

        B.push("forgottenBiomes");
        MARSH_FOG_ENABLED = B.comment("Client: dense fog and muffled sound in the Whispering Marsh.").define("marshFog", true);
        DUNES_SLOW_CROPS = B.comment("Crops grow at half speed in the Clockwork Dunes.").define("dunesSlowCrops", true);
        UPDRAFT_ENABLED = B.comment("Updraft Springs lift whatever stands in their column of air.").define("updraftSprings", true);
        LABYRINTH_ENABLED = B.comment("Mirror Labyrinths: stepping on the central trigger summons your Reflection, and defeating it opens the exit",
                "(off: the trigger opens the exit at once).").define("mirrorLabyrinth", true);
        MERCHANT_ENABLED = B.comment("The Last Merchant now and then visits a player in the overworld, like the wandering trader.").define("lastMerchant", true);
        MERCHANT_SPAWN_INTERVAL = B.comment("Ticks between two Last Merchant spawn attempts, each a 25 % chance (72000 = 3 in-game days).")
                .defineInRange("lastMerchantIntervalTicks", Balance.MERCHANT_SPAWN_INTERVAL, 1200, 10000000);
        SUNKEN_BELL_ENABLED = B.comment("Ringing a Sunken Bell reveals the ores around it (off: it only rings).").define("sunkenBell", true);
        B.pop();

        B.push("villages");
        VILLAGES_ENABLED = B.comment("Civic system in vanilla villages: reputation, laws, council, coins, guilds, schools.")
                .define("enabled", true);
        VILLAGES_LAWS = B.comment("Village laws: a drawn weapon in a village or a stranger outdoors at curfew costs reputation; hostile players "
                + "are chased by the iron golems.").define("laws", true);
        VILLAGES_COUNCIL = B.comment("The village council meets every 7 days and sets a policy for the week (Free Market, Austerity, Festival...).").define("council", true);
        VILLAGES_CURRENCY = B.comment("Village coins replace emeralds in Lacuna's trades; the Coiner (Mint Press) exchanges ores and ingots for them.").define("currency", true);
        VILLAGES_GUILDS = B.comment("Guilds: join one at a Guild Board to trade with its Guildmaster and take contracts.").define("guilds", true);
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

        B.push("almanac");
        GIVE_ALMANAC_ON_FIRST_JOIN = B.comment("Give every player a Lacuna Almanac (the in-game guide: what everything does and where "
                + "to find it) the first time they join a world.").define("giveOnFirstJoin", true);
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
