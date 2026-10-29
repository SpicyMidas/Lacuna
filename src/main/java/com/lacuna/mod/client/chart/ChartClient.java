package com.lacuna.mod.client.chart;

import com.lacuna.mod.chart.ChartGrid;
import com.lacuna.mod.chart.ChartNet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the Wayfarer's Chart: the last view the server sent and the tiles received so far (least recently
 * drawn dropped first; the server re-sends any tile that comes back into view after it forgot about it).
 */
public final class ChartClient {
    private static final int MAX_TILES = 512;
    private static final Map<ChartGrid.TileKey, ChartNet.Tile> TILES = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ChartGrid.TileKey, ChartNet.Tile> eldest) {
            return size() > MAX_TILES;
        }
    };
    @Nullable
    private static ChartNet.State state;

    private ChartClient() {}

    public static void register(IEventBus forgeBus) {
        forgeBus.addListener(ChartHandRenderer::onRenderHand);
        forgeBus.addListener(ChartClient::onLogout);
    }

    public static void onState(ChartNet.State payload) {
        state = payload;
        ChartTexture.invalidate();
    }

    public static void onTile(ChartNet.Tile payload) {
        if (!payload.wellFormed()) {
            return;
        }
        ChartGrid.TileKey key = new ChartGrid.TileKey(payload.zoom(), payload.tx(), payload.tz());
        TILES.put(key, payload);
        ChartTexture.tileArrived(key);
    }

    @Nullable
    static ChartNet.State state() {
        return state;
    }

    @Nullable
    static ChartNet.Tile tile(ChartGrid.TileKey key) {
        return TILES.get(key);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
        TILES.clear();
        ChartTexture.invalidate();
    }
}
