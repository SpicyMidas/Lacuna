package com.lacuna.mod;

import com.lacuna.mod.banish.BanishEvents;
import com.lacuna.mod.command.LacunaCommands;
import com.lacuna.mod.eclipse.EclipseEvents;
import com.lacuna.mod.chrono.ChronoshardWatchItem;
import com.lacuna.mod.fl.ForgottenBiomeEvents;
import com.lacuna.mod.flute.DeathMemory;
import com.lacuna.mod.gear.GearEvents;
import com.lacuna.mod.lantern.NullLanternItem;
import com.lacuna.mod.mirror.MirrorOfRecursionItem;
import com.lacuna.mod.mob.ScentTracker;
import com.lacuna.mod.orrery.OrreryBlock;
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
        modBus.addListener(ModEntities::onAttributes);
        modBus.addListener(ModEntities::onSpawnPlacements);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(WoodSetup::setup));

        // M6 utility items
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Added e) -> {
            if (e.getEntity() instanceof ServerPlayer player && e.getEffectInstance() != null) {
                ChronoshardWatchItem.recordHarmful(player, e.getEffectInstance().getEffect());
            }
        });
        NeoForge.EVENT_BUS.addListener(MirrorOfRecursionItem::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(NullLanternItem::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(NullLanternItem::onSpawnCheck);
        NeoForge.EVENT_BUS.addListener(DeathMemory::onDeath);
        NeoForge.EVENT_BUS.addListener(DeathMemory::onServerStopped);
        GearEvents.register(NeoForge.EVENT_BUS);
        ForgottenBiomeEvents.register(NeoForge.EVENT_BUS);
        // M9 volatile scent trails
        NeoForge.EVENT_BUS.addListener(ScentTracker::onServerTick);
        NeoForge.EVENT_BUS.addListener(ScentTracker::onLogout);
        NeoForge.EVENT_BUS.addListener(ScentTracker::onChangeDimension);
        NeoForge.EVENT_BUS.addListener(ScentTracker::onServerStopped);
        AnvilRepairs.add(ModItems.WHISPERBONE_FLUTE, ModItems.ECHO_FRAGMENT, Balance.FLUTE_REPAIR_PER_FRAGMENT);

        AnvilRepairs.add(ModItems.DIVINE_MAGNIFYING_GLASS, ModItems.DIVINE_GLASS_SHARD, Balance.BEAM_REPAIR_PER_SHARD);
        AnvilRepairs.add(ModItems.ARRIVAL_SHARD, () -> net.minecraft.world.item.Items.ENDER_PEARL, Balance.SHARD_REPAIR_PER_PEARL);
        AnvilRepairs.add(ModItems.DEPARTURE_SHARD, () -> net.minecraft.world.item.Items.ENDER_PEARL, Balance.SHARD_REPAIR_PER_PEARL);
        NeoForge.EVENT_BUS.addListener(AnvilRepairs::onAnvilUpdate);
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
        OrreryBlock.clearVolatile();
    }
}
