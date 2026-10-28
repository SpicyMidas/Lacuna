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

    // ---------------------------------------------------------------- divine sight (M1)
    public static final int DIVINE_SIGHT_RANGE = 12;
    public static final int DIVINE_SIGHT_GLASS_DISTANCE = 6;
    public static final double DIVINE_SIGHT_CONE_DEGREES = 50.0;
    public static final int DIVINE_SIGHT_SCAN_INTERVAL = 10;
    public static final int DIVINE_SIGHT_MAX_POSITIONS = 4096;
    public static final int DIVINE_SIGHT_MAX_BOXES = 128;
    public static final int DIVINE_SIGHT_MAX_ENTITY_BOXES = 32;

    // ---------------------------------------------------------------- totem of banishing (M2)
    /** Total uses: the totem cannot be repaired, so every crossing and every banishment counts. */
    public static final int TOTEM_DURABILITY = 6;
    /** Cooldown after banishing another creature or player (15 s). */
    public static final int TOTEM_COOLDOWN = 300;
    /** Cooldown after crossing yourself, either way (4 s). */
    public static final int TOTEM_SELF_COOLDOWN = 80;
    public static final double TOTEM_KNOCKBACK = 1.5;

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

    // ---------------------------------------------------------------- umbral steel / eclipser (M4)
    public static final int ECLIPSER_DURABILITY = 250;
    public static final int ECLIPSER_ATTACK_DAMAGE = 8; // including base 1 (the tooltip shows 8)
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
    public static final float ECLIPSE_DAMAGE_BASE = 12.0F;
    public static final float ECLIPSE_DAMAGE_PER_CHARGE = 0.18F;
    public static final float ECLIPSE_DAMAGE_MAX = 30.0F;
    public static final int ECLIPSE_DURABILITY_BASE = 5;
    public static final int ECLIPSE_DURABILITY_SCALE = 15;
    public static final int ECLIPSE_COOLDOWN = 400;
    public static final int SHADOW_WALK_BASE_TICKS = 40;
    public static final int SHADOW_WALK_SCALE_TICKS = 80;

    // ---------------------------------------------------------------- sun's fury (M4)
    public static final float SUNS_FURY_SPEED = 2.5F;
    public static final double SUNS_FURY_GRAVITY = 0.03;
    public static final int SUNS_FURY_FUSE = 200;
    /** Trident-style throw: minimum draw (ticks) and the draw at which the full speed is reached. */
    public static final int SUNS_FURY_MIN_CHARGE = 10;
    public static final int SUNS_FURY_FULL_CHARGE = 25;
    /** Impact damage when the spear itself hits a creature (a trident deals 8). */
    public static final float SUNS_FURY_IMPACT_DAMAGE = 8.0F;
    /** Focus phase between landing and the strike: the sun climbs to noon and the light converges. */
    public static final int SUNS_FURY_FOCUS_TICKS = 100;
    /** Fade phase after the strike: purely visual. */
    public static final int SOLAR_FADE_TICKS = 20;
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
    public static final int LANTERN_TICKS_PER_DURABILITY = 200;
    /** Mobs hunting a protected player are swept from this far away (or the config radius, if larger). */
    public static final int LANTERN_SWEEP_RADIUS = 32;
    /** A lit placed lantern shelters every player within this many blocks of it. */
    public static final int LANTERN_PLACED_RADIUS = 12;
    public static final int LANTERN_LIGHT = 13;
    /** Charge restored by one fuel item, in percent of the maximum. */
    public static final int LANTERN_PEARL_RECHARGE_PERCENT = 50;
    public static final int LANTERN_ECHO_RECHARGE_PERCENT = 100;

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

    // ---------------------------------------------------------------- gear (M7)
    /** Umbral Steel outclasses Ruby: netherite mining level, 1500 uses, speed 8.5, +4 damage, enchantability 15. */
    public static final int UMBRAL_TOOL_DURABILITY = 1500;
    public static final float UMBRAL_TOOL_SPEED = 8.5F;
    public static final float UMBRAL_TOOL_DAMAGE_BONUS = 4.0F;
    public static final int UMBRAL_TOOL_ENCHANTABILITY = 15;
    /** Eclipser keeps the old Umbral mining speed (it is a sword; the value only matters for its tier record). */
    public static final float ECLIPSER_TOOL_SPEED = 6.5F;
    /** Sword: +3 over the tier (1 + 4 + 3 = 8 damage, 1.6 speed). Axe: +5 over the tier (10 damage, 1.0 speed). */
    public static final int UMBRAL_SWORD_DAMAGE = 3;
    public static final float UMBRAL_SWORD_SPEED = -2.4F;
    public static final float UMBRAL_AXE_DAMAGE = 5.0F;
    public static final float UMBRAL_AXE_SPEED = -3.0F;
    /** Umbral Rend (sword): the 3rd hit on the same target within 80 ticks detonates. */
    public static final int UMBRAL_REND_HITS = 3;
    public static final int UMBRAL_REND_WINDOW = 80;
    public static final float UMBRAL_REND_DAMAGE = 6.0F;
    public static final float UMBRAL_REND_HEAL = 2.0F;
    public static final int UMBRAL_REND_DEBUFF_TICKS = 40;
    /** Eclipse Cleave (axe): fully charged hits deal 60 % of the hit to other mobs within 3 blocks of the target. */
    public static final float UMBRAL_CLEAVE_MIN_CHARGE = 0.9F;
    public static final double UMBRAL_CLEAVE_RADIUS = 3.0;
    public static final float UMBRAL_CLEAVE_FACTOR = 0.6F;
    public static final double UMBRAL_CLEAVE_KNOCKBACK = 0.7;
    /** Nightforged (every Umbral Steel tool): +30 % mining speed where the light at the eyes is <= 7. */
    public static final float UMBRAL_NIGHTFORGED_BONUS = 0.3F;
    public static final int UMBRAL_NIGHTFORGED_LIGHT = 7;
    public static final float UMBRAL_DARK_BONUS = 0.25F;
    public static final int UMBRAL_DARK_LIGHT = 4;

    public static final int STARLACE_DURABILITY_FACTOR = 24;
    public static final int STARLACE_ENCHANTABILITY = 18;
    public static final float STARLACE_TOUGHNESS = 0.5F;
    public static final float STARLACE_GLIDE_FALL = 4.0F;
    public static final int STARLACE_GLIDE_TICKS = 80;
    public static final int STARLACE_GLIDE_COOLDOWN = 200;
    /** Constellation Step (full set): a second jump in mid-air, once per airtime, at this fraction of the jump strength. */
    public static final float STARLACE_STEP_JUMP_FACTOR = 1.1F;
    /** Horizontal push added along the movement direction by Constellation Step (blocks/tick). */
    public static final double STARLACE_STEP_FORWARD = 0.22;
    /** Horizontal speed carried into the step from the current movement, and the cap after the push (blocks/tick). */
    public static final double STARLACE_STEP_MAX_CARRY = 0.5;
    public static final double STARLACE_STEP_MAX_HORIZONTAL = 0.62;
    /** Minimum ticks between two Constellation Steps (a guard against packet spam; the airtime rule does the rest). */
    public static final int STARLACE_STEP_COOLDOWN = 8;
    public static final float STARLACE_STEP_EXHAUSTION = 0.1F;
    /** Starfall: fall damage multiplier with 2+ pieces; with the full set a fall over the threshold becomes a shockwave. */
    public static final int STARLACE_HALF_FALL_PIECES = 2;
    public static final float STARLACE_FALL_DAMAGE_FACTOR = 0.5F;
    public static final float STARLACE_STARFALL_FALL = 5.0F;
    public static final double STARLACE_STARFALL_RADIUS = 3.5;
    public static final float STARLACE_STARFALL_DAMAGE = 4.0F;
    public static final double STARLACE_STARFALL_KNOCKBACK = 0.9;
    /** Starlit: movement speed per worn piece at night under the open sky (or anywhere in the End), re-checked every second. */
    public static final double STARLACE_STARLIT_SPEED = 0.05;
    public static final int STARLACE_STARLIT_INTERVAL = 20;

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
    public static final int SICKLE_MAX_TIDE = 3;
    /** Tide charges trickle in while the wielder stands in water: one every this many ticks. */
    public static final int SICKLE_TIDE_WATER_TICKS = 200;
    /** Melee: bonus damage while the wielder is in water or rain; the target is drenched (Slowness I) for this long. */
    public static final float SICKLE_TIDE_BONUS = 3.0F;
    public static final int SICKLE_DRENCH_TICKS = 40;
    /** Tidal Wave (use): a rolling wall of water. Front speed in blocks per tick (14 blocks in about one second). */
    public static final int SICKLE_WAVE_COOLDOWN = 60;
    public static final int SICKLE_WAVE_DURABILITY = 2;
    public static final double SICKLE_WAVE_RANGE = 14.0;
    public static final double SICKLE_WAVE_SPEED = 0.7;
    public static final double SICKLE_WAVE_HALF_WIDTH = 2.5;
    public static final double SICKLE_WAVE_HEIGHT = 2.5;
    public static final float SICKLE_WAVE_DAMAGE = 4.0F;
    /** Maelstrom (sneak + use at full tide): a whirlpool that drowns, then a geyser. */
    public static final int SICKLE_MAELSTROM_COOLDOWN = 300;
    public static final int SICKLE_MAELSTROM_DURABILITY = 5;
    public static final double SICKLE_MAELSTROM_REACH = 24.0;
    public static final double SICKLE_MAELSTROM_FALLBACK = 8.0;
    public static final double SICKLE_MAELSTROM_RADIUS = 7.0;
    public static final int SICKLE_MAELSTROM_TICKS = 100;
    public static final float SICKLE_MAELSTROM_DAMAGE = 1.5F;
    /** Upward speed of the closing geyser (about 6-8 blocks of rise). */
    public static final double SICKLE_GEYSER_LAUNCH = 1.15;

    public static final int HEARTWOOD_DURABILITY = 480;
    public static final int ROOTED_TICKS = 40;
    public static final int ROOTED_PINNED_TICKS = 80;
    /** Grasping Roots (Heartwood Bow): lifetime around a hit target / on terrain, final withering stage, per-hit cap. */
    public static final int GRASPING_ROOTS_TARGET_TICKS = 120;
    public static final int GRASPING_ROOTS_SPROUT_TICKS = 200;
    public static final int GRASPING_ROOTS_WITHER_TICKS = 20;
    public static final int GRASPING_ROOTS_PER_HIT = 5;
    /** Rooted duration re-applied every tick to a living entity standing in grasping roots. */
    public static final int GRASPING_ROOTS_HOLD_TICKS = 10;

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

    // ---------------------------------------------------------------- ember triangle (thermal wave relic)
    /** Total uses; repaired at an anvil with Ember, {@link #EMBER_TRIANGLE_REPAIR_PER_EMBER} uses per Ember. */
    public static final int EMBER_TRIANGLE_DURABILITY = 16;
    public static final int EMBER_TRIANGLE_REPAIR_PER_EMBER = 4;
    /** Cooldown after a thermal wave (60 s). */
    public static final int EMBER_TRIANGLE_COOLDOWN = 1200;
    /** The triangle flares for this many ticks before the wave is released. */
    public static final int EMBER_TRIANGLE_WINDUP = 6;
    /** The wave front runs this far along the ground (blocks) in {@link #EMBER_TRIANGLE_WAVE_TICKS}, slowing as it spreads. */
    public static final double EMBER_TRIANGLE_RADIUS = 10.0;
    public static final int EMBER_TRIANGLE_WAVE_TICKS = 12;
    /** The front catches what stands up to this far above / below the ground it runs on. */
    public static final double EMBER_TRIANGLE_REACH_UP = 2.5;
    public static final double EMBER_TRIANGLE_REACH_DOWN = 1.0;
    /** Bosses (tag lacuna:cannot_be_banished) are scorched for this much instead of being pulverized. */
    public static final float EMBER_TRIANGLE_BOSS_DAMAGE = 30.0F;

    // ---------------------------------------------------------------- Wand of Quickening (animated items)
    public static final int QUICKENING_DURABILITY = 64;
    public static final int QUICKENING_COOLDOWN = 40;
    public static final float QUICKENING_HEALTH_COST = 2.0F;
    /** Refused at or below this health (like the Pyrrhic Scepter); free in creative. */
    public static final float QUICKENING_MIN_HEALTH = 4.0F;
    public static final double QUICKENING_RANGE = 16.0;
    /** Generous pick radius around the view ray for dropped items. */
    public static final double QUICKENING_PICK_RADIUS = 0.8;
    /** Animated items alive per player; the oldest falls asleep early when a new one wakes. */
    public static final int QUICKENING_MAX_ALIVE = 3;
    public static final int ANIMATED_LIFETIME = 800;
    /** Legs sprout over the first ticks of life... */
    public static final int ANIMATED_WAKE_TICKS = 8;
    /** ...and shrink away over the last ones (also the delay of an early sleep). */
    public static final int ANIMATED_SLEEP_TICKS = 10;
    public static final float ANIMATED_MAX_HEALTH = 16.0F;
    public static final double ANIMATED_SPEED = 0.3;
    public static final double ANIMATED_HUNT_RANGE = 16.0;
    public static final double ANIMATED_TELEPORT_DISTANCE = 24.0;
    public static final double ANIMATED_FOLLOW_START = 4.0;
    public static final double ANIMATED_FOLLOW_STOP = 2.0;
    /** Blades and other weapons strike with their own damage times this. */
    public static final float ANIMATED_WEAPON_MULTIPLIER = 1.25F;
    /** Swords also sweep foes next to the target for 1 + this fraction of the hit. */
    public static final float ANIMATED_SWEEP_FRACTION = 0.25F;
    public static final float ANIMATED_SLAM_DAMAGE = 2.0F;
    /** Block items slam harder the harder the block: + hardness x 0.8, at most this. */
    public static final float ANIMATED_SLAM_HARDNESS_BONUS = 4.0F;
    public static final int ANIMATED_IGNITE_SECONDS = 4;
    public static final double ANIMATED_ARCHER_RANGE = 8.0;
    public static final double ANIMATED_THROWER_RANGE = 6.0;
    public static final int ANIMATED_SHOT_INTERVAL = 30;
    public static final int ANIMATED_TRIDENT_INTERVAL = 40;
    public static final int ANIMATED_THROW_MIN_INTERVAL = 40;
    public static final int ANIMATED_THROW_MAX_INTERVAL = 80;
    /** Beneficial splash/lingering potions are thrown at the owner, at most this often. */
    public static final int ANIMATED_POTION_OWNER_INTERVAL = 120;
    public static final float ANIMATED_TNT_RADIUS = 3.0F;
    public static final int ANIMATED_TNT_FUSE = 30;
    public static final int ANIMATED_TNT_REARM = 40;
    public static final double ANIMATED_SHIELD_TAUNT_RANGE = 8.0;
    public static final int ANIMATED_SHIELD_TAUNT_INTERVAL = 60;
    public static final double ANIMATED_SHIELD_ARMOR = 12.0;
    public static final double ANIMATED_SHIELD_KNOCKBACK_RESISTANCE = 0.6;
    public static final int ANIMATED_FEED_INTERVAL = 100;
    public static final int ANIMATED_BLINK_INTERVAL = 60;
    public static final int ANIMATED_LEAP_WINDUP = 3;

    // ------------------------------------------------------------------ Chrono Hourglass (chrono/ChronoHourglassBlockEntity)
    /** Sand time bought by one Chronal Shard (ticks). */
    public static final int HOURGLASS_TICKS_PER_SHARD = 1200;
    /** Most time the hourglass can hold (ticks). */
    public static final int HOURGLASS_MAX_TICKS = 12000;
    /** Local tick speed multiplier while the sand runs (4 = block entities tick 3 extra times per tick). */
    public static final int HOURGLASS_SPEED = 4;
    /** Reach around the hourglass: horizontal radius and vertical reach above/below its two blocks. */
    public static final int HOURGLASS_RADIUS = 4;
    public static final int HOURGLASS_VERTICAL = 3;
    /** At most this many ticking block entities are sped up by one hourglass. */
    public static final int HOURGLASS_MAX_BLOCK_ENTITIES = 48;
    /** Block entities in reach are looked up again this often (ticks). */
    public static final int HOURGLASS_SCAN_INTERVAL = 20;
    /** Upper bound on extra random ticks per game tick (guards against a huge randomTickSpeed gamerule). */
    public static final int HOURGLASS_MAX_RANDOM_TICKS = 32;
    /** Players in reach heal (natural regeneration) and shake off harmful effects this many times faster. */
    public static final int HOURGLASS_PLAYER_SPEED = 2;
    /** Scheduled fluid ticks in reach (water and lava spreading) come this many times sooner. */
    public static final int HOURGLASS_FLUID_SPEED = 2;
    /** Scheduled block ticks in reach (fire, falling blocks, frogspawn, sniffer eggs...; never redstone) come this many times sooner. */
    public static final int HOURGLASS_SCHEDULED_SPEED = 2;
    /** At most this many scheduled ticks are brought forward by one hourglass per game tick. */
    public static final int HOURGLASS_MAX_SCHEDULED_TICKS = 48;
    /** Chunks holding more scheduled ticks than this are not searched (a huge contraption must not cost every tick). */
    public static final int HOURGLASS_MAX_TICK_QUEUE = 4096;

    // ---------------------------------------------------------------- eclipser dash air wake
    /** Vertical margin added to the dasher's box when sweeping the dash for victims (horizontal reach: ECLIPSE_DASH_RADIUS). */
    public static final double ECLIPSE_DASH_VERTICAL_MARGIN = 0.6;
    /** Sideways speed (blocks/tick) given to a cut creature: base + per block/tick of dash speed + extra at full charge. */
    public static final double ECLIPSE_SHOVE_BASE = 0.35;
    public static final double ECLIPSE_SHOVE_PER_SPEED = 0.18;
    public static final double ECLIPSE_SHOVE_PER_CHARGE = 0.25;
    /** Upward lift of a cut creature, plus the extra at full charge. */
    public static final double ECLIPSE_SHOVE_LIFT = 0.28;
    public static final double ECLIPSE_SHOVE_LIFT_PER_CHARGE = 0.12;
    /** Drag along the dash direction, as a fraction of the sideways shove. */
    public static final double ECLIPSE_SHOVE_DRAG = 0.3;
    /** Bosses only feel this fraction of the shove. */
    public static final double ECLIPSE_SHOVE_BOSS = 0.25;
    /** Width of the air wake beside the cut zone: creatures there are pushed aside without damage. */
    public static final double ECLIPSE_GUST_WIDTH = 1.75;
    /** Wake push as a fraction of the shove at the edge of the cut zone (fades to 30% of that at the outer edge). */
    public static final double ECLIPSE_GUST_FACTOR = 0.55;
    public static final double ECLIPSE_GUST_LIFT = 0.12;
    // ------------------------------------------------------------------ Comically Large Bag: players (bag/BaggedPlayers)
    /** How long a bagged player stays in the bag before wriggling free on their own (ticks). */
    public static final int BAG_PLAYER_TICKS = 300;
    /** After getting out, nobody can bag that player again for this long (ticks). */
    public static final int BAG_PLAYER_COOLDOWN_TICKS = 600;
    /** Each struggle (jump, attack or sneak press inside the bag) shaves this much off the time left (ticks). */
    public static final int BAG_STRUGGLE_TICKS = 5;
    /** At most one struggle counts per this many ticks (no macro speedruns out of the bag). */
    public static final int BAG_STRUGGLE_INTERVAL = 4;
    // ---------------------------------------------------------------- shadow possession (Shadow Walk)
    /** A shadow walker possesses a creature or player within this many blocks of its eyes. */
    public static final double POSSESS_REACH = 8.0;
    /** Struggle points at which the host fights back and the expulsion countdown starts. */
    public static final float POSSESS_FIGHT_BACK = 20.0F;
    /** Once a creature fights back you have this long (ticks) before it casts you out. */
    public static final int POSSESS_EXPEL_TICKS_MOB = 100;
    /** Once a player fights back you have this long (ticks); every tick they keep struggling it runs twice as fast. */
    public static final int POSSESS_EXPEL_TICKS_PLAYER = 80;
    /** No body holds a shadow longer than this (ticks): then it fights back whatever it is. */
    public static final int POSSESS_MAX_TICKS = 600;
    /** A possessed player's own movement keys only add this fraction on top of yours (your controls win). */
    public static final float POSSESS_PLAYER_OWN_INPUT = 0.25F;
    /** Per-tick fraction by which a possessed player's view is pulled back to where you look. */
    public static final float POSSESS_LOOK_PULL = 0.55F;
    /** Creature will (struggle per tick, randomised 0.5..1.5x): base, extra when hostile, per max-health point (capped). */
    public static final float POSSESS_MOB_WILL_BASE = 0.06F;
    public static final float POSSESS_MOB_WILL_HOSTILE = 0.12F;
    public static final float POSSESS_MOB_WILL_PER_HEALTH = 0.004F;
    public static final float POSSESS_MOB_WILL_HEALTH_CAP = 0.25F;
    /** Extra will of a creature below half health; a pet of the possessor itself resists only this fraction as much. */
    public static final float POSSESS_MOB_WILL_WOUNDED = 0.1F;
    public static final float POSSESS_MOB_WILL_OWN_PET = 0.5F;
    /** Struggle gained per point of damage the host takes (pain helps it break free). */
    public static final float POSSESS_PAIN_STRUGGLE = 2.5F;
    /** Struggle points a fighting player can report per tick at most, and what a refused strike or use is worth. */
    public static final float POSSESS_PLAYER_STRUGGLE_MAX = 6.0F;
    public static final float POSSESS_ACTION_STRUGGLE = 4.0F;
    /** Struggle decays by this per tick, so stray twitches never add up. */
    public static final float POSSESS_STRUGGLE_DECAY = 0.12F;
    /** A possessed creature walks at its movement speed times this, clamped to [min, max] blocks per tick. */
    public static final double POSSESS_MOB_SPEED_SCALE = 0.75;
    public static final double POSSESS_MOB_SPEED_MIN = 0.08;
    public static final double POSSESS_MOB_SPEED_MAX = 0.2;
    /** Sprinting multiplies that speed by this. */
    public static final double POSSESS_SPRINT = 1.35;
    /** Ticks between the strikes / ranged attacks you make a host perform, and the ranged attack range. */
    public static final int POSSESS_MELEE_COOLDOWN = 12;
    public static final int POSSESS_RANGED_COOLDOWN = 25;
    public static final double POSSESS_RANGED_RANGE = 32.0;
    /** After being cast out you cannot possess again for this long (ticks); after leaving on your own, briefly. */
    public static final int POSSESS_EXPEL_COOLDOWN = 80;
    public static final int POSSESS_LEAVE_COOLDOWN = 10;
    /** A player / creature that cast a shadow out cannot be possessed again for this long (ticks). */
    public static final int POSSESS_HOST_IMMUNITY_PLAYER = 300;
    public static final int POSSESS_HOST_IMMUNITY_MOB = 60;
    /** A cast-out shadow is thrown this far out of the host's back. */
    public static final double POSSESS_EXPEL_DISTANCE = 3.5;
    /** The Shadow Walk clock stands still while possessing; it resumes afterwards with at least this many ticks. */
    public static final int POSSESS_RESUME_MIN_TICKS = 30;
}
