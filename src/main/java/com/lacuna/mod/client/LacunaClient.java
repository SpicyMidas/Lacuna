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
        ClientSetup.register(modBus);

        NeoForge.EVENT_BUS.addListener(DivineSight::onClientTick);
        NeoForge.EVENT_BUS.addListener(OutlineRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(OutlineRenderer::onLogout);
        NeoForge.EVENT_BUS.addListener(MagnifierInput::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MarshAtmosphere::onClientTick);
        NeoForge.EVENT_BUS.addListener(MarshAtmosphere::onFog);
        NeoForge.EVENT_BUS.addListener(MarshAtmosphere::onPlaySound);
        NeoForge.EVENT_BUS.addListener(GenericTooltips::onTooltip);
    }
}
