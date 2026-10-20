package com.lacuna.mod.bag;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Comically Large Bag holds: exactly one entity (its full save data, id and UUID included, passengers nested)
 * or exactly one block ({@code state}, the {@code block_entity} saved with full metadata and the {@code facing} of
 * the player who bagged it). The tag is never mutated once stored; readers copy it.
 */
public record BagContents(String kind, CompoundTag data) {
    public static final String ENTITY = "entity";
    public static final String BLOCK = "block";

    public static final Codec<BagContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(BagContents::kind),
            CompoundTag.CODEC.fieldOf("data").forGetter(BagContents::data)
    ).apply(i, BagContents::new));

    public static final StreamCodec<ByteBuf, BagContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BagContents::kind,
            ByteBufCodecs.COMPOUND_TAG, BagContents::data,
            BagContents::new);

    public boolean isEntity() {
        return ENTITY.equals(kind);
    }

    public boolean isBlock() {
        return BLOCK.equals(kind);
    }
}
