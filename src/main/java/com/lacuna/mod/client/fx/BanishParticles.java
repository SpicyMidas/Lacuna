package com.lacuna.mod.client.fx;

import com.lacuna.mod.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client providers for the banishment particles, in the exilite palette sampled from the hand-drawn debris, shard,
 * ingot and totem textures (bone white, cream, sand, a warm umber rim and pearl glints of pink, mint and lavender;
 * never the totem's ruby): they replace the vanilla End and portal particles every crossing
 * to and from the Forgotten Land used to show.
 * <ul>
 *   <li>{@code banish_mote}: a tumbling stone chip of the totem, moving like the vanilla totem-of-undying particle (fast
 *       burst, heavy drag, slow fall) but with its course turning a little every tick, always the same way, so a burst
 *       pinwheels out like a swirl; it shrinks to a glint before it fades.</li>
 *   <li>{@code rift_spark}: a pale additive four-point spark with a pearl-lavender halo that drifts up and shrinks away (the
 *       rift's spiral, the portal surface, the held totem).</li>
 * </ul>
 */
public final class BanishParticles {
    public static final FxParticles.Style RIFT_SPARK = new FxParticles.Style(12, 24, 0.075F, -0.003F, 0.9F, true, true, true, 0.0F,
            0.35F, 1.0F, false, 0xFFFFFF, 0.05F);

    /** Tints over the bone-coloured chip sprite: mostly bone, some cream, now and then a pearl glint (pink, mint, lavender). */
    private static final int[] MOTE_TINTS = {0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xF2EBDD, 0xFFEEF3, 0xEDFCF4, 0xF1EDFF};

    private BanishParticles() {}

    /** From {@link FxParticles#register}. */
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BANISH_MOTE.get(),
                sprites -> (t, level, x, y, z, dx, dy, dz) -> new BanishMote(level, x, y, z, dx, dy, dz, sprites));
        event.registerSpriteSet(ModParticles.RIFT_SPARK.get(),
                sprites -> (t, level, x, y, z, dx, dy, dz) -> new FxParticles.FxParticle(level, x, y, z, dx, dy, dz, sprites, RIFT_SPARK));
    }

    /** See the class comment; full bright like the vanilla totem particle, drawn opaque so the umber rim reads. */
    static final class BanishMote extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float spin;
        private float swirl;

        BanishMote(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.friction = 0.62F;
            this.gravity = 0.5F;
            this.lifetime = 34 + this.random.nextInt(16);
            this.quadSize = 0.07F + this.random.nextFloat() * 0.06F;
            this.swirl = 0.2F + this.random.nextFloat() * 0.18F;
            this.spin = (0.08F + this.random.nextFloat() * 0.22F) * (this.random.nextBoolean() ? 1.0F : -1.0F);
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            int tint = MOTE_TINTS[this.random.nextInt(MOTE_TINTS.length)];
            float k = 0.94F + this.random.nextFloat() * 0.06F;
            this.setColor(((tint >> 16) & 0xFF) / 255.0F * k, ((tint >> 8) & 0xFF) / 255.0F * k, (tint & 0xFF) / 255.0F * k);
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) {
                return;
            }
            // the course turns a little every tick, the same way for every mote: the whole burst pinwheels
            double c = Math.cos(this.swirl);
            double s = Math.sin(this.swirl);
            double nx = this.xd * c - this.zd * s;
            this.zd = this.xd * s + this.zd * c;
            this.xd = nx;
            this.swirl *= 0.97F;
            this.oRoll = this.roll;
            this.roll += this.spin;
            this.setSpriteFromAge(this.sprites);
            float life = (float) this.age / this.lifetime;
            this.alpha = life < 0.5F ? 1.0F : Mth.clamp(1.0F - (life - 0.5F) / 0.5F, 0.0F, 1.0F);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
