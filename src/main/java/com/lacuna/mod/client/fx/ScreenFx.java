package com.lacuna.mod.client.fx;

import com.lacuna.mod.Lacuna;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Screen-space feedback: camera shake, full-screen flashes and held overlays (tints / vignettes). Every effect is
 * scaled by the vanilla "Distortion Effects" accessibility slider so players can turn it down.
 * <ul>
 *   <li>{@link #shake(float, int)}: decaying jitter of yaw/pitch/roll (degrees).</li>
 *   <li>{@link #flash(int, float, int)}: a colour flash that fades out.</li>
 *   <li>{@link #hold(String, int, float, boolean)}: an overlay that must be refreshed every tick; it eases in and
 *       eases out once no longer refreshed.</li>
 * </ul>
 */
public final class ScreenFx {
    public static final String FEATURE = "screen_fx";
    private static final ResourceLocation VIGNETTE = Lacuna.id("textures/effect/vignette.png");

    private static float shakeAmp;
    private static int shakeTicks;
    private static int shakeTotal;
    private static int flashRgb;
    private static float flashAlpha;
    private static int flashTicks;
    private static int flashTotal;
    private static final Map<String, Overlay> OVERLAYS = new HashMap<>();

    private static final class Overlay {
        int rgb;
        boolean vignette;
        float target;
        float current;
        float previous;
        boolean refreshed;
    }

    private ScreenFx() {}

    private static float scale() {
        if (!com.lacuna.mod.client.LacunaClientConfig.get(com.lacuna.mod.client.LacunaClientConfig.SCREEN_EFFECTS)) {
            return 0.0F;
        }
        return Minecraft.getInstance().options.screenEffectScale().get().floatValue();
    }

    /** Camera shake of up to {@code degrees}, decaying over {@code ticks}. Stronger calls override weaker ones. */
    public static void shake(float degrees, int ticks) {
        float remaining = shakeTicks > 0 ? shakeAmp * shakeTicks / (float) shakeTotal : 0.0F;
        if (degrees >= remaining) {
            shakeAmp = degrees;
            shakeTicks = ticks;
            shakeTotal = Math.max(1, ticks);
        }
    }

    /** Full-screen flash of colour {@code rgb} starting at {@code alpha}, fading over {@code ticks}. */
    public static void flash(int rgb, float alpha, int ticks) {
        float remaining = flashTicks > 0 ? flashAlpha * flashTicks / (float) flashTotal : 0.0F;
        if (alpha >= remaining) {
            flashRgb = rgb;
            flashAlpha = alpha;
            flashTicks = ticks;
            flashTotal = Math.max(1, ticks);
        }
    }

    /**
     * Keeps overlay {@code key} at {@code strength} (0..1) while called every tick; {@code vignette} darkens/tints the
     * screen edges only, otherwise the whole screen is tinted.
     */
    public static void hold(String key, int rgb, float strength, boolean vignette) {
        Overlay o = OVERLAYS.computeIfAbsent(key, k -> new Overlay());
        o.rgb = rgb;
        o.vignette = vignette;
        o.target = Mth.clamp(strength, 0.0F, 1.0F);
        o.refreshed = true;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        if (shakeTicks > 0) {
            shakeTicks--;
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
        Iterator<Overlay> it = OVERLAYS.values().iterator();
        while (it.hasNext()) {
            Overlay o = it.next();
            if (!o.refreshed) {
                o.target = 0.0F;
            }
            o.refreshed = false;
            o.previous = o.current;
            o.current += (o.target - o.current) * 0.18F;
            if (o.target == 0.0F && o.current < 0.004F) {
                it.remove();
            }
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0) {
            return;
        }
        float t = (float) ((Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0)
                + event.getPartialTick());
        float amp = shakeAmp * (shakeTicks - (float) event.getPartialTick()) / shakeTotal * scale();
        amp = Math.max(0.0F, amp);
        event.setYaw(event.getYaw() + amp * Mth.sin(t * 2.7F) * 0.6F);
        event.setPitch(event.getPitch() + amp * Mth.sin(t * 3.3F + 1.3F) * 0.6F);
        event.setRoll(event.getRoll() + amp * Mth.sin(t * 2.1F + 2.1F) * 0.4F);
    }

    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, Lacuna.id("screen_fx"), ScreenFx::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (OVERLAYS.isEmpty() && flashTicks <= 0) {
            return;
        }
        FxGuard.run(FEATURE, () -> {
            float partial = delta.getGameTimeDeltaPartialTick(false);
            float s = scale();
            if (s <= 0.0F) {
                return;
            }
            s = Math.max(0.25F, s);
            int w = g.guiWidth();
            int h = g.guiHeight();
            for (Overlay o : OVERLAYS.values()) {
                float a = Mth.lerp(partial, o.previous, o.current) * s;
                if (a <= 0.003F) {
                    continue;
                }
                if (o.vignette) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderColor(((o.rgb >> 16) & 0xFF) / 255.0F, ((o.rgb >> 8) & 0xFF) / 255.0F,
                            (o.rgb & 0xFF) / 255.0F, Mth.clamp(a, 0.0F, 1.0F));
                    try {
                        g.blit(VIGNETTE, 0, 0, 0, 0.0F, 0.0F, w, h, w, h);
                    } finally {
                        // never leave the tint behind: it would colour everything drawn after it
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                } else {
                    g.fill(0, 0, w, h, FxDraw.argb(a, o.rgb));
                }
            }
            if (flashTicks > 0) {
                float a = flashAlpha * (flashTicks - partial) / flashTotal * s;
                if (a > 0.003F) {
                    g.fill(0, 0, w, h, FxDraw.argb(Mth.clamp(a, 0.0F, 1.0F), flashRgb));
                }
            }
        });
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        OVERLAYS.clear();
        shakeTicks = 0;
        flashTicks = 0;
    }
}
