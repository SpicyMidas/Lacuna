package com.lacuna.mod.bag;

import com.lacuna.mod.Lacuna;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Network side of bagged players ({@link BaggedPlayers}): who sits in whose bag, and the struggle button. */
public final class BagPlayerNet {
    private BagPlayerNet() {}

    /**
     * Server → client: {@code target} is now in {@code bagger}'s bag ({@code active}) for {@code ticksLeft} more ticks,
     * or out again. Sent to the target and everyone tracking it, so every client hides and shrinks it the same way.
     */
    public record State(UUID target, UUID bagger, boolean active, int ticksLeft) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Lacuna.id("bag_player"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, State::target,
                UUIDUtil.STREAM_CODEC, State::bagger,
                ByteBufCodecs.BOOL, State::active,
                ByteBufCodecs.VAR_INT, State::ticksLeft,
                State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: the bagged player pressed jump or attack inside the bag. The server rate-limits it. */
    public record Struggle() implements CustomPacketPayload {
        public static final Struggle INSTANCE = new Struggle();
        public static final Type<Struggle> TYPE = new Type<>(Lacuna.id("bag_struggle"));
        public static final StreamCodec<ByteBuf, Struggle> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(State.TYPE, State.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                com.lacuna.mod.client.bag.BagPlayerClient.onState(payload);
            }
        });
        registrar.playToServer(Struggle.TYPE, Struggle.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                BaggedPlayers.struggle(player);
            }
        });
    }
}
