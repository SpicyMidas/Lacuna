package com.lacuna.mod.client.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Tidecaller's water colours, taken from the water of the place: vanilla's own biome water blend
 * ({@link BiomeColors#getAverageWaterColor}, the colour water blocks get - swamps, warm and frozen oceans, mangroves,
 * Lacuna's own biomes) sampled where an effect appears. The whole palette is derived from it with the shading the
 * tide was designed with: the colour is first brought to the wave's body brightness (luminance 0.46), then
 * <ul>
 *   <li>{@link #DEEP}: more saturated, at 0.42 of the body (the eye, the funnel's core, the hollow of the wave);</li>
 *   <li>{@link #BODY}: the water itself;</li>
 *   <li>{@link #LIGHT}, {@link #PALE}, {@link #FOAM}, {@link #GLINT}: brightened and mixed 24 / 68 / 90 / 50 % toward
 *       white - foam stays near-white but tinted.</li>
 * </ul>
 * The {@code lit} palette is also dimmed by the light of the place (the effects are drawn unlit, unlike blocks), so
 * the tide is not glowing at night; vanilla particles get the {@code raw} palette since the game lights them. The
 * {@code tide_vortex} shader derives the same tones from the lit body colour alone.
 */
final class TideWater {
    static final int DEEP = 0;
    static final int BODY = 1;
    static final int LIGHT = 2;
    static final int PALE = 3;
    static final int FOAM = 4;
    static final int GLINT = 5;
    private static final float BODY_LUM = 0.46F;
    private static final int VANILLA_WATER = 0x3F76E4;

    private float r;
    private float g;
    private float b;
    private float light = 1.0F;
    private float tr;
    private float tg;
    private float tb;
    private float tLight = 1.0F;
    private final int[] lit = new int[6];
    private final int[] raw = new int[6];

    private TideWater() {}

    /** The water at {@code pos} (sampled once; {@link #resample} + {@link #ease} follow a moving effect). */
    static TideWater at(@Nullable ClientLevel level, Vec3 pos) {
        TideWater w = new TideWater();
        w.resample(level, pos);
        w.r = w.tr;
        w.g = w.tg;
        w.b = w.tb;
        w.light = w.tLight;
        w.rebuild();
        return w;
    }

    /** Takes the water colour and light at {@code pos} as the new target. */
    void resample(@Nullable ClientLevel level, Vec3 pos) {
        int c = VANILLA_WATER;
        float l = 1.0F;
        if (level != null) {
            BlockPos at = BlockPos.containing(pos);
            c = BiomeColors.getAverageWaterColor(level, at);
            l = ambient(level, at.above());
        }
        float cr = ((c >> 16) & 0xFF) / 255.0F;
        float cg = ((c >> 8) & 0xFF) / 255.0F;
        float cb = (c & 0xFF) / 255.0F;
        float k = Mth.clamp(BODY_LUM / Math.max(0.02F, lum(cr, cg, cb)), 0.5F, 2.4F);
        tr = Math.min(1.0F, cr * k);
        tg = Math.min(1.0F, cg * k);
        tb = Math.min(1.0F, cb * k);
        tLight = l;
    }

    /** Moves the colour a fraction {@code k} toward the last sample. */
    void ease(float k) {
        r += (tr - r) * k;
        g += (tg - g) * k;
        b += (tb - b) * k;
        light += (tLight - light) * k;
        rebuild();
    }

    /** Brightness of the place, like the lightmap: block light or sky light under the current daylight, 0.3..1. */
    private static float ambient(ClientLevel level, BlockPos pos) {
        float block = LightTexture.getBrightness(level.dimensionType(), level.getBrightness(LightLayer.BLOCK, pos));
        float sky = LightTexture.getBrightness(level.dimensionType(), level.getBrightness(LightLayer.SKY, pos)) * level.getSkyDarken(1.0F);
        return Mth.clamp(0.22F + 0.85F * Math.max(block, sky), 0.3F, 1.0F);
    }

    private static float lum(float r, float g, float b) {
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    private void rebuild() {
        derive(r, g, b, light, lit);
        derive(r, g, b, 1.0F, raw);
    }

    private static void derive(float r, float g, float b, float light, int[] out) {
        r *= light;
        g *= light;
        b *= light;
        float l = lum(r, g, b);
        float w = light;
        out[DEEP] = rgb((l + (r - l) * 1.3F) * 0.42F, (l + (g - l) * 1.3F) * 0.42F, (l + (b - l) * 1.3F) * 0.42F);
        out[BODY] = rgb(r, g, b);
        out[LIGHT] = toward(r, g, b, 1.4F, w, 0.24F);
        out[PALE] = toward(r, g, b, 1.3F, w, 0.68F);
        out[FOAM] = toward(r, g, b, 1.25F, w, 0.9F);
        out[GLINT] = toward(r, g, b, 1.5F, w, 0.5F);
    }

    private static int toward(float r, float g, float b, float gain, float white, float k) {
        return rgb(Mth.lerp(k, r * gain, white), Mth.lerp(k, g * gain, white), Mth.lerp(k, b * gain, white));
    }

    private static int rgb(float r, float g, float b) {
        return (Mth.clamp((int) (r * 255.0F + 0.5F), 0, 255) << 16) | (Mth.clamp((int) (g * 255.0F + 0.5F), 0, 255) << 8)
                | Mth.clamp((int) (b * 255.0F + 0.5F), 0, 255);
    }

    /** Lit colour of a palette slot (for geometry drawn by the effects). */
    int get(int slot) {
        return lit[slot];
    }

    /** Unlit colour of a palette slot (for vanilla particles, which the game lights itself). */
    int raw(int slot) {
        return raw[slot];
    }

    static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /** Storm-cloud grey with a hint of the water (lit): the canopy over the spout. */
    int cloud(float brightness) {
        int body = lit[BODY];
        float l = lum(((body >> 16) & 0xFF) / 255.0F, ((body >> 8) & 0xFF) / 255.0F, (body & 0xFF) / 255.0F);
        int grey = rgb(l * brightness, l * brightness, l * brightness);
        return mix(grey, lit[DEEP], 0.4F);
    }

    // ------------------------------------------------------------------ fallback shading (no shader)

    /**
     * CPU stand-in for the shader when it did not load: water at {@code edge} (0 face-on .. 1 grazing) turns paler and
     * denser like the wave does; the mist and droplets keep their colour.
     */
    static int fallback(int argb, int mode, float edge) {
        if (mode == TideMesh.FLAT || mode == TideMesh.PUFF) {
            return argb;
        }
        int rgb = argb & 0xFFFFFF;
        float a = (argb >>> 24) / 255.0F;
        float e = edge * edge;
        int lightc = mix(rgb, 0xFFFFFF, 0.45F);
        int deep = mix(rgb, 0x000000, 0.45F);
        int col = mode == TideMesh.CANOPY ? mix(mix(rgb, 0x404040, 0.6F), 0x000000, 0.3F) : mix(mode == TideMesh.CORE || mode == TideMesh.POOL ? deep : rgb, lightc, e);
        float alpha = mode == TideMesh.CANOPY || mode == TideMesh.POOL ? a * 0.8F : a * (0.35F + 0.45F * e);
        return (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24) | col;
    }

    // ------------------------------------------------------------------ particles in the water's colour

    /** Like {@code level.addParticle}: skipped far from the camera and thinned by the particle setting. */
    @Nullable
    static Particle spawn(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z) > 1024.0) {
            return null;
        }
        switch (mc.options.particles().get()) {
            case MINIMAL -> {
                return null;
            }
            case DECREASED -> {
                if (mc.level.random.nextInt(3) == 0) {
                    return null;
                }
            }
            default -> { }
        }
        return mc.particleEngine.createParticle(options, x, y, z, vx, vy, vz);
    }

    private void tint(@Nullable Particle p, int slot) {
        if (p != null) {
            int c = raw[slot];
            p.setColor(((c >> 16) & 0xFF) / 255.0F, ((c >> 8) & 0xFF) / 255.0F, (c & 0xFF) / 255.0F);
        }
    }

    private static final BlockParticleOption WATER_BIT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WATER.defaultBlockState());

    /** A pixel of water flung at (vx, vy, vz): a water-block particle in this water's colour (falls, lit by the game). */
    void splash(double x, double y, double z, double vx, double vy, double vz, int slot) {
        Particle p = spawn(WATER_BIT, x, y, z, vx, vy, vz);
        if (p != null) {
            p.setParticleSpeed(vx, vy, vz);
            tint(p, slot);
        }
    }

    /** A falling drop of this water (vanilla's falling water, recoloured). */
    void drip(double x, double y, double z) {
        tint(spawn(ParticleTypes.FALLING_WATER, x, y, z, 0.0, 0.0, 0.0), LIGHT);
    }

    /** Any other white or tinted vanilla particle, recoloured to a palette slot. */
    void particle(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz, int slot) {
        tint(spawn(options, x, y, z, vx, vy, vz), slot);
    }
}
