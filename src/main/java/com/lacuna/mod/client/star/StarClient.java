package com.lacuna.mod.client.star;

import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.star.FallingStarEntity;
import com.lacuna.mod.star.Starfall;
import com.lacuna.mod.util.SafeRun;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Client wiring of the Starcaller Trumpet: the falling star is drawn entirely by a {@link FallingStarFx} attached when
 * the entity arrives on the client (its entity renderer draws nothing), the impact by {@link StarImpactFx}, and the
 * trumpet is raised to the lips with the vanilla goat horn "tooting" model switch.
 */
public final class StarClient {
    /** Live star effects by entity id, so the impact can tell its star where it landed. */
    static final Map<Integer, FallingStarFx> STARS = new HashMap<>();

    private StarClient() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(StarClient::onRegisterRenderers);
        modBus.addListener(StarClient::onClientSetup);
        forgeBus.addListener(StarClient::onEntityJoin);
        forgeBus.addListener(StarClient::onLogout);
        FxHandlers.register(Starfall.FX_IMPACT, StarImpactFx::handle);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FALLING_STAR.get(), NoopRenderer::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SafeRun.run("item_properties", () -> ItemProperties.register(ModItems.STARCALLER_TRUMPET.get(),
                ResourceLocation.withDefaultNamespace("tooting"), (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F)));
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof FallingStarEntity star) {
            SafeRun.run(Starfall.FEATURE, () -> {
                FallingStarFx fx = new FallingStarFx(star);
                STARS.put(star.getId(), fx);
                FxManager.add(fx);
            });
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STARS.clear();
    }
}
