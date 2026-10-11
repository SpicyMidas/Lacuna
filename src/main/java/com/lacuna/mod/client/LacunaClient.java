package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point; this class and everything under {@code client/} never loads on a dedicated server. */
@Mod(value = Lacuna.MODID, dist = Dist.CLIENT)
public final class LacunaClient {
    public LacunaClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
