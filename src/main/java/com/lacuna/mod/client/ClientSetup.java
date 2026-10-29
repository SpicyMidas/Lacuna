package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.eclipse.EclipserItem;
import com.lacuna.mod.lantern.NullLanternItem;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Mod-bus client registrations: item model predicates and entity renderers (vanilla renderer classes only). */
public final class ClientSetup {
    private ClientSetup() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(com.lacuna.mod.client.fx.FxShaders::onRegisterShaders);
        modBus.addListener(com.lacuna.mod.client.fx.FxParticles::register);
        modBus.addListener(com.lacuna.mod.client.fx.ScreenFx::registerLayer);
        modBus.addListener(com.lacuna.mod.client.gear.AnimatedItemRenderer::registerLayers);
        modBus.addListener(com.lacuna.mod.client.gear.TideRenderTypes::onRegisterShaders);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SafeRun.run("item_properties", ClientSetup::itemProperties));
    }

    private static void itemProperties() {
        ItemProperties.register(ModItems.ECLIPSER.get(), Lacuna.id("charge"),
                (stack, level, entity, seed) -> EclipserItem.charge(stack) / 100.0F);
        // the Null Lantern goes dark (lacuna:item/null_lantern_off) when its charge runs out
        ItemProperties.register(ModItems.NULL_LANTERN.get(), Lacuna.id("lit"),
                (stack, level, entity, seed) -> NullLanternItem.isLit(stack) ? 1.0F : 0.0F);
        // the Heartwood Bow reuses the vanilla bow model predicates, registered for our item
        ItemProperties.register(ModItems.HEARTWOOD_BOW.get(), ResourceLocation.withDefaultNamespace("pull"),
                (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                        : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F);
        ItemProperties.register(ModItems.HEARTWOOD_BOW.get(), ResourceLocation.withDefaultNamespace("pulling"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SUNS_FURY.get(), com.lacuna.mod.client.solar.SunsFuryRenderer::new);
        event.registerEntityRenderer(ModEntities.SOLAR_RAY.get(), com.lacuna.mod.client.solar.SolarRayRenderer::new);
        event.registerEntityRenderer(ModEntities.ARRIVAL_SHARD.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.ECHO.get(), EchoRenderer::new);
        event.registerEntityRenderer(ModEntities.PYRRHIC_BOLT.get(), PyrrhicBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.LANTERN_MOTH.get(), LanternMothRenderer::new);
        event.registerEntityRenderer(ModEntities.DECOY.get(), DecoyRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_DECOY.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.REFLECTION.get(), ReflectionRenderer::new);
        event.registerEntityRenderer(ModEntities.LAST_MERCHANT.get(), LastMerchantRenderer::new);
        event.registerEntityRenderer(ModEntities.ANIMATED_ITEM.get(), com.lacuna.mod.client.gear.AnimatedItemRenderer::new);
    }
}
