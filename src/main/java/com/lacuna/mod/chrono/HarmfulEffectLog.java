package com.lacuna.mod.chrono;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;

/** Player attachment: effect id -> game time it was last applied (to clear harmful effects of the last 10 s). */
public record HarmfulEffectLog(Map<String, Long> applied) {
    public static final HarmfulEffectLog EMPTY = new HarmfulEffectLog(Map.of());
    public static final Codec<HarmfulEffectLog> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
            .xmap(HarmfulEffectLog::new, HarmfulEffectLog::applied);

    public HarmfulEffectLog with(String effect, long time, long now, long window) {
        Map<String, Long> copy = new HashMap<>();
        applied.forEach((k, v) -> {
            if (now - v <= window) {
                copy.put(k, v);
            }
        });
        copy.put(effect, time);
        return new HarmfulEffectLog(Map.copyOf(copy));
    }
}
