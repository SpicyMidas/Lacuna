package com.lacuna.mod.client.solar;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client wiring of the solar effects (Sun's Fury and the Divine Magnifying Glass): shaders, the sun flare in the
 * sky, the after-particles light pass and the per-tick impact particles. Everything runs inside {@link SafeRun}.
 */
public final class SolarClient {
    public static final String FEATURE = "solar_fx";

    private SolarClient() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(SolarRenderTypes::onRegisterShaders);
        modBus.addListener(SolarClient::onClientSetup);
        forgeBus.addListener(SolarClient::onClientTick);
        forgeBus.addListener(SolarClient::onRenderLevel);
        forgeBus.addListener(SolarClient::onFogColor);
        forgeBus.addListener(SolarSky::onFrame);
        forgeBus.addListener(SolarClient::onLogout);
        HandPose.register(forgeBus);
    }

    /** {@code lacuna:throwing}: Sun's Fury swaps to its raised, drawn-back model while it is being charged. */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SafeRun.run("item_properties", () -> ItemProperties.register(ModItems.SUNS_FURY.get(),
                Lacuna.id("throwing"), (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F)));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            SolarSky.tick(mc);
            MagnifierBeams.tick(mc);
            SpearCharge.tick(mc);
        });
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (SafeRun.isDisabled(FEATURE)) {
            SolarVisuals.clear();
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            SafeRun.run(FEATURE, () -> SolarSky.renderSky(event));
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || event.getPoseStack() == null) {
                SolarVisuals.clear();
                return;
            }
            SafeRun.run(FEATURE, () -> {
                RenderSystem.defaultBlendFunc();
                MultiBufferSource.BufferSource buffers = SolarBuffers.get();
                FxRenderContext ctx = new FxRenderContext(event.getPoseStack(), event.getCamera(), event.getCamera().getPosition(),
                        event.getPartialTick().getGameTimeDeltaPartialTick(false), buffers, mc.level);
                try {
                    SolarVisuals.render(ctx);
                    MagnifierBeams.render(ctx);
                    SpearCharge.render(ctx);
                } finally {
                    buffers.endBatch();
                    RenderSystem.defaultBlendFunc();
                }
            }, SolarVisuals::clear);
        }
    }

    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!SafeRun.isDisabled(FEATURE)) {
            SafeRun.run(FEATURE, () -> SolarSky.onFogColor(event));
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SolarVisuals.clear();
        SolarSky.reset();
    }
}
