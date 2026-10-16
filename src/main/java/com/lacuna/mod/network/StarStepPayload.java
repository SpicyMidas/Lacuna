package com.lacuna.mod.network;

import com.lacuna.mod.Lacuna;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the local player pressed jump again in mid-air while wearing the full Starlace set (Constellation
 * Step). Carries only the horizontal direction of the movement keys in world space (a unit vector or zero); the server
 * re-checks everything else and decides the motion itself (see {@code gear/StarlaceSet}).
 */
public record StarStepPayload(float dirX, float dirZ) implements CustomPacketPayload {
    public static final Type<StarStepPayload> TYPE = new Type<>(Lacuna.id("star_step"));
    public static final StreamCodec<ByteBuf, StarStepPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StarStepPayload::dirX,
            ByteBufCodecs.FLOAT, StarStepPayload::dirZ,
            StarStepPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
