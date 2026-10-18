package com.lacuna.mod.client.chrono;

import com.lacuna.mod.registry.ModBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client wiring of the Chrono Hourglass: the sand renderer (the frame and glass are plain block models). */
public final class ChronoHourglassClient {
    private ChronoHourglassClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ChronoHourglassClient::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CHRONO_HOURGLASS.get(), ChronoHourglassRenderer::new);
    }
}
