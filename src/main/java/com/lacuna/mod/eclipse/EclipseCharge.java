package com.lacuna.mod.eclipse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Eclipser data component: charge 0..100 and the game time of the last charging hit. */
public record EclipseCharge(int charge, long lastHit) {
    public static final EclipseCharge EMPTY = new EclipseCharge(0, 0L);

    public static final Codec<EclipseCharge> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 100).optionalFieldOf("charge", 0).forGetter(EclipseCharge::charge),
            Codec.LONG.optionalFieldOf("last_hit", 0L).forGetter(EclipseCharge::lastHit)
    ).apply(i, EclipseCharge::new));

    public static final StreamCodec<ByteBuf, EclipseCharge> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EclipseCharge::charge,
            ByteBufCodecs.VAR_LONG, EclipseCharge::lastHit,
            EclipseCharge::new);
}
