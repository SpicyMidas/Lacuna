package com.lacuna.mod.pearl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Data component carried by both halves of a Fractured Pearl: the shared link id and a "cracked" display flag. */
public record PearlLink(UUID id, boolean cracked) {
    public static final Codec<PearlLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(PearlLink::id),
            Codec.BOOL.optionalFieldOf("cracked", false).forGetter(PearlLink::cracked)
    ).apply(i, PearlLink::new));

    public static final StreamCodec<ByteBuf, PearlLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PearlLink::id,
            ByteBufCodecs.BOOL, PearlLink::cracked,
            PearlLink::new);

    /** Short display form: first 8 hex digits as ABCD-1234. */
    public String shortId() {
        String hex = id.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return hex.substring(0, 4) + "-" + hex.substring(4);
    }

    public PearlLink withCracked(boolean value) {
        return new PearlLink(id, value);
    }
}
