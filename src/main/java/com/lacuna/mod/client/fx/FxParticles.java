package com.lacuna.mod.client.fx;

import com.lacuna.mod.registry.ModParticles;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client providers for {@link ModParticles}: one data-driven particle class, styled per type. */
public final class FxParticles {
    /** Additive particle sheet: sprites add light instead of covering what is behind them. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public boolean isTranslucent() {
            return true;
        }

        @Override
        public String toString() {
            return "LACUNA_ADDITIVE";
        }
    };

    /**
     * @param grow     size multiplier reached at the end of life (1 = constant, 0 = shrink away, 2 = double)
     * @param animated choose the sprite by age (else a random sprite of the set)
     */
    public record Style(int minLife, int maxLife, float size, float gravity, float friction, boolean fullBright, boolean additive,
                       boolean animated, float spin, float grow, float alpha, boolean physics, int rgb, float colorJitter) {}

    public static final Style LIGHT_MOTE = new Style(18, 36, 0.14F, -0.004F, 0.94F, true, true, false, 0.0F, 0.0F, 0.9F, false, 0xFFF2D0, 0.06F);
    public static final Style SOLAR_GLINT = new Style(8, 14, 0.22F, 0.0F, 0.85F, true, true, true, 0.08F, 0.4F, 1.0F, false, 0xFFFFFF, 0.0F);
    public static final Style CRIMSON_SPARK = new Style(10, 22, 0.07F, 0.05F, 0.94F, true, true, true, 0.0F, 0.3F, 1.0F, true, 0xFFFFFF, 0.08F);
    public static final Style SHADOW_MOTE = new Style(14, 28, 0.3F, -0.006F, 0.9F, false, false, true, 0.05F, 1.3F, 0.85F, false, 0xFFFFFF, 0.05F);
    public static final Style UMBRAL_SLASH = new Style(6, 9, 0.9F, 0.0F, 0.6F, true, false, true, 0.0F, 1.25F, 1.0F, false, 0xFFFFFF, 0.0F);
    public static final Style UMBRAL_SHARD = new Style(18, 34, 0.1F, 0.05F, 0.96F, true, false, false, 0.25F, 0.5F, 1.0F, true, 0xFFFFFF, 0.1F);
    public static final Style UMBRAL_WISP = new Style(10, 18, 0.09F, 0.0F, 0.9F, true, true, false, 0.0F, 0.2F, 1.0F, false, 0xA874FF, 0.1F);
    public static final Style MIST = new Style(120, 220, 2.6F, -0.0004F, 0.995F, false, false, false, 0.004F, 1.4F, 0.32F, false, 0xFFFFFF, 0.04F);

    private FxParticles() {}

    public static void register(RegisterParticleProvidersEvent event) {
        provider(event, ModParticles.LIGHT_MOTE.get(), LIGHT_MOTE);
        provider(event, ModParticles.SOLAR_GLINT.get(), SOLAR_GLINT);
        provider(event, ModParticles.CRIMSON_SPARK.get(), CRIMSON_SPARK);
        provider(event, ModParticles.SHADOW_MOTE.get(), SHADOW_MOTE);
        provider(event, ModParticles.UMBRAL_SLASH.get(), UMBRAL_SLASH);
        provider(event, ModParticles.ECLIPSE_SLASH.get(), UMBRAL_SLASH);
        provider(event, ModParticles.UMBRAL_SHARD.get(), UMBRAL_SHARD);
        provider(event, ModParticles.MIST.get(), MIST);
        provider(event, ModParticles.UMBRAL_WISP.get(), UMBRAL_WISP);
    }

    private static void provider(RegisterParticleProvidersEvent event, SimpleParticleType type, Style style) {
        event.registerSpriteSet(type, sprites -> (t, level, x, y, z, dx, dy, dz) -> new FxParticle(level, x, y, z, dx, dy, dz, sprites, style));
    }

    /** One particle class for every Lacuna particle type; behaviour comes from its {@link Style}. */
    public static final class FxParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final Style style;
        private final float baseSize;
        private final float spinSpeed;

        FxParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, Style style) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.style = style;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.lifetime = style.minLife() + this.random.nextInt(Math.max(1, style.maxLife() - style.minLife() + 1));
            this.baseSize = style.size() * (0.75F + this.random.nextFloat() * 0.5F);
            this.quadSize = this.baseSize;
            this.gravity = style.gravity();
            this.friction = style.friction();
            this.hasPhysics = style.physics();
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.spinSpeed = style.spin() * (this.random.nextBoolean() ? 1.0F : -1.0F);
            float j = style.colorJitter();
            float k = 1.0F - j + this.random.nextFloat() * j * 2.0F;
            this.rCol = Mth.clamp(((style.rgb() >> 16) & 0xFF) / 255.0F * k, 0.0F, 1.0F);
            this.gCol = Mth.clamp(((style.rgb() >> 8) & 0xFF) / 255.0F * k, 0.0F, 1.0F);
            this.bCol = Mth.clamp((style.rgb() & 0xFF) / 255.0F * k, 0.0F, 1.0F);
            this.alpha = style.alpha();
            if (style.animated()) {
                this.setSpriteFromAge(sprites);
            } else {
                this.pickSprite(sprites);
            }
        }

        @Override
        public void tick() {
            super.tick();
            this.oRoll = this.roll;
            this.roll += this.spinSpeed;
            if (style.animated() && !this.removed) {
                this.setSpriteFromAge(sprites);
            }
            float life = (float) this.age / this.lifetime;
            float fadeIn = Math.min(1.0F, life / 0.12F);
            float fadeOut = life < 0.55F ? 1.0F : 1.0F - (life - 0.55F) / 0.45F;
            this.alpha = style.alpha() * Mth.clamp(Math.min(fadeIn, fadeOut), 0.0F, 1.0F);
        }

        @Override
        public float getQuadSize(float partialTick) {
            float life = Mth.clamp((this.age + partialTick) / this.lifetime, 0.0F, 1.0F);
            return this.baseSize * Mth.lerp(life, 1.0F, style.grow());
        }

        @Override
        protected int getLightColor(float partialTick) {
            return style.fullBright() ? 0xF000F0 : super.getLightColor(partialTick);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return style.additive() ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
