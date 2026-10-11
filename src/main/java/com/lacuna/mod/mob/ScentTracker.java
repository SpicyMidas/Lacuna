package com.lacuna.mod.mob;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ScentTrail;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Volatile scent trails of every online player (never saved; cleared on logout and server stop). */
public final class ScentTracker {
    private static final Map<UUID, ScentTrail> TRAILS = new ConcurrentHashMap<>();

    private ScentTracker() {}

    public static ScentTrail trail(UUID player) {
        return TRAILS.get(player);
    }

    public static Map<UUID, ScentTrail> all() {
        return TRAILS;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!LacunaConfig.get(LacunaConfig.SCENT_ENABLED) || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            long now = player.serverLevel().getGameTime();
            ScentTrail trail = TRAILS.computeIfAbsent(player.getUUID(),
                    k -> new ScentTrail(Balance.SCENT_INTERVAL, Balance.SCENT_WINDOW, Balance.SCENT_MAX_POINTS));
            trail.record(player.getX(), player.getY(), player.getZ(), now);
            trail.prune(now);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRAILS.remove(event.getEntity().getUUID());
    }

    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        TRAILS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        TRAILS.clear();
    }
}
