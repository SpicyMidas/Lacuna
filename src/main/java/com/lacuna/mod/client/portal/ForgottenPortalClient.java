package com.lacuna.mod.client.portal;

import com.lacuna.mod.registry.ModBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client wiring of the Forgotten Portal: the seamless surface ({@link ForgottenPortalRenderer}, shader registered in
 * {@code FxShaders}). Crossing through it uses the plain vanilla dimension change and loading screen.
 */
public final class ForgottenPortalClient {
    private ForgottenPortalClient() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(ForgottenPortalClient::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.FORGOTTEN_PORTAL.get(), ForgottenPortalRenderer::new);
    }
}
