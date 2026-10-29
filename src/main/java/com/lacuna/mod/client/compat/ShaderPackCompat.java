package com.lacuna.mod.client.compat;

import com.lacuna.mod.Lacuna;
import java.lang.reflect.Method;
import net.minecraft.Util;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Shader-pack detection for Iris (and Oculus, which ships the same API), by reflection only: no compile-time or
 * runtime dependency. While a shader pack draws the world into its own buffers, Lacuna's stencil tricks and custom
 * core shaders would draw into the wrong targets (black, missing or smeared effects), so while {@link #active()}:
 * <ul>
 *   <li>the stencil features stay off: the Divine Glass X-ray window falls back to the outline mode and
 *       {@code StencilSupport} hands out no stencil (Mirror Glass reflections gate on this too);</li>
 *   <li>every custom-shader render type resolves to its plain fallback ({@link #usable}): vanilla position / texture /
 *       colour quads, the end portal, the textured beams, which shader packs do handle.</li>
 * </ul>
 * Packs can be switched in game, so the answer is cached for a second, then asked again. If Iris or Oculus is loaded
 * but its API cannot be read, a pack is assumed (the plain look is always safe). Client only, render thread.
 */
public final class ShaderPackCompat {
    /** Iris' public API (Iris 1.1+ on every loader; Oculus bundles the same package). */
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String[] MOD_IDS = {"iris", "oculus"};
    private static final long RECHECK_MS = 1000L;

    private static final int UNRESOLVED = 0;
    private static final int API_FOUND = 1;
    private static final int NO_SHADER_MOD = 2;
    private static final int API_BROKEN = 3;

    private static int mode = UNRESOLVED;
    @Nullable
    private static Object api;
    @Nullable
    private static Method shaderPackInUse;
    private static long nextCheck = Long.MIN_VALUE;
    private static boolean active;
    private static boolean loggedOn;
    private static boolean loggedOff;

    private ShaderPackCompat() {}

    /** True while a shader pack renders the world (asked at most once a second). */
    public static boolean active() {
        long now = Util.getMillis();
        if (now >= nextCheck) {
            nextCheck = now + RECHECK_MS;
            boolean was = active;
            active = query();
            if (active != was) {
                logChange();
            }
        }
        return active;
    }

    /** True when a custom core shader loaded and may be used now (no shader pack in use). */
    public static boolean usable(@Nullable ShaderInstance shader) {
        return shader != null && !active();
    }

    private static boolean query() {
        if (mode == UNRESOLVED) {
            resolve();
        }
        if (mode == API_FOUND) {
            try {
                return Boolean.TRUE.equals(shaderPackInUse.invoke(api));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                mode = API_BROKEN;
                Lacuna.LOGGER.warn("Lacuna: the Iris/Oculus API failed ({}); assuming a shader pack is in use", e.toString());
            }
        }
        return mode == API_BROKEN;
    }

    private static void resolve() {
        boolean shaderMod = false;
        for (String id : MOD_IDS) {
            shaderMod |= ModList.get() != null && ModList.get().isLoaded(id);
        }
        try {
            Class<?> type = Class.forName(API, true, ShaderPackCompat.class.getClassLoader());
            Object instance = type.getMethod("getInstance").invoke(null);
            Method inUse = type.getMethod("isShaderPackInUse");
            if (instance != null) {
                api = instance;
                shaderPackInUse = inUse;
                mode = API_FOUND;
                Lacuna.LOGGER.info("Lacuna: Iris/Oculus API found; stencil effects and custom shaders follow the shader pack state");
                return;
            }
        } catch (ClassNotFoundException e) {
            // no Iris API on the class path
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Lacuna.LOGGER.warn("Lacuna: the Iris/Oculus API could not be read ({})", e.toString());
        }
        mode = shaderMod ? API_BROKEN : NO_SHADER_MOD;
        if (mode == API_BROKEN) {
            Lacuna.LOGGER.warn("Lacuna: Iris/Oculus is loaded without a readable API; assuming a shader pack is in use");
        }
    }

    private static void logChange() {
        if (active && !loggedOn) {
            loggedOn = true;
            Lacuna.LOGGER.info("Lacuna: shader pack in use: the Divine Glass X-ray window and Mirror Glass reflections are off "
                    + "(outlines instead) and Lacuna's custom shaders use their plain fallbacks");
        } else if (!active && !loggedOff) {
            loggedOff = true;
            Lacuna.LOGGER.info("Lacuna: no shader pack in use any more: stencil effects and custom shaders are back");
        }
    }
}
