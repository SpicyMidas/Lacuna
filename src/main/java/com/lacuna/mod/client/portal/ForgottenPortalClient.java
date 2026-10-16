package com.lacuna.mod.client.portal;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.registry.ModDimensions;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client wiring of the Forgotten Portal: the seamless surface ({@link ForgottenPortalRenderer}, shader registered in
 * {@code FxShaders}), the travel transition ({@link PortalTransition}: client tick, GUI layer above the whole HUD,
 * FxPayload handler registered from {@code FxHandlers.bootstrap()}) and its loading screen ({@link ForgottenVoidScreen}).
 */
public final class ForgottenPortalClient {
    private ForgottenPortalClient() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(ForgottenPortalClient::onRegisterRenderers);
        modBus.addListener(ForgottenPortalClient::onRegisterLayers);
        modBus.addListener(ForgottenPortalClient::onRegisterTransitionScreens);
        forgeBus.addListener(PortalTransition::onClientTick);
        forgeBus.addListener(PortalTransition::onLogout);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.FORGOTTEN_PORTAL.get(), ForgottenPortalRenderer::new);
    }

    private static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Lacuna.id("forgotten_portal_transition"), PortalTransition::render);
    }

    private static void onRegisterTransitionScreens(RegisterDimensionTransitionScreenEvent event) {
        event.registerConditionalEffect(ModDimensions.FORGOTTEN_LAND, Level.OVERWORLD, ForgottenVoidScreen::new);
        event.registerConditionalEffect(Level.OVERWORLD, ModDimensions.FORGOTTEN_LAND, ForgottenVoidScreen::new);
    }
}
