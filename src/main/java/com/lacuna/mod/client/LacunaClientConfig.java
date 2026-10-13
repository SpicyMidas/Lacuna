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

    // ---------------------------------------------------------------- eclipser (dash effects)
    public static final ModConfigSpec.BooleanValue ECLIPSE_AFTERIMAGES;

    static {
        B.push("eclipser");
        ECLIPSE_AFTERIMAGES = B.comment("Dark afterimages of the dashing player along the Eclipser dash.")
                .define("afterimages", true);
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
