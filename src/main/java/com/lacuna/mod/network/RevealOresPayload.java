package com.lacuna.mod.network;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: ore positions revealed by the Sunken Bell (at most 64), outlined for {@code durationTicks}. */
public record RevealOresPayload(List<BlockPos> positions, int durationTicks) implements CustomPacketPayload {
    public static final Type<RevealOresPayload> TYPE = new Type<>(Lacuna.id("reveal_ores"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RevealOresPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(Balance.BELL_MAX_POSITIONS)), RevealOresPayload::positions,
            ByteBufCodecs.VAR_INT, RevealOresPayload::durationTicks,
            RevealOresPayload::new);

    public RevealOresPayload {
        positions = List.copyOf(positions.size() > Balance.BELL_MAX_POSITIONS ? positions.subList(0, Balance.BELL_MAX_POSITIONS) : positions);
        durationTicks = Math.max(1, Math.min(20 * 60, durationTicks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
