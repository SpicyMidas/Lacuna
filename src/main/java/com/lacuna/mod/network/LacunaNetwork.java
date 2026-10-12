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
    }
}
