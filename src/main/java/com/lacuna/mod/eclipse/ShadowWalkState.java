package com.lacuna.mod.eclipse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Player attachment while Shadow Walk is active: game mode to restore, end time and dash origin. */
public record ShadowWalkState(String previousMode, long until, long origin) {
    public static final ShadowWalkState NONE = new ShadowWalkState("", 0L, 0L);

    public static final Codec<ShadowWalkState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("previous_mode", "").forGetter(ShadowWalkState::previousMode),
            Codec.LONG.optionalFieldOf("until", 0L).forGetter(ShadowWalkState::until),
            Codec.LONG.optionalFieldOf("origin", 0L).forGetter(ShadowWalkState::origin)
    ).apply(i, ShadowWalkState::new));

    public boolean active() {
        return !previousMode.isEmpty();
    }
}
