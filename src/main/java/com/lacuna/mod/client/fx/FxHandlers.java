package com.lacuna.mod.client.fx;

import com.lacuna.mod.network.FxPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Dispatches {@link FxPayload}s to the client effect registered for their {@code kind}. */
public final class FxHandlers {
    private static final Map<String, Consumer<FxPayload>> HANDLERS = new HashMap<>();

    private FxHandlers() {}

    public static void register(String kind, Consumer<FxPayload> handler) {
        HANDLERS.put(kind, handler);
    }

    public static void handle(FxPayload payload) {
        Consumer<FxPayload> handler = HANDLERS.get(payload.kind());
        if (handler == null || Minecraft.getInstance().level == null) {
            return;
        }
        // a failing handler costs this one effect only: it is logged and the kind keeps working (FxGuard, not SafeRun)
        FxGuard.run("fx_" + payload.kind(), () -> handler.accept(payload));
    }

    /** Called once from the client entry point: every feature registers its effect kinds here. */
    public static void bootstrap() {
        // feature effect registrations (one line per feature)
        DebugFx.register();
        RiteFx.register();
        com.lacuna.mod.client.gear.GravemawFx.register();
        com.lacuna.mod.client.gear.TidecallerFx.register();
        com.lacuna.mod.client.solar.SolarFx.register();
        com.lacuna.mod.client.eclipse.EclipseFx.register();
        com.lacuna.mod.client.gear.EmberTriangleFx.register();
        com.lacuna.mod.client.gear.StarlaceClient.registerFx();
        com.lacuna.mod.client.portal.PortalTransition.register();
    }
}
