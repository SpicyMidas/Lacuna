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
