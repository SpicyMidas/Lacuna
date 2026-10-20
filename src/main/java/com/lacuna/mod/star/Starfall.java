package com.lacuna.mod.star;

import com.lacuna.mod.Lacuna;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * Starcaller Trumpet / falling star tuning and shared ids. Kept here (not in the shared Balance) so the feature stays
 * self-contained: {@link StarcallerTrumpetItem} calls the star, {@link FallingStarEntity} flies and strikes,
 * {@link FallenStarBlock} is what it leaves behind.
 */
public final class Starfall {
    /** SafeRun feature name (server and client). */
    public static final String FEATURE = "starfall";
    /** FxPayload kinds (client/star/StarClient). */
    public static final String FX_IMPACT = "starfall_impact";
    public static final String ADVANCEMENT = "lacuna/wish_upon_a_star";
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Lacuna.id("falling_star"));

    // ------------------------------------------------------------------ the trumpet
    public static final int TRUMPET_DURABILITY = 32;
    /** 30 s between calls. */
    public static final int COOLDOWN = 600;
    /** How long the trumpet is held to the lips (the toot animation). */
    public static final int USE_TICKS = 30;
    /** Reach of the call: the block or creature under the crosshair, up to this far. */
    public static final double RANGE = 96.0;
    /** Extra pick radius around creatures, so distant targets are easier to lock on. */
    public static final double PICK_LENIENCY = 0.35;

    // ------------------------------------------------------------------ the star's flight
    /** The star twinkles into being in the sky while the fanfare plays, then falls. */
    public static final int APPEAR_TICKS = 16;
    /** Duration of the fall itself (2.5 s). */
    public static final int FALL_TICKS = 50;
    public static final double HEIGHT_MIN = 62.0;
    public static final double HEIGHT_MAX = 76.0;
    /** Horizontal offset of the starting point, so the star comes in at an angle. */
    public static final double SLANT_MIN = 22.0;
    public static final double SLANT_MAX = 34.0;
    /** Fraction of the path speed at the start of the fall (the rest is acceleration). */
    public static final double START_SPEED = 0.35;

    // ------------------------------------------------------------------ the impact
    public static final float BLAST_RADIUS = 2.5F;
    /** Damage at the centre; it falls off linearly to a quarter of this at the edge of the blast. */
    public static final float BLAST_DAMAGE = 8.0F;
    public static final double KNOCKBACK = 1.1;
    public static final float BURN_SECONDS = 3.0F;
    /** Crater half-width (only when mobGriefing is on); blocks tougher than {@link #CRATER_MAX_RESISTANCE} survive. */
    public static final double CRATER_RADIUS = 1.75;
    public static final float CRATER_MAX_RESISTANCE = 9.0F;
    public static final int FIRES = 3;
    /** Players this close feel the impact (camera shake); the flash is seen closer still. */
    public static final double SHAKE_RANGE = 48.0;

    // ------------------------------------------------------------------ natural stars ({@link NaturalStarfall})
    /** SafeRun feature name of the natural event (a fault there leaves the trumpet working). */
    public static final String NATURAL_FEATURE = "natural_starfall";
    public static final String NATURAL_ADVANCEMENT = "lacuna/shooting_star";
    /** Damage type of a star nobody called (its death message names no caller). */
    public static final ResourceKey<DamageType> NATURAL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Lacuna.id("shooting_star"));
    /** Default chance, per player and per clear night, that a star falls near them (config: starfall.naturalChancePerNight). */
    public static final double NATURAL_CHANCE_PER_NIGHT = 0.3;
    /** Players are rolled for this often (ticks) while the night is clear. */
    public static final int NATURAL_CHECK_INTERVAL = 100;
    /** The dark part of the night (time of day), when stars can be seen. */
    public static final long NIGHT_FROM = 13500L;
    public static final long NIGHT_TO = 22500L;
    /** Never on the world's first night: the world must be this old (game ticks). */
    public static final long NATURAL_GRACE = 24000L;
    /** At least this long (ticks) between two natural stars in the world. */
    public static final int NATURAL_SPACING = 1200;
    /** The star lands this far from the player it falls for, and at least the minimum from every player. */
    public static final double NATURAL_MIN_DISTANCE = 24.0;
    public static final double NATURAL_MAX_DISTANCE = 64.0;
    /** The player must be within this many blocks under the surface (in a house: yes; down a mine: no). */
    public static final int NATURAL_SURFACE_DEPTH = 10;
    /** Landing spots tried per roll. */
    public static final int NATURAL_ATTEMPTS = 12;
    /** No beds, workstations or other points of interest this close to the landing spot, and no block entities. */
    public static final int NATURAL_CLEARANCE = 12;
    public static final int NATURAL_BLOCK_ENTITY_CLEARANCE = 8;
    /** A natural star comes from higher and further out than a called one: a long streak across the sky. */
    public static final double NATURAL_HEIGHT_MIN = 90.0;
    public static final double NATURAL_HEIGHT_MAX = 110.0;
    public static final double NATURAL_SLANT_MIN = 40.0;
    public static final double NATURAL_SLANT_MAX = 64.0;
    /** Players this close to the landing spot are told where the star fell. */
    public static final double NATURAL_HINT_RANGE = 160.0;

    private Starfall() {}

    /** Eased progress of the fall at {@code p} in 0..1: starts at {@link #START_SPEED} of the mean speed and accelerates. */
    public static double ease(double p) {
        p = Math.max(0.0, Math.min(1.0, p));
        return START_SPEED * p + (1.0 - START_SPEED) * p * p;
    }

    /** Blast damage at {@code distance} from the centre (0 outside the blast). */
    public static float damageAt(double distance) {
        if (distance >= BLAST_RADIUS) {
            return 0.0F;
        }
        return BLAST_DAMAGE * (float) (1.0 - 0.75 * distance / BLAST_RADIUS);
    }
}
