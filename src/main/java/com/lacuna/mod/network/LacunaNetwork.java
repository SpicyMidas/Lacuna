package com.lacuna.mod.network;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Mod-bus payload registration. Client handlers live in client/ and are only touched on the physical client. */
public final class LacunaNetwork {
    private LacunaNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(RevealOresPayload.TYPE, RevealOresPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                com.lacuna.mod.client.ClientPayloads.reveal(payload);
            }
        });
        registrar.playToClient(FxPayload.TYPE, FxPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist.isClient()) {
                com.lacuna.mod.client.fx.FxHandlers.handle(payload);
            }
        });
        // client -> server: handled on the server thread; the server validates every condition itself
        registrar.playToServer(StarStepPayload.TYPE, StarStepPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                com.lacuna.mod.gear.StarlaceSet.onStepRequest(player, payload);
            }
        });
        com.lacuna.mod.bag.BagPlayerNet.register(registrar);
    }
}
