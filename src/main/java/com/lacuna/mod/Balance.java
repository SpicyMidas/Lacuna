package com.lacuna.mod;

/**
 * Every fixed gameplay number of the mod (spec rule 1.3). Values that make sense to tune per server live in
 * {@link LacunaConfig} and use the constants below as their defaults. Values needed during registration
 * (durability, stack sizes, attack stats) must stay here because configs are not loaded yet at that point.
 */
public final class Balance {
    private Balance() {}

    // ---------------------------------------------------------------- global budgets (section 3)
    public static final int BLOCK_CHANGES_PER_TICK = 400;
    public static final int MAX_ENTITY_QUERY_SIDE = 64;
    public static final int MAX_PARTICLE_PACKETS_PER_TICK = 40;

    // ---------------------------------------------------------------- safe-spot search (section 6)
    public static final int SAFE_SPOT_HORIZONTAL = 16;
    public static final int SAFE_SPOT_VERTICAL = 24;

    // ---------------------------------------------------------------- forgotten land (section 6, 9)
    public static final double FL_SCALE = 12.0;
    public static final int FL_TIMER_TICKS = 7200;
    public static final double FL_DRIFT_MIN = 0.75;
    public static final double FL_DRIFT_MAX = 1.5;
    public static final int FL_CHECK_INTERVAL = 20;
    public static final int ARRIVAL_PROTECTION_TICKS = 40;

    // ---------------------------------------------------------------- ruby (M1)
    public static final int RUBY_TOOL_DURABILITY = 800;
    public static final float RUBY_TOOL_SPEED = 7.0F;
    public static final float RUBY_TOOL_DAMAGE_BONUS = 3.0F;
    public static final int RUBY_TOOL_ENCHANTABILITY = 15;

    // ---------------------------------------------------------------- divine sight (M1)
    public static final int DIVINE_SIGHT_RANGE = 12;
    public static final int DIVINE_SIGHT_GLASS_DISTANCE = 6;
    public static final double DIVINE_SIGHT_CONE_DEGREES = 50.0;
    public static final int DIVINE_SIGHT_SCAN_INTERVAL = 10;
    public static final int DIVINE_SIGHT_MAX_POSITIONS = 4096;
    public static final int DIVINE_SIGHT_MAX_BOXES = 128;
    public static final int DIVINE_SIGHT_MAX_ENTITY_BOXES = 32;

    // ---------------------------------------------------------------- totem of banishing (M2)
    public static final int TOTEM_DURABILITY = 16;
    public static final int TOTEM_COOLDOWN = 2400;
    public static final double TOTEM_KNOCKBACK = 1.5;
    public static final float TOTEM_REPAIR_FRACTION = 0.25F;

    // ---------------------------------------------------------------- relics (M2)
    public static final int RELIC_LIFETIME_TICKS = 36000; // ~30 min
    public static final int RELIC_OWNER_ONLY_TICKS = 12000; // 10 min
    public static final int RELIC_COARSE_STEP = 100;

    // ---------------------------------------------------------------- magnifying glass (M3)
    public static final int GLASS_DURABILITY = 500;
    public static final int GLASS_TICKS_PER_DURABILITY = 20;
    public static final int GLASS_USE_DURATION = 72000;
    public static final double BEAM_RANGE = 24.0;
    public static final float BEAM_DAMAGE_PER_SECOND = 12.0F;
    public static final int BEAM_DAMAGE_INTERVAL = 5;
    public static final int BEAM_FIRE_SECONDS = 4;
    public static final int BEAM_BLOCK_INTERVAL = 10;
    public static final int BEAM_VAPOR_INTERVAL = 5;
    public static final int BEAM_RELEASE_COOLDOWN = 10;
    public static final int BEAM_REPAIR_PER_SHARD = 60;
    public static final int BEAM_PARTICLE_INTERVAL = 2;
    public static final int BEAM_MAX_PARTICLES = 24;

    // ---------------------------------------------------------------- umbral steel / eclipser (M4)
    public static final int ECLIPSER_DURABILITY = 250;
    public static final int ECLIPSER_ATTACK_DAMAGE = 7; // including base 1
    public static final float ECLIPSER_ATTACK_SPEED = 1.6F;
    public static final int ECLIPSE_CHARGE_PER_HIT = 10;
    public static final int ECLIPSE_MAX_CHARGE = 100;
    public static final int ECLIPSE_HIT_INTERVAL = 5;
    public static final int ECLIPSE_DECAY_DELAY = 160;
    public static final int ECLIPSE_DECAY_PER_SECOND = 2;
    public static final int ECLIPSE_MIN_DASH_CHARGE = 20;
    public static final double ECLIPSE_DASH_BASE = 4.0;
    public static final double ECLIPSE_DASH_PER_CHARGE = 0.10;
    public static final double ECLIPSE_DASH_MAX = 14.0;
    public static final double ECLIPSE_DASH_RADIUS = 1.5;
    public static final float ECLIPSE_DAMAGE_BASE = 6.0F;
    public static final float ECLIPSE_DAMAGE_PER_CHARGE = 0.14F;
    public static final float ECLIPSE_DAMAGE_MAX = 20.0F;
    public static final int ECLIPSE_DURABILITY_BASE = 5;
    public static final int ECLIPSE_DURABILITY_SCALE = 15;
    public static final int ECLIPSE_COOLDOWN = 400;
    public static final int SHADOW_WALK_BASE_TICKS = 40;
    public static final int SHADOW_WALK_SCALE_TICKS = 80;

    // ---------------------------------------------------------------- sun's fury (M4)
    public static final float SUNS_FURY_SPEED = 2.5F;
    public static final double SUNS_FURY_GRAVITY = 0.03;
    public static final int SUNS_FURY_FUSE = 200;
    public static final int SOLAR_RAY_TICKS = 60;
    public static final double SOLAR_RAY_DAMAGE_RADIUS = 7.0;
    public static final float SOLAR_RAY_DAMAGE = 5.0F;
    public static final int SOLAR_RAY_DAMAGE_INTERVAL = 5;
    public static final int SOLAR_RAY_FIRE_SECONDS = 10;
    public static final int SOLAR_RAY_HEIGHT = 80;
    public static final int SOLAR_DESTRUCTION_RADIUS = 6;
    public static final int SOLAR_DESTRUCTION_BELOW = 8;
    public static final int SOLAR_DESTRUCTION_ABOVE = 40;
    public static final float SOLAR_MAX_HARDNESS = 50.0F;
    public static final int SOLAR_MAX_CHANGES_PER_USE = 12000;

    // ---------------------------------------------------------------- fractured pearl (M5)
    public static final int ARRIVAL_SHARD_DURABILITY = 10;
    public static final int ARRIVAL_SHARD_COOLDOWN = 600;
    public static final int DEPARTURE_SHARD_DURABILITY = 5;
    public static final int DEPARTURE_SHARD_COOLDOWN = 300;
    public static final float ARRIVAL_SHARD_SPEED = 1.5F;
    public static final double ARRIVAL_SHARD_GRAVITY = 0.03;
    public static final int SHARD_REPAIR_PER_PEARL = 3;
    public static final float ANCHOR_BLAST_RESISTANCE = 1200.0F;

    // ---------------------------------------------------------------- pocket watch (M6)
    public static final int WATCH_DURABILITY = 12;
    public static final int WATCH_SNAPSHOT_INTERVAL = 2400;
    public static final int WATCH_RING_SIZE = 3;
    public static final int WATCH_MIN_AGE = 400;
    public static final int WATCH_MAX_SHARDS = 5;
    public static final int WATCH_BASE_COOLDOWN = 6000;
    public static final int WATCH_COOLDOWN_PER_SHARD = 600;
    public static final int WATCH_HARMFUL_WINDOW = 200;

    // ---------------------------------------------------------------- mirror of recursion (M6)
    public static final int MIRROR_DURABILITY = 20;
    public static final int MIRROR_MAX_STORED = 3;
    public static final int MIRROR_MAX_REUSE = 3;
    public static final double MIRROR_REUSE_FACTOR = 0.85;

    // ---------------------------------------------------------------- null lantern (M6)
    public static final int LANTERN_DURABILITY = 300;
    public static final int LANTERN_RADIUS = 16;
    public static final int LANTERN_SPAWN_RADIUS = 24;
    public static final int LANTERN_PROVOKE_TICKS = 100;
    public static final double LANTERN_CLOSE_RANGE = 3.0;
    public static final int LANTERN_TICKS_PER_DURABILITY = 200;

    // ---------------------------------------------------------------- whisperbone flute (M6)
    public static final int FLUTE_DURABILITY = 40;
    public static final int FLUTE_COOLDOWN = 1200;
    public static final int FLUTE_MAX_ECHOES = 3;
    public static final int FLUTE_DEATH_RADIUS = 20;
    public static final int FLUTE_DEATH_WINDOW = 1200;
    public static final int FLUTE_RING_SIZE = 64;
    public static final int FLUTE_REPAIR_PER_FRAGMENT = 20;
    public static final int ECHO_LIFETIME = 200;
    public static final float ECHO_MAX_HEALTH = 20.0F;
    public static final float ECHO_MAX_ATTACK = 6.0F;
    public static final double ECHO_SPEED = 0.3;

    // ---------------------------------------------------------------- orrery (M6)
    public static final int ORRERY_COOLDOWN = 100;
    public static final int ORRERY_RADIUS_CHUNKS = 48;
    public static final int ORRERY_GLOBAL_INTERVAL = 20;

    // ---------------------------------------------------------------- gear (M7)
    public static final int UMBRAL_TOOL_DURABILITY = 900;
    public static final float UMBRAL_TOOL_SPEED = 6.5F;
    public static final float UMBRAL_TOOL_DAMAGE_BONUS = 2.5F;
    public static final int UMBRAL_TOOL_ENCHANTABILITY = 12;
    public static final float UMBRAL_DARK_BONUS = 0.25F;
    public static final int UMBRAL_DARK_LIGHT = 4;

    public static final int STARLACE_DURABILITY_FACTOR = 24;
    public static final int STARLACE_ENCHANTABILITY = 18;
    public static final float STARLACE_TOUGHNESS = 0.5F;
    public static final float STARLACE_GLIDE_FALL = 4.0F;
    public static final int STARLACE_GLIDE_TICKS = 80;
    public static final int STARLACE_GLIDE_COOLDOWN = 200;

    public static final int GRAVEMAW_DURABILITY = 700;
    public static final int GRAVEMAW_DAMAGE = 11;
    public static final float GRAVEMAW_SPEED = 0.8F;
    public static final int GRAVEMAW_MAX_CHARGE = 40;
    public static final int GRAVEMAW_MIN_CHARGE = 20;
    public static final double GRAVEMAW_RANGE = 8.0;
    public static final double GRAVEMAW_ARC_DEGREES = 35.0;
    public static final float GRAVEMAW_WAVE_BASE = 10.0F;
    public static final float GRAVEMAW_WAVE_PER_TICK = 0.3F;
    public static final int GRAVEMAW_COOLDOWN = 120;
    public static final double GRAVEMAW_STILL_EPSILON = 0.05;

    public static final int SICKLE_DURABILITY = 450;
    public static final int SICKLE_DAMAGE = 4;
    public static final float SICKLE_SPEED = 2.2F;
    public static final int SICKLE_COOLDOWN = 100;
    public static final double SICKLE_RANGE = 12.0;
    public static final double SICKLE_PULL = 1.2;
    public static final int SICKLE_MAX_TIDE = 3;
    public static final float SICKLE_HOOK_DAMAGE = 3.0F;
    public static final float SICKLE_TIDE_HEAL = 2.0F;

    public static final int HEARTWOOD_DURABILITY = 480;
    public static final int ROOTED_TICKS = 40;
    public static final int ROOTED_PINNED_TICKS = 80;

    public static final int ECHO_DAGGER_DURABILITY = 300;
    public static final int ECHO_DAGGER_DAMAGE = 3;
    public static final float ECHO_DAGGER_SPEED = 2.6F;
    public static final int ECHO_DAGGER_DELAY = 10;
    public static final float ECHO_DAGGER_FACTOR = 0.6F;

    public static final int SCEPTER_DURABILITY = 250;
    public static final int SCEPTER_COOLDOWN = 20;
    public static final float SCEPTER_HEALTH_COST = 2.0F;
    public static final float SCEPTER_MIN_HEALTH = 4.0F;
    public static final float BOLT_SPEED = 1.2F;
    public static final double BOLT_CONE_DEGREES = 30.0;
    public static final double BOLT_RANGE = 24.0;
    public static final double BOLT_TURN_RATE = 0.15;
    public static final float BOLT_BASE_DAMAGE = 5.0F;
    public static final float BOLT_MISSING_HEALTH_DAMAGE = 10.0F;

    // ---------------------------------------------------------------- mobs (M9)
    public static final int SILK_GLOW_TICKS = 3600;
    public static final int SCENT_INTERVAL = 200;
    public static final int SCENT_WINDOW = 3600;
    public static final int SCENT_MAX_POINTS = 18;
    public static final int DECOY_LIFETIME = 300;
    public static final int DECOY_COOLDOWN = 400;

    // ---------------------------------------------------------------- forgotten land biomes (M10)
    public static final int UPDRAFT_INTERVAL = 4;
    public static final int UPDRAFT_HEIGHT = 40;
    public static final double UPDRAFT_ACCEL = 0.08;
    public static final double UPDRAFT_MAX = 0.4;
    public static final int FOOTPRINT_FADE_TICKS = 100;
    public static final int SPORE_INTERVAL = 20;
    public static final int SPORE_GLOW_TICKS = 60;
    public static final double SPORE_RADIUS = 6.0;
    public static final int BELL_COOLDOWN = 1200;
    public static final int BELL_RADIUS = 24;
    public static final int BELL_MAX_POSITIONS = 64;
    public static final int BELL_REVEAL_TICKS = 160;
    public static final int OBELISK_COOLDOWN = 24000;
    public static final float OBELISK_REWARD_CHANCE = 0.12F;
    public static final int OBELISK_LORE_LINES = 24;
    public static final int REFLECTION_DELAY = 60;
    public static final int REFLECTION_LEASH = 48;
    public static final int MERCHANT_SPAWN_INTERVAL = 72000;
    public static final float MERCHANT_SPAWN_CHANCE = 0.25F;
    public static final int MERCHANT_LIFETIME = 72000;
    public static final float MERCHANT_FRAGMENT_CHANCE = 0.08F;

    // ---------------------------------------------------------------- village civic system (M11)
    public static final int REP_MIN = -100;
    public static final int REP_MAX = 100;
    public static final int VILLAGE_MAX_VILLAGERS = 40;
    public static final int COUNCIL_PERIOD_DAYS = 7;
    public static final int GUILD_FEE = 20;
    public static final int GUILD_MIN_REP = 10;
    public static final int PARDON_COOLDOWN_DAYS = 3;
}
