package com.lacuna.mod.client.mummy;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Client wiring of the Starlace Bandage: the {@link MummyLayer} is added to every living-entity renderer (vanilla and
 * modded alike), and its texture-size cache is dropped when resource packs change.
 */
public final class MummyClient {
    private MummyClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MummyClient::onAddLayers);
        modBus.addListener(MummyClient::onReloadListeners);
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?> renderer = event.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer<?, ?> living) {
                addTo(living);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTo(LivingEntityRenderer renderer) {
        renderer.addLayer(new MummyLayer(renderer));
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> MummyLayer.clearCache());
    }
}
