package com.lacuna.mod.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * CLIENT config (visual preferences only; never read on a dedicated server). Documented in docs/CONFIG.md.
 * Each feature adds its values in its own section below; read them through {@link #get}.
 */
public final class LacunaClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- effects
    public static final ModConfigSpec.BooleanValue SCREEN_EFFECTS;

    static {
        B.push("effects");
        SCREEN_EFFECTS = B.comment("Camera shake, flashes and screen tints from Lacuna items (also scaled by the vanilla Distortion Effects slider).")
                .define("screenEffects", true);
        B.pop();
    }

    // ---------------------------------------------------------------- solar (Sun's Fury, Divine Magnifying Glass)
    public static final ModConfigSpec.BooleanValue SOLAR_SKY;

    static {
        B.push("solar");
        SOLAR_SKY = B.comment("While Sun's Fury focuses or strikes, the sun flares in the sky and the fog warms.")
                .define("sunFlare", true);
        B.pop();
    }

    // ---------------------------------------------------------------- forgotten land atmosphere
    public static final ModConfigSpec.IntValue FL_FOG_DISTANCE;
    public static final ModConfigSpec.IntValue FL_MARSH_FOG_DISTANCE;
    public static final ModConfigSpec.BooleanValue FL_MIST;

    static {
        B.push("forgottenLand");
        FL_FOG_DISTANCE = B.comment("Fog far plane (blocks) everywhere in the Forgotten Land. Lower = thicker fog.")
                .defineInRange("fogDistance", 24, 8, 512);
        FL_MARSH_FOG_DISTANCE = B.comment("Fog far plane (blocks) in the Whispering Marsh.")
                .defineInRange("marshFogDistance", 14, 6, 512);
        FL_MIST = B.comment("Drifting mist wisps near the ground in the Forgotten Land.").define("mist", true);
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private LacunaClientConfig() {}

    /** Safe read: the default when the config is not loaded yet. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }
}
