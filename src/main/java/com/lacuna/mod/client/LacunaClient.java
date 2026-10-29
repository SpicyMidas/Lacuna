package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.render.OutlineRenderer;
import com.lacuna.mod.client.sight.DivineSight;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point; this class and everything under {@code client/} never loads on a dedicated server. */
@Mod(value = Lacuna.MODID, dist = Dist.CLIENT)
public final class LacunaClient {
    public LacunaClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, LacunaClientConfig.SPEC);
        ClientSetup.register(modBus);
        modBus.addListener(GenericTooltips::onRegisterKeys);

        NeoForge.EVENT_BUS.addListener(DivineSight::onClientTick);
        com.lacuna.mod.client.sight.DivineXray.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.client.mirror.MirrorReflections.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(OutlineRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(OutlineRenderer::onLogout);
        NeoForge.EVENT_BUS.addListener(MagnifierInput::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MarshAtmosphere::onClientTick);
        NeoForge.EVENT_BUS.addListener(ForgottenAtmosphere::onClientTick);
        NeoForge.EVENT_BUS.addListener(ForgottenAtmosphere::onFog);
        NeoForge.EVENT_BUS.addListener(MarshAtmosphere::onPlaySound);
        NeoForge.EVENT_BUS.addListener(GenericTooltips::onTooltip);
        NeoForge.EVENT_BUS.addListener(HeldAuras::onClientTick);
        // Revision 2 effect framework (client/fx)
        NeoForge.EVENT_BUS.addListener(com.lacuna.mod.client.fx.FxManager::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lacuna.mod.client.fx.FxManager::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.lacuna.mod.client.fx.FxManager::onLogout);
        NeoForge.EVENT_BUS.addListener(com.lacuna.mod.client.fx.ScreenFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lacuna.mod.client.fx.ScreenFx::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(com.lacuna.mod.client.fx.ScreenFx::onLogout);
        com.lacuna.mod.client.fx.FxHandlers.bootstrap();
        NeoForge.EVENT_BUS.addListener(com.lacuna.mod.client.gear.GravemawFx::onClientTick);
        com.lacuna.mod.client.gear.StarlaceClient.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.client.solar.SolarClient.register(modBus, NeoForge.EVENT_BUS);
        com.lacuna.mod.client.eclipse.EclipseClient.register(modBus, NeoForge.EVENT_BUS);
        com.lacuna.mod.client.portal.ForgottenPortalClient.register(modBus, NeoForge.EVENT_BUS);
        com.lacuna.mod.client.debug.ClientDemo.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.client.chrono.ChronoHourglassClient.register(modBus);
        com.lacuna.mod.client.bag.BagClient.register(modBus);
        com.lacuna.mod.client.star.StarClient.register(modBus, NeoForge.EVENT_BUS);
        com.lacuna.mod.client.confusion.ConfusionClient.register(modBus, NeoForge.EVENT_BUS);
        com.lacuna.mod.client.mummy.MummyClient.register(modBus);
        com.lacuna.mod.client.chart.ChartClient.register(NeoForge.EVENT_BUS);
    }
}
