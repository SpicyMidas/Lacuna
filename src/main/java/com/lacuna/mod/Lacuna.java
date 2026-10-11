package com.lacuna.mod;

import com.lacuna.mod.command.LacunaCommands;
import com.lacuna.mod.debug.SmokeRunner;
import com.lacuna.mod.registry.ModCreativeTabs;
import com.lacuna.mod.util.SafeRun;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(Lacuna.MODID)
public final class Lacuna {
    public static final String MODID = "lacuna";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Lacuna(IEventBus modBus, ModContainer container) {
        ModCreativeTabs.TABS.register(modBus);

        container.registerConfig(ModConfig.Type.COMMON, LacunaConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(LacunaCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(Lacuna::onServerStopped);
        if (SmokeRunner.isActive()) {
            SmokeRunner.register(NeoForge.EVENT_BUS);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        // Volatile, session-scoped state must not leak into the next world (spec section 3, "Data").
        SafeRun.resetAll();
    }
}
