package com.lacuna.mod.client.eclipse;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.registry.ModEffects;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Screen atmosphere while the local player is in Shadow Walk: the world dims, the edges sink into a dark vignette
 * and a thin crimson rim glows and pulses along the screen border. Eases in and out; scaled by the vanilla
 * Distortion Effects slider and the Lacuna screen-effects toggle.
 */
final class ShadowWalkOverlay {
    private static final ResourceLocation VIGNETTE = Lacuna.id("textures/effect/shadow_vignette.png");
    private static final ResourceLocation RIM = Lacuna.id("textures/effect/shadow_rim.png");
    private static float current;
    private static float previous;
    private static int time;

    private ShadowWalkOverlay() {}

    static void tick(Minecraft mc) {
        boolean on = mc.player != null && mc.player.hasEffect(ModEffects.SHADOW_WALK);
        previous = current;
        current += ((on ? 1.0F : 0.0F) - current) * (on ? 0.25F : 0.14F);
        if (!on && current < 0.003F) {
            current = 0.0F;
        }
        time++;
    }

    static void reset() {
        current = 0.0F;
        previous = 0.0F;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        if (current <= 0.0F && previous <= 0.0F) {
            return;
        }
        if (!LacunaClientConfig.get(LacunaClientConfig.SCREEN_EFFECTS)) {
            return;
        }
        float scale = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        if (scale <= 0.0F) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float a = Mth.lerp(partial, previous, current) * Math.max(0.35F, scale);
        if (a <= 0.003F) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        g.fill(0, 0, w, h, FxDraw.argb(0.22F * a, 0x0A0410));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.015F, 0.0F, 0.03F, Mth.clamp(0.95F * a, 0.0F, 1.0F));
        g.blit(VIGNETTE, 0, 0, 0, 0.0F, 0.0F, w, h, w, h);
        // crimson rim hugging the border: a deep alpha-blended band plus a faint additive glow so it smoulders
        float pulse = 0.75F + 0.25F * Mth.sin((time + partial) * 0.18F);
        RenderSystem.setShaderColor(0.55F, 0.03F, 0.11F, Mth.clamp(0.9F * a * pulse, 0.0F, 1.0F));
        g.blit(RIM, 0, 0, 0, 0.0F, 0.0F, w, h, w, h);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(0.7F, 0.05F, 0.15F, Mth.clamp(0.35F * a * pulse, 0.0F, 1.0F));
        g.blit(RIM, 0, 0, 0, 0.0F, 0.0F, w, h, w, h);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }
}
