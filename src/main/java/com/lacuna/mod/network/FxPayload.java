package com.lacuna.mod.network;

import com.lacuna.mod.Lacuna;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Generic server → client visual-effect trigger. {@code kind} selects a client handler registered in
 * {@code client/fx/FxHandlers}; the remaining fields are interpreted by that handler (two points, an entity,
 * three floats and an ARGB colour). Purely cosmetic: a client that ignores it loses nothing but the effect.
 */
public record FxPayload(String kind, int entityId, Vec3 a, Vec3 b, float p0, float p1, float p2, int color)
        implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(Lacuna.id("fx"));

    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);

    public static final StreamCodec<ByteBuf, FxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.stringUtf8(64).encode(buf, p.kind);
                ByteBufCodecs.VAR_INT.encode(buf, p.entityId);
                VEC3.encode(buf, p.a);
                VEC3.encode(buf, p.b);
                buf.writeFloat(p.p0);
                buf.writeFloat(p.p1);
                buf.writeFloat(p.p2);
                buf.writeInt(p.color);
            },
            buf -> new FxPayload(ByteBufCodecs.stringUtf8(64).decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    VEC3.decode(buf), VEC3.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt()));

    /** Effect at a point. */
    public static FxPayload at(String kind, Vec3 pos) {
        return new FxPayload(kind, -1, pos, pos, 0, 0, 0, 0xFFFFFFFF);
    }

    /** Effect between two points. */
    public static FxPayload line(String kind, Vec3 from, Vec3 to) {
        return new FxPayload(kind, -1, from, to, 0, 0, 0, 0xFFFFFFFF);
    }

    public FxPayload withEntity(int id) {
        return new FxPayload(kind, id, a, b, p0, p1, p2, color);
    }

    public FxPayload withParams(float q0, float q1, float q2) {
        return new FxPayload(kind, entityId, a, b, q0, q1, q2, color);
    }

    public FxPayload withColor(int argb) {
        return new FxPayload(kind, entityId, a, b, p0, p1, p2, argb);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
