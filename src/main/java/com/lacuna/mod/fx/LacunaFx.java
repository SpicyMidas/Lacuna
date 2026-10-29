package com.lacuna.mod.fx;

import com.lacuna.mod.network.FxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side helpers that broadcast {@link FxPayload} visual effects to nearby clients. */
public final class LacunaFx {
    /** Default broadcast radius: comfortably beyond normal view of small effects. */
    public static final double NEAR = 96.0;

    private LacunaFx() {}

    /** Sends to every player in {@code level} within {@code radius} of {@code at}. */
    public static void send(ServerLevel level, Vec3 at, double radius, FxPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, radius, payload);
    }

    public static void send(ServerLevel level, Vec3 at, FxPayload payload) {
        send(level, at, NEAR, payload);
    }

    public static void sendTo(ServerPlayer player, FxPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
