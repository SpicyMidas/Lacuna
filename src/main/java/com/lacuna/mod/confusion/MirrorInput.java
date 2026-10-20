package com.lacuna.mod.confusion;

import com.lacuna.mod.Lacuna;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Client -> server: the owner's movement keys this tick, sent only while the owner's client sees one of their
 * {@link ConfusionCopy copies}. {@code strafe}/{@code forward} are the client's input impulses after the sneaking and
 * item-use slowdowns (what the player's own movement uses), {@code jump} is the jump key. The server only uses them to
 * steer the sender's own copies, clamped to the range a real client produces.
 */
public record MirrorInput(float strafe, float forward, boolean jump) implements CustomPacketPayload {
    public static final Type<MirrorInput> TYPE = new Type<>(Lacuna.id("confusion_input"));
    public static final StreamCodec<ByteBuf, MirrorInput> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, MirrorInput::strafe, ByteBufCodecs.FLOAT, MirrorInput::forward, ByteBufCodecs.BOOL, MirrorInput::jump,
            MirrorInput::new);
    public static final MirrorInput NONE = new MirrorInput(0.0F, 0.0F, false);

    /** Latest input per player with the server tick it arrived on (server thread only; cleared on logout and stop). */
    private static final Map<UUID, Stamped> LATEST = new ConcurrentHashMap<>();

    private record Stamped(MirrorInput input, int tick) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void receive(ServerPlayer player, MirrorInput in) {
        if (player.getServer() == null || !Float.isFinite(in.strafe) || !Float.isFinite(in.forward)) {
            return;
        }
        MirrorInput clamped = new MirrorInput(Mth.clamp(in.strafe, -1.0F, 1.0F), Mth.clamp(in.forward, -1.0F, 1.0F), in.jump);
        LATEST.put(player.getUUID(), new Stamped(clamped, player.getServer().getTickCount()));
    }

    /** What {@code owner} is pressing now; {@link #NONE} when nothing fresh arrived (the copies stand still). */
    static MirrorInput current(ServerPlayer owner) {
        Stamped s = LATEST.get(owner.getUUID());
        if (s == null || owner.getServer() == null || owner.getServer().getTickCount() - s.tick > Confusion.INPUT_STALE_TICKS) {
            return NONE;
        }
        return s.input;
    }

    static void forget(UUID player) {
        LATEST.remove(player);
    }

    static void clear() {
        LATEST.clear();
    }
}
