package com.lacuna.mod.client.eclipse;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.fx.FxDraw;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Screen of a Shadow Possession. For the shadow: the world seen through the host's eyes sinks into a dark violet,
 * umbral vignette that throbs like a heartbeat (quicker as the host resists), with the host's name and health and its
 * resistance at the top; once the host fights back, a crimson rim and cracks creep in from the edges as the expulsion
 * countdown runs out, and the heartbeat is heard. For a possessed player: a lighter umbral vignette and what is
 * happening to it (struggle to cast the shadow out). The vignette follows the Lacuna screen-effects toggle and the
 * vanilla Distortion Effects slider; the HUD text always shows.
 */
final class PossessionOverlay {
    private static final ResourceLocation VIGNETTE = Lacuna.id("textures/effect/shadow_vignette.png");
    private static final ResourceLocation RIM = Lacuna.id("textures/effect/shadow_rim.png");
    private static final int CRACKS = 8;
    private static float current;
    private static float previous;
    private static float beat;
    private static float beatPrev;
    private static long crackSeed = 1L;

    private PossessionOverlay() {}

    static void onEnter() {
        crackSeed = System.nanoTime();
    }

    static void reset() {
        current = 0.0F;
        previous = 0.0F;
    }

    private static boolean shadow() {
        return PossessionClient.possessing();
    }

    private static float countdown() {
        return shadow() ? PossessionClient.countdown : PossessionClient.hostCountdown;
    }

    private static float hold() {
        return shadow() ? PossessionClient.hold : PossessionClient.hostHold;
    }

    static void tick(Minecraft mc) {
        boolean on = PossessionClient.possessing() || PossessionClient.possessed;
        previous = current;
        current += ((on ? 1.0F : 0.0F) - current) * (on ? 0.2F : 0.15F);
        if (!on && current < 0.003F) {
            current = 0.0F;
        }
        float cd = countdown();
        boolean fighting = cd >= 0.0F;
        float bpm = fighting ? 100.0F + 45.0F * (1.0F - cd) : 60.0F + 30.0F * hold();
        beatPrev = beat;
        beat += bpm / 60.0F / 20.0F;
        if (beat >= 1.0F) {
            beat -= 1.0F;
            beatPrev -= 1.0F;
            if (on && fighting && mc.getSoundManager() != null) {
                float volume = shadow() ? 0.55F : 0.35F;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.9F + 0.3F * (1.0F - cd), volume));
            }
        }
    }

    /** Two thumps per beat ("lub-dub"), 0..1. */
    private static float pulse(float phase) {
        float a = (phase - 0.06F) / 0.05F;
        float b = (phase - 0.24F) / 0.06F;
        return Math.min(1.0F, (float) (Math.exp(-a * a) + 0.6 * Math.exp(-b * b)));
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        if (current <= 0.0F && previous <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float a = Mth.lerp(partial, previous, current);
        if (a <= 0.003F) {
            return;
        }
        float phase = Mth.lerp(partial, beatPrev, beat);
        float pulse = pulse(phase < 0.0F ? phase + 1.0F : phase);
        float cd = countdown();
        float scale = mc.options.screenEffectScale().get().floatValue();
        if (LacunaClientConfig.get(LacunaClientConfig.SCREEN_EFFECTS) && scale > 0.0F) {
            vignette(g, a * Math.max(0.35F, scale), pulse, cd, shadow());
        }
        if (shadow()) {
            shadowHud(g, mc, a, pulse, cd, partial);
        } else if (PossessionClient.possessed) {
            hostHud(g, mc, a, pulse, cd);
        }
    }

    // ------------------------------------------------------------------------------------------ vignette

    private static void vignette(GuiGraphics g, float a, float pulse, float cd, boolean shadow) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        boolean fighting = cd >= 0.0F;
        float strength = shadow ? 1.0F : 0.6F;
        g.fill(0, 0, w, h, FxDraw.argb(0.14F * a * strength, 0x12052A));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.07F, 0.01F, 0.16F, Mth.clamp(a * strength * (0.78F + 0.22F * pulse), 0.0F, 1.0F));
        g.blit(VIGNETTE, 0, 0, 0, 0.0F, 0.0F, w, h, w, h);
        if (fighting) {
            float grow = 0.45F + 0.55F * (1.0F - cd);
            RenderSystem.setShaderColor(0.55F, 0.03F, 0.12F, Mth.clamp(a * grow * (0.45F + 0.55F * pulse), 0.0F, 1.0F));
            g.blit(RIM, 0, 0, 0, 0.0F, 0.0F, w, h, w, h);
        }
        // the heartbeat glows violet through the edges (additive)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        float glow = (fighting ? 0.42F : 0.28F) * strength * pulse;
        RenderSystem.setShaderColor(0.42F, 0.1F, 0.72F, Mth.clamp(a * glow, 0.0F, 1.0F));
        g.blit(VIGNETTE, 0, 0, 0, 0.0F, 0.0F, w, h, w, h);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        if (fighting) {
            cracks(g, w, h, a, 1.0F - cd, pulse);
        }
    }

    /** Jagged cracks creeping in from the screen edges as the expulsion nears. */
    private static void cracks(GuiGraphics g, int w, int h, float a, float progress, float pulse) {
        RandomSource r = RandomSource.create(crackSeed);
        float maxLen = Math.min(w, h) * 0.42F;
        int dark = FxDraw.argb(0.85F * a, 0x14000A);
        int hot = FxDraw.argb((0.35F + 0.4F * pulse) * a, 0xC0203A);
        for (int i = 0; i < CRACKS; i++) {
            int edge = r.nextInt(4);
            float t = 0.1F + r.nextFloat() * 0.8F;
            float x = edge == 0 ? t * w : edge == 1 ? w - 1 : edge == 2 ? t * w : 0;
            float y = edge == 0 ? 0 : edge == 1 ? t * h : edge == 2 ? h - 1 : t * h;
            float angle = (float) Math.atan2(h * 0.5F - y, w * 0.5F - x) + (r.nextFloat() - 0.5F) * 0.9F;
            float len = maxLen * (0.55F + 0.45F * r.nextFloat()) * Mth.clamp(progress * 1.15F - i * 0.04F, 0.0F, 1.0F);
            float walked = 0.0F;
            while (walked < len) {
                float step = 4.0F + r.nextFloat() * 5.0F;
                angle += (r.nextFloat() - 0.5F) * 1.1F;
                float nx = x + Mth.cos(angle) * step;
                float ny = y + Mth.sin(angle) * step;
                line(g, x, y, nx, ny, dark, hot);
                if (r.nextFloat() < 0.18F) {
                    float ba = angle + (r.nextBoolean() ? 0.9F : -0.9F);
                    line(g, nx, ny, nx + Mth.cos(ba) * step * 0.8F, ny + Mth.sin(ba) * step * 0.8F, dark, hot);
                }
                x = nx;
                y = ny;
                walked += step;
            }
        }
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int dark, int hot) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        for (int s = 0; s <= steps; s++) {
            int x = Math.round(Mth.lerp(s / (float) steps, x0, x1));
            int y = Math.round(Mth.lerp(s / (float) steps, y0, y1));
            g.fill(x + 1, y, x + 2, y + 1, hot);
            g.fill(x, y, x + 1, y + 1, dark);
        }
    }

    // ------------------------------------------------------------------------------------------ hud

    private static int text(float alpha, int rgb) {
        return FxDraw.argb(Mth.clamp(alpha, 0.05F, 1.0F), rgb);
    }

    private static void bar(GuiGraphics g, int x, int y, int width, float fill, int rgb, float a) {
        g.fill(x - 1, y - 1, x + width + 1, y + 4, FxDraw.argb(0.6F * a, 0x050208));
        int filled = Math.round(width * Mth.clamp(fill, 0.0F, 1.0F));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + 3, FxDraw.argb(0.95F * a, rgb));
            g.fill(x, y, x + filled, y + 1, FxDraw.argb(0.5F * a, 0xFFFFFF & (rgb | 0x404040)));
        }
    }

    private static int mix(int c0, int c1, float t) {
        int r = (int) Mth.lerp(t, (c0 >> 16) & 0xFF, (c1 >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (c0 >> 8) & 0xFF, (c1 >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, c0 & 0xFF, c1 & 0xFF);
        return r << 16 | gr << 8 | b;
    }

    private static String seconds(float countdown, boolean playerHost) {
        int max = playerHost ? Balance.POSSESS_EXPEL_TICKS_PLAYER : Balance.POSSESS_EXPEL_TICKS_MOB;
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0F, countdown) * max / 20.0F);
    }

    private static void shadowHud(GuiGraphics g, Minecraft mc, float a, float pulse, float cd, float partial) {
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int y = 8;
        Entity host = PossessionClient.host(mc);
        if (host != null) {
            g.drawCenteredString(font, host.getDisplayName(), cx, y, text(a, 0xE6D6FF));
            y += 11;
            if (host instanceof LivingEntity living) {
                float hp = living.getHealth();
                float max = Math.max(1.0F, living.getMaxHealth());
                int width = 92;
                bar(g, cx - width / 2, y, width, hp / max, 0xC0283E, a);
                String label = String.format(Locale.ROOT, "%.0f/%.0f", Math.max(0.0F, hp), max);
                g.drawString(font, label, cx + width / 2 + 4, y - 2, text(a * 0.9F, 0xF0A0B0));
                y += 8;
            }
        }
        boolean playerHost = host instanceof Player;
        int width = 92;
        if (cd < 0.0F) {
            float hold = PossessionClient.hold;
            g.drawCenteredString(font, Component.translatable("lacuna.possess.hud.resistance"), cx, y, text(a * 0.85F, 0xB9A3D9));
            y += 10;
            bar(g, cx - width / 2, y, width, hold, mix(0x7A3FC0, 0xC0203A, hold), a);
        } else {
            int jitter = PossessionClient.struggling ? (int) ((mc.level == null ? 0 : mc.level.random.nextFloat() - 0.5F) * 3.0F) : 0;
            Component msg = Component.translatable("lacuna.possess.hud.fighting", seconds(cd, playerHost));
            g.drawCenteredString(font, msg, cx + jitter, y, text(a, mix(0xE03A52, 0xFFD2D8, pulse * 0.6F)));
            y += 10;
            bar(g, cx - width / 2 + jitter, y, width, cd, 0xB3122E, a);
        }
        int hintTicks = PossessionClient.possessTicks;
        if (hintTicks < 120) {
            float fade = a * Mth.clamp((120 - hintTicks - partial) / 30.0F, 0.0F, 1.0F);
            if (fade > 0.05F) {
                g.drawCenteredString(font, Component.translatable("lacuna.possess.hud.hint"), cx, g.guiHeight() - 44, text(fade * 0.9F, 0xCBB8E8));
            }
        }
    }

    private static void hostHud(GuiGraphics g, Minecraft mc, float a, float pulse, float cd) {
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int y = 8;
        int width = 92;
        if (cd < 0.0F) {
            g.drawCenteredString(font, Component.translatable("lacuna.possess.host.seized"), cx, y, text(a, mix(0xC9A8FF, 0xFFFFFF, pulse * 0.3F)));
            y += 11;
            g.drawCenteredString(font, Component.translatable("lacuna.possess.host.hint"), cx, y, text(a * 0.85F, 0xB0A0C8));
            y += 11;
            bar(g, cx - width / 2, y, width, PossessionClient.hostHold, mix(0x7A3FC0, 0xE03A52, PossessionClient.hostHold), a);
        } else {
            g.drawCenteredString(font, Component.translatable("lacuna.possess.host.breaking", seconds(cd, true)), cx, y,
                    text(a, mix(0xE8C8FF, 0xFFFFFF, pulse * 0.5F)));
            y += 11;
            bar(g, cx - width / 2, y, width, 1.0F - cd, 0xE0B0FF, a);
        }
    }
}
