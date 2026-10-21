package com.lacuna.mod.client.eclipse;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client wiring of the Eclipser: the local dash movement ({@link DashDriver}), the dasher's FOV punch and dark
 * vignette, and the Shadow Walk screen atmosphere. The world effects themselves are {@link EclipseFx}.
 */
public final class EclipseClient {
    public static final String FEATURE = "eclipse_client";

    private EclipseClient() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(EclipseClient::onRegisterLayers);
        forgeBus.addListener(EclipseClient::onPlayerTickPre);
        forgeBus.addListener(EclipseClient::onPlayerTickPost);
        forgeBus.addListener(EclipseClient::onMovementInput);
        forgeBus.addListener(EclipseClient::onClientTick);
        forgeBus.addListener(EclipseClient::onComputeFov);
        forgeBus.addListener(EclipseClient::onLogout);
        PossessionClient.register(modBus, forgeBus);
    }

    private static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, Lacuna.id("shadow_walk"),
                (g, delta) -> SafeRun.run(FEATURE, () -> ShadowWalkOverlay.render(g, delta)));
    }

    private static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (DashDriver.active() && event.getEntity().level().isClientSide()) {
            SafeRun.run(FEATURE, () -> DashDriver.onPlayerTickPre(event), DashDriver::stop);
        }
    }

    private static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (DashDriver.active() && event.getEntity().level().isClientSide()) {
            SafeRun.run(FEATURE, () -> DashDriver.onPlayerTickPost(event), DashDriver::stop);
        }
    }

    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (DashDriver.active()) {
            DashDriver.onMovementInput(event);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            DashDriver.reset();
            ShadowWalkOverlay.reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            DashDriver.clientTick();
            if (DashDriver.active()) {
                ScreenFx.hold("eclipse_dash", 0x000000, 0.8F, true);
            }
            ShadowWalkOverlay.tick(mc);
        }, DashDriver::stop);
    }

    /** Brief FOV punch while dashing (scaled by the vanilla FOV Effects slider). */
    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || SafeRun.isDisabled(FEATURE)
                || !LacunaClientConfig.get(LacunaClientConfig.SCREEN_EFFECTS)) {
            return;
        }
        float punch = DashDriver.punch((float) event.getPartialTick());
        if (punch > 0.0F) {
            double scale = Minecraft.getInstance().options.fovEffectScale().get();
            event.setFOV(event.getFOV() * (1.0 + 0.16 * punch * scale));
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DashDriver.reset();
        ShadowWalkOverlay.reset();
        Afterimage.reset();
    }
}
