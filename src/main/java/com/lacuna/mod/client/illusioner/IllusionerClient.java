package com.lacuna.mod.client.illusioner;

import com.lacuna.mod.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.IllusionerRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client wiring of the Illusioner: its duplicates are drawn exactly like it ({@link IllusionerDoubleRenderer}), and both
 * gain the starlight eyes ({@link IllusionerEyesLayer}). The starcloth robe and hood are a resource override of the
 * vanilla illusioner texture, so the vanilla renderer itself stays in place.
 */
public final class IllusionerClient {
    private IllusionerClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(IllusionerClient::onRegisterRenderers);
        modBus.addListener(IllusionerClient::onAddLayers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ILLUSIONER_DOUBLE.get(), IllusionerDoubleRenderer::new);
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        EntityRenderer<?> renderer = event.getRenderer(EntityType.ILLUSIONER);
        if (renderer instanceof IllusionerRenderer illusioner) {
            illusioner.addLayer(new IllusionerEyesLayer<>(illusioner));
        }
    }
}
