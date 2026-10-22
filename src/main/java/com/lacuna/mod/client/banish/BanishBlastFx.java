package com.lacuna.mod.client.banish;

import com.lacuna.mod.banish.TotemBlast;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.GroundDecal;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Totem of Banishing's knockback blast as seen ({@link TotemBlast#FX}): a shockwave racing out over the ground to the
 * blast radius ({@link GroundDecal}, so it hugs steps and slopes), in the exilite palette sampled from the user's
 * textures: an umber shadow ring under a pearl-white front, a cream ring trailing behind it, a pale flare at the centre;
 * stone chips and rift sparks thrown off the front as it passes, and a short camera shake for whoever stands in it.
 * A player who has just landed sees their own arrival wave once the loading screen is gone.
 * The arrival blast ({@code p1} = 1) is a little slower and lingers longer.
 */
public final class BanishBlastFx {
    /** Exilite palette (debris, shard, ingot and totem textures): pearl front, bone, cream, umber outline. */
    static final int PEARL = 0xF6F3FB;
    static final int BONE = 0xE8E2D6;
    static final int CREAM = 0xD2C9B2;
    static final int UMBER = 0x7C6C58;

    /** Longest wait behind a loading screen (10 s). */
    private static final int MAX_WAIT = 200;

    private BanishBlastFx() {}

    /** From {@code BanishArrivalFx.register()}. */
    public static void register() {
        FxHandlers.register(TotemBlast.FX, p -> FxManager.add(new Wave(p)));
    }

    private static final class Wave implements ClientFx {
        private final Vec3 at;
        private final float radius;
        private final int life;
        private final GroundDecal decal = new GroundDecal();
        private int age;
        private int waited;
        /** The level it was sent in: the departure wave dies with the old world when its traveller changes dimension. */
        private final ClientLevel born = Minecraft.getInstance().level;

        Wave(FxPayload p) {
            this.at = p.a();
            this.radius = Math.max(1.0F, p.p0());
            this.life = p.p1() > 0.5F ? 22 : 16;
        }

        /** Front radius after {@code t} ticks: fast out, easing at the edge. */
        private float front(float t) {
            float x = Mth.clamp(t / (life * 0.7F), 0.0F, 1.0F);
            return radius * (1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x));
        }

        /** The arriving player's own copy waits behind the loading screen, so the wave is seen when the world is. */
        private boolean waiting() {
            return Minecraft.getInstance().screen instanceof ReceivingLevelScreen && waited < MAX_WAIT;
        }

        @Override
        public boolean tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != born) {
                return false;
            }
            if (waiting()) {
                waited++;
                return true;
            }
            ClientLevel level = mc.level;
            if (age == 0 && mc.player != null && mc.player.position().distanceToSqr(at) < radius * radius) {
                ScreenFx.shake(radius > 3.0F ? 2.2F : 1.2F, 10);
            }
            if (level != null && age < life * 0.7F) {
                RandomSource r = level.random;
                float f = front(age + 1);
                int n = 4 + (int) (f * 2.0F);
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double cos = Math.cos(a);
                    double sin = Math.sin(a);
                    double y = (decal.ground() == 0.0 ? at.y : decal.follow(at.x + cos * f, at.z + sin * f)) + 0.1;
                    boolean chip = r.nextInt(3) == 0;
                    double v = chip ? 0.28 : 0.12;
                    level.addParticle(chip ? ModParticles.BANISH_MOTE.get() : ModParticles.RIFT_SPARK.get(), at.x + cos * f, y,
                            at.z + sin * f, cos * v, 0.04 + r.nextDouble() * (chip ? 0.18 : 0.05), sin * v);
                }
            }
            return ++age < life;
        }

        @Override
        public void render(FxRenderContext ctx) {
            if (waiting() || ctx.level() != born) {
                return;
            }
            float t = ctx.time(age);
            float life01 = Mth.clamp(t / life, 0.0F, 1.0F);
            float fade = life01 < 0.1F ? life01 / 0.1F : 1.0F - Mth.clamp((life01 - 0.35F) / 0.65F, 0.0F, 1.0F);
            float r = Math.max(0.3F, front(t));
            float trail = Math.max(0.2F, front(t - 3.0F));
            GroundDecal g = decal.at(ctx.level(), at.x, at.y, at.z, radius + 1.0);
            VertexConsumer shade = ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.RING));
            g.square(ctx, shade, at.x, at.z, r * 1.04F, 0.0F, FxDraw.argb(0.55F * fade, UMBER));
            VertexConsumer light = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
            g.square(ctx, light, at.x, at.z, r, 0.005F, FxDraw.argb(0.95F * fade, PEARL));
            g.square(ctx, light, at.x, at.z, trail, 0.01F, FxDraw.argb(0.6F * fade, CREAM));
            float flare = Mth.clamp(1.0F - t / 6.0F, 0.0F, 1.0F);
            if (flare > 0.0F) {
                FxDraw.billboard(ctx.pose().last(), ctx.buffers().getBuffer(FxRenderTypes.glowShader()), ctx.camera(),
                        ctx.rel(at.add(0.0, 0.8, 0.0)), 2.4F * (1.2F - flare * 0.2F), 0.0F, FxDraw.argb(0.9F * flare, BONE));
            }
        }
    }
}
