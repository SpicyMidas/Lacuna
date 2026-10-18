package com.lacuna.mod.client.solar;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;

/**
 * Where the vanilla sun is drawn this frame and what colour its light has, refreshed once per frame by the solar
 * passes (no allocation). The direction is exactly {@code LevelRenderer#renderSky}'s: the sun quad sits at
 * {@code (0, 100, 0)} rotated by {@code X(timeOfDay * 360)} then {@code Y(-90)}, i.e. towards
 * {@code (-sin a, cos a, 0)} with {@code a = timeOfDay * 2 pi}, at infinity (the sky is drawn with the camera rotation
 * only), and {@code timeOfDay} comes from the level's day time, which {@link SolarSky#onFrame} moves every frame along
 * the focus sweep. So anything drawn at {@code cameraRelative = distance * dir} lands on the sun's disc.
 * <p>
 * Colour: warm white-gold while the sun is high, shifting to the vanilla sunrise / sunset fan colour
 * ({@link DimensionSpecialEffects#getSunriseColor}, weighted by its own alpha) as it nears the horizon. {@link #up}
 * fades the sunlight out while the sun sinks below the horizon; dimensions that draw no sun count as night (like the
 * Divine Magnifying Glass, whose light turns white there).
 */
final class SunLight {
    static final int WHITE = 0xFFFFFF;
    /** Angular half-size of the sun's bright disc (8 of the 32 texture pixels of the 60-wide quad at distance 100). */
    static final double DISC = 0.075;

    /** Unit direction from the camera to the centre of the drawn sun ({@code z} is always 0). */
    static double dirX;
    static double dirY = 1.0;
    /** 0 while the sun is below the horizon (or there is no sun), 1 once its disc is clear of the horizon. */
    static float up;
    /** Weight of the sunrise / sunset colour (0 with the sun high in the sky, up to 1 on the horizon). */
    private static float sunset;
    /** Sunrise / sunset colour, brightened so its strongest channel is 1. */
    private static float sunsetR = 1.0F;
    private static float sunsetG = 0.45F;
    private static float sunsetB = 0.24F;

    private SunLight() {}

    static void update(ClientLevel level, float partialTick) {
        DimensionSpecialEffects effects = level.effects();
        if (effects.skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            dirX = 0.0;
            dirY = 1.0;
            up = 0.0F;
            sunset = 0.0F;
            return;
        }
        float timeOfDay = level.getTimeOfDay(partialTick);
        double angle = timeOfDay * Math.PI * 2.0;
        dirX = -Math.sin(angle);
        dirY = Math.cos(angle);
        up = smooth((float) ((dirY + DISC * 0.6) / (DISC * 1.6)));
        float[] fan = effects.getSunriseColor(timeOfDay, partialTick);
        if (fan == null) {
            sunset = 0.0F;
            return;
        }
        float max = Math.max(fan[0], Math.max(fan[1], Math.max(fan[2], 1.0E-3F)));
        sunsetR = fan[0] / max;
        sunsetG = fan[1] / max;
        sunsetB = fan[2] / max;
        sunset = Mth.clamp(fan[3], 0.0F, 1.0F);
    }

    /**
     * The sunlight colour for a layer whose midday colour is {@code noonRgb}: moves to the sunset colour (lightened
     * towards warm white by {@code whiteness}, 0..1, for the brighter inner layers) as the sun nears the horizon.
     */
    static int shade(int noonRgb, float whiteness) {
        if (sunset <= 0.002F) {
            return noonRgb & 0xFFFFFF;
        }
        float r = Mth.lerp(whiteness, sunsetR, 1.0F);
        float g = Mth.lerp(whiteness, sunsetG, 0.9F);
        float b = Mth.lerp(whiteness, sunsetB, 0.74F);
        return mix(noonRgb, rgb(r, g, b), sunset);
    }

    /** Like {@link #shade} by day, white light at night (the sun below the horizon or no sun at all). */
    static int lit(int noonRgb, float whiteness) {
        return mix(WHITE, shade(noonRgb, whiteness), up);
    }

    static int mix(int a, int b, float t) {
        if (t <= 0.0F) {
            return a & 0xFFFFFF;
        }
        if (t >= 1.0F) {
            return b & 0xFFFFFF;
        }
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    private static int rgb(float r, float g, float b) {
        return ((int) (Mth.clamp(r, 0.0F, 1.0F) * 255.0F) << 16) | ((int) (Mth.clamp(g, 0.0F, 1.0F) * 255.0F) << 8)
                | (int) (Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
