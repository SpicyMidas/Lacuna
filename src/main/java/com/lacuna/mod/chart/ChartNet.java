package com.lacuna.mod.chart;

import com.lacuna.mod.Lacuna;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Network side of the Wayfarer's Chart: the holder's view and markers, and finished tiles, server to holder. */
public final class ChartNet {
    private ChartNet() {}

    /** A point of interest on the Overworld, in block coordinates. */
    public record Marker(int kind, int x, int z) {
        public static final int SPAWN = 0;
        public static final int HOME = 1;
        public static final int BANISHED_FROM = 2;
        public static final StreamCodec<ByteBuf, Marker> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Marker::kind,
                ByteBufCodecs.VAR_INT, Marker::x,
                ByteBufCodecs.VAR_INT, Marker::z,
                Marker::new);
    }

    /**
     * What the holder's chart shows while they are in {@code dimension}: the Overworld at {@code zoom}, centred on their
     * position times {@code scale}, or on {@code anchor} when {@code scale} is 0 (the End), plus the markers.
     */
    public record State(ResourceKey<Level> dimension, int zoom, double scale, BlockPos anchor, List<Marker> markers)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Lacuna.id("chart_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(Registries.DIMENSION), State::dimension,
                ByteBufCodecs.VAR_INT, State::zoom,
                ByteBufCodecs.DOUBLE, State::scale,
                BlockPos.STREAM_CODEC, State::anchor,
                Marker.STREAM_CODEC.apply(ByteBufCodecs.list(8)), State::markers,
                State::new);

        public boolean follows() {
            return scale > 0.0;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One finished tile: {@link ChartGrid#TILE_PIXELS} packed vanilla map colours (row-major, north row first) and a bit
     * set flagging the pixels sketched from the climate instead of surveyed.
     */
    public record Tile(int zoom, int tx, int tz, byte[] colors, byte[] rumour) implements CustomPacketPayload {
        public static final Type<Tile> TYPE = new Type<>(Lacuna.id("chart_tile"));
        public static final StreamCodec<ByteBuf, Tile> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Tile::zoom,
                ByteBufCodecs.VAR_INT, Tile::tx,
                ByteBufCodecs.VAR_INT, Tile::tz,
                ByteBufCodecs.BYTE_ARRAY, Tile::colors,
                ByteBufCodecs.BYTE_ARRAY, Tile::rumour,
                Tile::new);

        public boolean wellFormed() {
            return colors.length == ChartGrid.TILE_PIXELS && rumour.length == ChartGrid.RUMOUR_BYTES
                    && zoom == ChartGrid.clampZoom(zoom);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(State.TYPE, State.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                com.lacuna.mod.client.chart.ChartClient.onState(payload);
            }
        });
        registrar.playToClient(Tile.TYPE, Tile.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                com.lacuna.mod.client.chart.ChartClient.onTile(payload);
            }
        });
    }
}
