package com.lacuna.mod;

import com.lacuna.mod.banish.BanishEvents;
import com.lacuna.mod.command.LacunaCommands;
import com.lacuna.mod.eclipse.EclipseEvents;
import com.lacuna.mod.chrono.ChronoshardWatchItem;
import com.lacuna.mod.fl.ForgottenBiomeEvents;
import com.lacuna.mod.flute.DeathMemory;
import com.lacuna.mod.gear.GearEvents;
import com.lacuna.mod.lantern.LanternWard;
import com.lacuna.mod.mirror.MirrorOfRecursionItem;
import com.lacuna.mod.network.LacunaNetwork;
import com.lacuna.mod.structure.SunkenBellBlock;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.wood.WoodSetup;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import com.lacuna.mod.debug.SmokeRunner;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModRegistries;
import com.lacuna.mod.util.AnvilRepairs;
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
        ModRegistries.register(modBus);

        container.registerConfig(ModConfig.Type.COMMON, LacunaConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(LacunaCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(Lacuna::onServerStopped);
        BanishEvents.register(NeoForge.EVENT_BUS);
        EclipseEvents.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.polish.Flourishes.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.gear.TexturedSweep.register(NeoForge.EVENT_BUS);
        modBus.addListener(ModEntities::onAttributes);
        modBus.addListener(ModEntities::onSpawnPlacements);
        modBus.addListener(LacunaNetwork::register);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(WoodSetup::setup));
        // M11c: the village coin replaces the emerald placeholder while the village civic system and its currency are enabled
        com.lacuna.mod.merchant.CurrencyService.install(() -> LacunaConfig.get(LacunaConfig.VILLAGES_ENABLED)
                && LacunaConfig.get(LacunaConfig.VILLAGES_CURRENCY) ? ModItems.VILLAGE_COIN.get() : net.minecraft.world.item.Items.EMERALD);

        // M6 utility items
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Added e) -> {
            if (e.getEntity() instanceof ServerPlayer player && e.getEffectInstance() != null) {
                ChronoshardWatchItem.recordHarmful(player, e.getEffectInstance().getEffect());
            }
        });
        NeoForge.EVENT_BUS.addListener(MirrorOfRecursionItem::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(LanternWard::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(LanternWard::onSpawnCheck);
        NeoForge.EVENT_BUS.addListener(LanternWard::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DeathMemory::onDeath);
        NeoForge.EVENT_BUS.addListener(DeathMemory::onServerStopped);
        GearEvents.register(NeoForge.EVENT_BUS);
        ForgottenBiomeEvents.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.village.VillageEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(com.lacuna.mod.merchant.MerchantSpawner::onServerTick);
        AnvilRepairs.add(ModItems.WHISPERBONE_FLUTE, ModItems.ECHO_FRAGMENT, Balance.FLUTE_REPAIR_PER_FRAGMENT);

        AnvilRepairs.add(ModItems.DIVINE_MAGNIFYING_GLASS, ModItems.DIVINE_GLASS_SHARD, Balance.BEAM_REPAIR_PER_SHARD);
        AnvilRepairs.add(ModItems.ARRIVAL_SHARD, () -> net.minecraft.world.item.Items.ENDER_PEARL, Balance.SHARD_REPAIR_PER_PEARL);
        AnvilRepairs.add(ModItems.DEPARTURE_SHARD, () -> net.minecraft.world.item.Items.ENDER_PEARL, Balance.SHARD_REPAIR_PER_PEARL);
        AnvilRepairs.add(ModItems.EMBER_TRIANGLE, ModItems.EMBER, Balance.EMBER_TRIANGLE_REPAIR_PER_EMBER);
        NeoForge.EVENT_BUS.addListener(AnvilRepairs::onAnvilUpdate);
        com.lacuna.mod.bag.BagEvents.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.confusion.Confusion.register(modBus, NeoForge.EVENT_BUS);
        com.lacuna.mod.star.NaturalStarfall.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.mummy.Mummification.register(NeoForge.EVENT_BUS);
        com.lacuna.mod.chart.ChartService.register(modBus, NeoForge.EVENT_BUS);
        com.lacuna.mod.guide.Guide.register(modBus, NeoForge.EVENT_BUS);
        com.lacuna.mod.illusioner.Illusioners.register(modBus, NeoForge.EVENT_BUS);
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
        SunkenBellBlock.clearVolatile();
        LanternWard.clearVolatile();
        com.lacuna.mod.structure.LabyrinthHandler.clearVolatile();
        com.lacuna.mod.village.VillageEvents.clearVolatile();
    }
}
