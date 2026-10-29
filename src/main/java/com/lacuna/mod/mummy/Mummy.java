package com.lacuna.mod.mummy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A creature's Starlace wraps (attachment {@code lacuna:mummy}): how many bandages are on it and how many it takes, and
 * what it was like before, so unwrapping restores it exactly. Saved with the creature, partial wraps too (they loosen
 * and drop back as soon as it ticks again after {@link Mummification#PARTIAL_TICKS}, so a chunk unload or the Comically
 * Large Bag never eats a bandage). Synced to clients, which draw the wraps over finished mummies.
 *
 * @param wraps     bandages applied so far
 * @param needed    bandages it takes ({@link Mummification#wrapsNeeded})
 * @param wasNoAi   the creature's own NoAI flag before it was sealed
 * @param wasSilent the creature's own Silent flag before it was sealed
 * @param lastWrap  game time of the last bandage (partial wraps loosen {@link Mummification#PARTIAL_TICKS} after it)
 */
public record Mummy(int wraps, int needed, boolean wasNoAi, boolean wasSilent, long lastWrap) {
    public static final Mummy NONE = new Mummy(0, 1, false, false, 0L);

    public static final Codec<Mummy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("wraps").forGetter(Mummy::wraps),
            Codec.INT.fieldOf("needed").forGetter(Mummy::needed),
            Codec.BOOL.optionalFieldOf("was_no_ai", false).forGetter(Mummy::wasNoAi),
            Codec.BOOL.optionalFieldOf("was_silent", false).forGetter(Mummy::wasSilent),
            Codec.LONG.optionalFieldOf("last_wrap", 0L).forGetter(Mummy::lastWrap)).apply(i, Mummy::new));

    public static final StreamCodec<ByteBuf, Mummy> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Mummy::wraps, ByteBufCodecs.VAR_INT, Mummy::needed, ByteBufCodecs.BOOL, Mummy::wasNoAi,
            ByteBufCodecs.BOOL, Mummy::wasSilent, ByteBufCodecs.VAR_LONG, Mummy::lastWrap, Mummy::new);

    /** Fully wrapped: frozen, silent and saved. */
    public boolean done() {
        return wraps > 0 && wraps >= needed;
    }

    public Mummy wrapped(long now) {
        return new Mummy(wraps + 1, needed, wasNoAi, wasSilent, now);
    }
}
