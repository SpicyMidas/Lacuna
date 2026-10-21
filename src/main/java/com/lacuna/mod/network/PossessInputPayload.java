package com.lacuna.mod.network;

import com.lacuna.mod.Lacuna;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server for Shadow Possession ({@code eclipse/Possession}). One payload, several kinds:
 * <ul>
 *   <li>{@link #REQUEST}: a shadow walker clicked {@code entityId} (flag {@link #SECONDARY} for the use button);</li>
 *   <li>{@link #INPUT}: every tick while possessing, the movement keys ({@code forward}/{@code strafe} in -1..1,
 *       relative to the look), the look itself and the jump/sprint flags;</li>
 *   <li>{@link #ACTION}: the possessor clicked: make the host strike ({@code entityId} is the client's crosshair
 *       entity or -1, only a hint: the server picks the target itself);</li>
 *   <li>{@link #STRUGGLE}: a possessed player fights back ({@code amount} accumulated since the last report);</li>
 *   <li>{@link #RELEASE}: the possessor pressed sneak to leave the host.</li>
 * </ul>
 * The server validates everything (and clamps struggle) itself.
 */
public record PossessInputPayload(byte kind, int entityId, float forward, float strafe, float yaw, float pitch, byte flags,
                                  float amount) implements CustomPacketPayload {
    public static final byte REQUEST = 0;
    public static final byte INPUT = 1;
    public static final byte ACTION = 2;
    public static final byte STRUGGLE = 3;
    public static final byte RELEASE = 4;

    public static final byte JUMP = 1;
    public static final byte SPRINT = 2;
    public static final byte SECONDARY = 4;

    public static final Type<PossessInputPayload> TYPE = new Type<>(Lacuna.id("possess_input"));
    public static final StreamCodec<ByteBuf, PossessInputPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                ByteBufCodecs.VAR_INT.encode(buf, p.entityId);
                buf.writeFloat(p.forward);
                buf.writeFloat(p.strafe);
                buf.writeFloat(p.yaw);
                buf.writeFloat(p.pitch);
                buf.writeByte(p.flags);
                buf.writeFloat(p.amount);
            },
            buf -> new PossessInputPayload(buf.readByte(), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readFloat()));

    public static PossessInputPayload request(int entityId, boolean secondary) {
        return new PossessInputPayload(REQUEST, entityId, 0, 0, 0, 0, secondary ? SECONDARY : 0, 0);
    }

    public static PossessInputPayload input(float forward, float strafe, float yaw, float pitch, boolean jump, boolean sprint) {
        return new PossessInputPayload(INPUT, -1, forward, strafe, yaw, pitch, (byte) ((jump ? JUMP : 0) | (sprint ? SPRINT : 0)), 0);
    }

    public static PossessInputPayload action(int entityId, boolean secondary, float yaw, float pitch) {
        return new PossessInputPayload(ACTION, entityId, 0, 0, yaw, pitch, secondary ? SECONDARY : 0, 0);
    }

    public static PossessInputPayload struggle(float amount) {
        return new PossessInputPayload(STRUGGLE, -1, 0, 0, 0, 0, (byte) 0, amount);
    }

    public static PossessInputPayload release() {
        return new PossessInputPayload(RELEASE, -1, 0, 0, 0, 0, (byte) 0, 0);
    }

    public boolean has(byte flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
