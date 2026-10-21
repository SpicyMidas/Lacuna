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
 * <p>
 * {@code player}: a bag carrying a player ({@link BaggedPlayers}) only holds a token of the carry: the player's
 * {@code uuid} and {@code name}, the game time {@code until} which they stay in, their {@code stacks} of items and
 * the carry {@code session}. The player never leaves the world; a token whose carry is over empties itself.
 */
public record BagContents(String kind, CompoundTag data) {
    public static final String ENTITY = "entity";
    public static final String BLOCK = "block";
    public static final String PLAYER = "player";

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

    public boolean isPlayer() {
        return PLAYER.equals(kind);
    }
}
