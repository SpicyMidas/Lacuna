package com.lacuna.mod.network;

import com.lacuna.mod.Lacuna;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client state of a Shadow Possession, sent every tick to both sides while it lasts:
 * <ul>
 *   <li>{@link #POSSESSOR}: the shadow inside {@code hostId} (HUD, umbral vignette, input sending);</li>
 *   <li>{@link #HOST}: a possessed player, with the possessor's movement keys, look and flags to blend into its own
 *       controls at weight {@code control};</li>
 *   <li>{@link #NONE}: the possession ended ({@code flags} carries {@link #EXPELLED} when the shadow was cast out).</li>
 * </ul>
 * {@code hold} is the host's struggle towards fighting back (0..1); {@code countdown} the time left before the
 * expulsion once it fights back (1..0), or -1 while it does not.
 */
public record PossessSyncPayload(byte role, int hostId, float forward, float strafe, float yaw, float pitch, byte flags,
                                 float hold, float countdown, float control) implements CustomPacketPayload {
    public static final byte NONE = 0;
    public static final byte POSSESSOR = 1;
    public static final byte HOST = 2;

    public static final byte JUMP = 1;
    public static final byte SPRINT = 2;
    public static final byte EXPELLED = 4;
    /** The host struggled this tick (heartbeat quickens, the host twitches). */
    public static final byte STRUGGLING = 8;

    public static final Type<PossessSyncPayload> TYPE = new Type<>(Lacuna.id("possess_sync"));
    public static final StreamCodec<ByteBuf, PossessSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.role);
                ByteBufCodecs.VAR_INT.encode(buf, p.hostId);
                buf.writeFloat(p.forward);
                buf.writeFloat(p.strafe);
                buf.writeFloat(p.yaw);
                buf.writeFloat(p.pitch);
                buf.writeByte(p.flags);
                buf.writeFloat(p.hold);
                buf.writeFloat(p.countdown);
                buf.writeFloat(p.control);
            },
            buf -> new PossessSyncPayload(buf.readByte(), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    public static PossessSyncPayload none(int hostId, boolean expelled) {
        return new PossessSyncPayload(NONE, hostId, 0, 0, 0, 0, expelled ? EXPELLED : 0, 0, -1, 0);
    }

    public boolean has(byte flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
