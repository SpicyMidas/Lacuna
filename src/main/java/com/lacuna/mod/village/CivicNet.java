package com.lacuna.mod.village;

import com.lacuna.mod.Lacuna;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network side of the civic block screens ({@link CivicScreens}). The server owns every rule: it sends a snapshot of
 * what the screen shows, the screen answers with a small action, the server validates it (block, distance, village,
 * costs) with the same logic the blocks always used and replies with a fresh snapshot carrying the outcome.
 */
public final class CivicNet {
    private CivicNet() {}

    /**
     * Server → client: what a civic screen shows. {@code open} asks the client to open the screen (a right click);
     * otherwise the snapshot only refreshes an already open screen for the same block. {@code feedback} is the outcome
     * of the last action (empty for none), {@code ok} tells success from refusal.
     */
    public record Snapshot(BlockPos pos, String kind, CompoundTag data, Component feedback, boolean ok, boolean open)
            implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Lacuna.id("civic_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Snapshot::pos,
                ByteBufCodecs.STRING_UTF8, Snapshot::kind,
                ByteBufCodecs.COMPOUND_TAG, Snapshot::data,
                ComponentSerialization.STREAM_CODEC, Snapshot::feedback,
                ByteBufCodecs.BOOL, Snapshot::ok,
                ByteBufCodecs.BOOL, Snapshot::open,
                Snapshot::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: a button of the civic screen of the block at {@code pos} ("refresh", "join", "pay"...). */
    public record Action(BlockPos pos, String action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Lacuna.id("civic_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos,
                ByteBufCodecs.stringUtf8(32), Action::action,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                com.lacuna.mod.client.village.CivicClient.onSnapshot(payload);
            }
        });
        registrar.playToServer(Action.TYPE, Action.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                CivicScreens.onAction(player, payload);
            }
        });
    }
}
