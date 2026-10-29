package com.lacuna.mod.chart;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.banish.BanishmentSavedData;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.util.SafeRun;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the Wayfarer's Chart. Every tick, each player holding a chart (either hand) gets a session: the
 * {@link ChartView} of their dimension, the tiles around their translated position (nearest first) and the markers.
 * Tiles are sampled inside one shared per-tick budget ({@code items.wayfarersChartBudgetMicros}) and sent only when a
 * holder does not have their current version yet, a few per tick. Nothing is saved: the cache lives as long as the
 * server runs.
 */
public final class ChartService {
    public static final String FEATURE = "wayfarers_chart";
    /** Extra pixels around the 128-pixel view whose tiles are fetched ahead. */
    private static final int MARGIN = 8;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final ChartAtlas ATLAS = new ChartAtlas();
    private static int rotation;

    private ChartService() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(ChartNet::register);
        forgeBus.addListener(ChartService::onServerTick);
        forgeBus.addListener(ChartService::onLogout);
        forgeBus.addListener(ChartService::onServerStopped);
        forgeBus.addListener(ChartService::onTagsUpdated);
    }

    /** The chart a player holds, main hand first, or {@link ItemStack#EMPTY}. */
    public static ItemStack heldChart(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof WayfarersChartItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof WayfarersChartItem ? off : ItemStack.EMPTY;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!LacunaConfig.get(LacunaConfig.CHART_ENABLED)) {
            if (!SESSIONS.isEmpty()) {
                clear();
            }
            return;
        }
        SafeRun.run(FEATURE, () -> tick(event.getServer()), ChartService::clear);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        ServerLevel overworld = server.overworld();
        List<Session> active = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack chart = heldChart(player);
            if (chart.isEmpty()) {
                continue;
            }
            Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
            session.update(player, chart, overworld, now);
            active.add(session);
        }
        if (!active.isEmpty()) {
            long deadline = System.nanoTime() + LacunaConfig.get(LacunaConfig.CHART_BUDGET_MICROS) * 1000L;
            rotation = (rotation + 1) % active.size();
            for (int k = 0; k < active.size(); k++) {
                active.get((rotation + k) % active.size()).pump(ATLAS, overworld, now, deadline);
            }
        }
        if (now % 100 == 0) {
            ATLAS.evict(now);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        // the server's own reload (server thread); a client receiving tags must not touch the server's cache
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            ATLAS.forgetInks();
        }
    }

    private static void clear() {
        SESSIONS.clear();
        ATLAS.clear();
    }

    /** One holder: what was last sent to them and which tiles they want, nearest first. */
    private static final class Session {
        private ServerPlayer player;
        private ChartNet.State sentState;
        private ChartView view;
        private int zoom = -1;
        private long markersDue;
        private List<ChartNet.Marker> markers = List.of();
        private final List<ChartGrid.TileKey> wanted = new ArrayList<>();
        private int rangeX0;
        private int rangeZ0;
        private int rangeX1;
        private int rangeZ1;
        private int rangeZoom = -1;
        /** Tile versions this holder has (only tiles in or next to the wanted range are remembered). */
        private final Object2LongOpenHashMap<ChartGrid.TileKey> sent = new Object2LongOpenHashMap<>();

        void update(ServerPlayer holder, ItemStack chart, ServerLevel overworld, long now) {
            player = holder;
            int newZoom = WayfarersChartItem.zoom(chart);
            ChartView newView = ChartView.of(holder);
            if (newZoom != zoom || !newView.equals(view) || now >= markersDue) {
                zoom = newZoom;
                view = newView;
                markers = markers(holder, overworld);
                markersDue = now + 20;
                ChartNet.State state = new ChartNet.State(holder.level().dimension(), zoom, view.follow() ? view.scale() : 0.0,
                        view.anchor(), markers);
                if (!state.equals(sentState)) {
                    sentState = state;
                    PacketDistributor.sendToPlayer(holder, state);
                }
            }
            int bpp = 1 << zoom;
            double cpx = view.centreX(holder.getX()) / bpp;
            double cpz = view.centreZ(holder.getZ()) / bpp;
            int reach = ChartGrid.VIEW / 2 + MARGIN;
            int x0 = ChartGrid.tileOf((int) Math.floor(cpx - reach));
            int x1 = ChartGrid.tileOf((int) Math.floor(cpx + reach));
            int z0 = ChartGrid.tileOf((int) Math.floor(cpz - reach));
            int z1 = ChartGrid.tileOf((int) Math.floor(cpz + reach));
            if (zoom != rangeZoom || x0 != rangeX0 || z0 != rangeZ0 || x1 != rangeX1 || z1 != rangeZ1) {
                rangeZoom = zoom;
                rangeX0 = x0;
                rangeZ0 = z0;
                rangeX1 = x1;
                rangeZ1 = z1;
                wanted.clear();
                for (int tz = z0; tz <= z1; tz++) {
                    for (int tx = x0; tx <= x1; tx++) {
                        wanted.add(new ChartGrid.TileKey(zoom, tx, tz));
                    }
                }
                double half = ChartGrid.TILE / 2.0;
                wanted.sort(Comparator.comparingDouble(k -> {
                    double dx = (k.tx() << ChartGrid.TILE_SHIFT) + half - cpx;
                    double dz = (k.tz() << ChartGrid.TILE_SHIFT) + half - cpz;
                    return dx * dx + dz * dz;
                }));
                // forget tiles well out of range: if they come back they are sent again
                sent.keySet().removeIf(k -> k.zoom() != zoom || k.tx() < x0 - 1 || k.tx() > x1 + 1 || k.tz() < z0 - 1 || k.tz() > z1 + 1);
            }
        }

        void pump(ChartAtlas atlas, ServerLevel overworld, long now, long deadline) {
            int sends = 0;
            for (ChartGrid.TileKey key : wanted) {
                ChartTile tile = atlas.tile(key, now);
                if (ChartAtlas.due(tile, now) && System.nanoTime() < deadline) {
                    atlas.work(overworld, tile, now, deadline);
                }
                if (tile.ready() && sends < Balance.CHART_TILES_PER_TICK && sent.getLong(key) != tile.version) {
                    PacketDistributor.sendToPlayer(player, new ChartNet.Tile(key.zoom(), key.tx(), key.tz(), tile.colors, tile.rumour));
                    sent.put(key, tile.version);
                    sends++;
                }
            }
        }

        private static List<ChartNet.Marker> markers(ServerPlayer holder, ServerLevel overworld) {
            List<ChartNet.Marker> list = new ArrayList<>(3);
            BlockPos spawn = overworld.getSharedSpawnPos();
            list.add(new ChartNet.Marker(ChartNet.Marker.SPAWN, spawn.getX(), spawn.getZ()));
            BlockPos respawn = holder.getRespawnPosition();
            if (respawn != null && holder.getRespawnDimension() == Level.OVERWORLD) {
                list.add(new ChartNet.Marker(ChartNet.Marker.HOME, respawn.getX(), respawn.getZ()));
            }
            if (ModDimensions.isForgottenLand(holder.level())) {
                // where the banishment took them from: walking toward it leads home to the same place
                BanishmentSavedData.get(holder.server).byEntity(holder.getUUID())
                        .filter(r -> r.originDimension == Level.OVERWORLD)
                        .ifPresent(r -> list.add(new ChartNet.Marker(ChartNet.Marker.BANISHED_FROM, r.originPos.getX(), r.originPos.getZ())));
            }
            return List.copyOf(list);
        }
    }
}
