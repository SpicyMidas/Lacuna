package com.lacuna.mod.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Guild contract component: deliver {@code count} of {@code itemId} for {@code rewardCoins} (spec 18.4). */
public record ContractData(String itemId, int count, int rewardCoins, String villageId, String guild) {
    public static final ContractData EMPTY = new ContractData("minecraft:air", 0, 0, "", "");

    public static final Codec<ContractData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(ContractData::itemId),
            Codec.intRange(0, 999).fieldOf("count").forGetter(ContractData::count),
            Codec.intRange(0, 999).fieldOf("reward_coins").forGetter(ContractData::rewardCoins),
            Codec.STRING.optionalFieldOf("village", "").forGetter(ContractData::villageId),
            Codec.STRING.optionalFieldOf("guild", "").forGetter(ContractData::guild)
    ).apply(i, ContractData::new));

    public static final StreamCodec<ByteBuf, ContractData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ContractData::itemId,
            ByteBufCodecs.VAR_INT, ContractData::count,
            ByteBufCodecs.VAR_INT, ContractData::rewardCoins,
            ByteBufCodecs.STRING_UTF8, ContractData::villageId,
            ByteBufCodecs.STRING_UTF8, ContractData::guild,
            ContractData::new);

    public boolean isEmpty() {
        return count <= 0;
    }
}
