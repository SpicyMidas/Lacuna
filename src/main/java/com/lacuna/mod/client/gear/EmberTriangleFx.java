package com.lacuna.mod.client.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.gear.EmberWave;
import com.lacuna.mod.gear.TideCurrents;
import com.lacuna.mod.network.FxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Ember Triangle visuals (client), one {@link FxPayload} each:
 * <ul>
 *   <li>{@code ember_windup}: the triangle glyph kindles on the ground at the caster's feet and flares, turning into
 *       place as it grows, fire licking up from its corners, flames streaming into the triangle held in the hand and a
 *       warm vignette for the caster.</li>
 *   <li>{@code ember_wave}: the thermal wave. A white-hot ring with an orange bloom runs out over the terrain (the same
 *       terrain path the server front follows, {@link TideCurrents#path}), dragging a red-hot glow and a darker scorched
 *       trail behind it, with a wavering veil of heat rising off it and sparks, flames, smoke and ash thrown up along
 *       it. A flash at the origin; the glyph flashes white-hot and burns out through orange to a charred mark shedding
 *       embers; camera shake for everyone near (stronger near the centre, a second jolt as the front rolls past) and a
 *       warm flash for the caster.</li>
 *   <li>{@code ember_dust}: the pulverized bodies ({@link EmberDust}).</li>
 * </ul>
 */
public final class EmberTriangleFx {
    static final ResourceLocation GLYPH = Lacuna.id("textures/effect/ember_glyph.png");
    private static final int WHITE_HOT = 0xFFF1CF;
    private static final int GOLD = 0xFFC155;
    private static final int ORANGE = 0xFF7B22;
    private static final int DEEP = 0xD9400E;
    private static final int EMBER_RED = 0x8E1C06;
    private static final int CHAR = 0x120A06;
    /** Half-size of the released glyph on the ground, in blocks. */
    private static final float GLYPH_HALF = 2.6F;
    /** Circumradius of the glyph's triangle as a fraction of its half-size (tools/art/m14_ember.py: 26 of 32 px). */
    private static final float GLYPH_TRIANGLE = 26.0F / 32.0F;

    private EmberTriangleFx() {}

    public static void register() {
        FxHandlers.register(EmberWave.WINDUP_FX, p -> FxManager.add(new Windup(p)));
        FxHandlers.register(EmberWave.WAVE_FX, p -> FxManager.add(new Wave(p)));
        FxHandlers.register(EmberWave.DUST_FX, EmberDust::spawn);
        NeoForge.EVENT_BUS.addListener(EmberDust::onRenderLiving);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isSelf(int entityId) {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getId() == entityId;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static int mixRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    private static void vtx(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int argb) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(argb);
    }

    /** The glyph lying on the ground centred at {@code c} (camera-relative), apex towards {@code yawDeg}. */
    private static void glyph(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, float half, float yawDeg, int argb) {
        double a = Math.toRadians(yawDeg);
        Vec3 forward = new Vec3(-Math.sin(a) * half, 0.0, Math.cos(a) * half);
        Vec3 side = new Vec3(Math.cos(a) * half, 0.0, Math.sin(a) * half);
        // the texture's top row (v = 0) holds the apex: it must lie forward
        FxDraw.quad(pose, vc, c, side, forward.scale(-1.0), argb);
    }

    /** Corner {@code k} (0 = apex) of the glyph's triangle around {@code c}. */
    private static Vec3 corner(Vec3 c, float half, float yawDeg, int k) {
        double a = Math.toRadians(yawDeg);
        double t = k * Mth.TWO_PI / 3.0;
        double rr = half * GLYPH_TRIANGLE;
        double dx = -Math.sin(a) * Math.cos(t) + Math.cos(a) * Math.sin(t);
        double dz = Math.cos(a) * Math.cos(t) + Math.sin(a) * Math.sin(t);
        return new Vec3(c.x + dx * rr, c.y, c.z + dz * rr);
    }

    // ------------------------------------------------------------------ wind-up

    /** The glyph kindles under the caster and flares while the triangle in their hand gathers fire. */
    private static final class Windup implements ClientFx {
        private final int casterId;
        private final int ticks;
        private final float yaw;
        private final Vec3 fallback;
        private int age;

        Windup(FxPayload p) {
            this.casterId = p.entityId();
            this.ticks = Math.max(1, (int) p.p0());
            this.yaw = p.p1();
            this.fallback = p.a();
        }

        private Vec3 feet(float partial) {
            ClientLevel level = Minecraft.getInstance().level;
            Entity e = level != null ? level.getEntity(casterId) : null;
            return e != null ? e.getPosition(partial) : fallback;
        }

        private static float half(float f) {
            return 1.6F + (GLYPH_HALF - 1.6F) * smooth(f);
        }

        /** The glyph turns into place as it flares (degrees off its final heading). */
        private float heading(float f) {
            return yaw + 55.0F * (1.0F - smooth(f));
        }

        @Override
        public boolean tick() {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) {
                return false;
            }
            age++;
            float f = Mth.clamp(age / (float) ticks, 0.0F, 1.0F);
            RandomSource r = level.random;
            Vec3 c = feet(1.0F);
            float half = half(f);
            float heading = heading(f);
            // fire licks up from the corners
            for (int k = 0; k < 3; k++) {
                Vec3 v = corner(c, half, heading, k);
                level.addParticle(ParticleTypes.FLAME, v.x, c.y + 0.1, v.z, 0.0, 0.02 + 0.05 * f, 0.0);
                if (r.nextFloat() < 0.5F + 0.4F * f) {
                    EmberDust.spark(level, v.x, c.y + 0.15, v.z, (r.nextDouble() - 0.5) * 0.08, 0.1 + 0.18 * f,
                            (r.nextDouble() - 0.5) * 0.08);
                }
            }
            // flames stream into the triangle held up in the hand
            Entity e = level.getEntity(casterId);
            if (e != null) {
                Vec3 hand = e.getEyePosition().add(e.getLookAngle().scale(0.6)).add(0.0, -0.45, 0.0);
                for (int i = 0; i < 3; i++) {
                    Vec3 from = hand.add(r.nextGaussian() * 0.9, r.nextGaussian() * 0.6, r.nextGaussian() * 0.9);
                    Vec3 v = hand.subtract(from).scale(0.16);
                    level.addParticle(ParticleTypes.SMALL_FLAME, from.x, from.y, from.z, v.x, v.y, v.z);
                }
            }
            if (isSelf(casterId)) {
                ScreenFx.hold("ember_windup", ORANGE, 0.3F * f, true);
            }
            return age < ticks + 1;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float f = Mth.clamp(time / ticks, 0.0F, 1.0F);
            float half = half(f);
            float heading = heading(f);
            float flicker = 0.85F + 0.15F * Mth.sin(time * 2.3F) * Mth.sin(time * 1.1F + 0.7F);
            Vec3 c = ctx.rel(feet(ctx.partialTick())).add(0.0, 0.05, 0.0);
            PoseStack.Pose pose = ctx.pose().last();
            VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            FxDraw.ground(pose, glow, c.add(0.0, -0.01, 0.0), half * 1.3F, FxDraw.argb(0.3F * f * flicker, ORANGE));
            VertexConsumer vc = ctx.buffers().getBuffer(FxRenderTypes.additive(GLYPH));
            glyph(pose, vc, c, half * 1.16F, heading, FxDraw.argb(0.35F * (0.3F + 0.7F * f) * flicker, DEEP));
            glyph(pose, vc, c.add(0.0, 0.004, 0.0), half, heading, FxDraw.argb((0.35F + 0.65F * f) * flicker, GOLD));
            glyph(pose, vc, c.add(0.0, 0.008, 0.0), half * 0.99F, heading, FxDraw.argb(0.6F * f * f * flicker, WHITE_HOT));
        }
    }

    // ------------------------------------------------------------------ the thermal wave

    /** A circular heat front running out over the ground, and the glyph burning out where it started. */
    private static final class Wave implements ClientFx {
        private static final int RAYS = 72;
        /** Ticks the scorched trail lingers after the front has stopped. */
        private static final int TRAIL = 30;
        /** Ticks the glyph takes to burn out. */
        private static final int BURN = 46;
        private final Vec3 origin;
        private final double radius;
        private final int expand;
        private final float yaw;
        private final int casterId;
        private final int life;
        private final double[] cos = new double[RAYS];
        private final double[] sin = new double[RAYS];
        /** Ground height along each ray (every {@link TideCurrents#PATH_STEP} blocks): the front hugs the terrain. */
        private final double[][] heights = new double[RAYS][];
        private boolean passedSelf;
        private int age;

        Wave(FxPayload p) {
            this.origin = p.a();
            this.radius = p.p0() > 0.0F ? p.p0() : Balance.EMBER_TRIANGLE_RADIUS;
            this.expand = p.p1() > 0.0F ? (int) p.p1() : Balance.EMBER_TRIANGLE_WAVE_TICKS;
            this.yaw = p.p2();
            this.casterId = p.entityId();
            this.life = Math.max(expand + TRAIL, BURN) + 2;
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            for (int i = 0; i < RAYS; i++) {
                double a = Mth.TWO_PI * i / RAYS;
                cos[i] = Math.cos(a);
                sin[i] = Math.sin(a);
                heights[i] = level == null ? new double[] {origin.y}
                        : TideCurrents.path(level, origin, new Vec3(cos[i], 0.0, sin[i]), radius + 3.0, false);
            }
            Player me = mc.player;
            if (me != null) {
                double d = flat(me.position());
                double reach = radius + 14.0;
                if (d < reach) {
                    ScreenFx.shake((float) (5.5 * Math.pow(1.0 - d / reach, 1.6)), 16);
                }
                if (me.getId() == casterId) {
                    ScreenFx.flash(GOLD, 0.3F, 9);
                }
            }
            if (level != null) {
                burst(level, EmberDust.particleScale(mc));
            }
        }

        /** The release: fire and sparks bursting out from the caster's feet, and a puff of smoke. */
        private void burst(ClientLevel level, float scale) {
            RandomSource r = level.random;
            int n = Math.max(6, (int) (28 * scale));
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double sp = 0.15 + 0.2 * r.nextDouble();
                double x = origin.x + Math.cos(a) * 0.4;
                double z = origin.z + Math.sin(a) * 0.4;
                level.addParticle(ParticleTypes.FLAME, x, origin.y + 0.15, z, Math.cos(a) * sp, 0.02 + 0.05 * r.nextDouble(),
                        Math.sin(a) * sp);
                EmberDust.spark(level, x, origin.y + 0.2, z, Math.cos(a) * sp * 0.8, 0.2 + 0.25 * r.nextDouble(), Math.sin(a) * sp * 0.8);
            }
            for (int i = 0; i < 5; i++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, origin.x + (r.nextDouble() - 0.5), origin.y + 0.3,
                        origin.z + (r.nextDouble() - 0.5), 0.0, 0.06, 0.0);
            }
        }

        private double flat(Vec3 p) {
            double dx = p.x - origin.x;
            double dz = p.z - origin.z;
            return Math.sqrt(dx * dx + dz * dz);
        }

        private double front(float time) {
            return EmberWave.radius(time, radius, expand);
        }

        /** Point on ray {@code i} at distance {@code r}, {@code up} blocks above the ground there (world space). */
        private Vec3 at(int i, double r, double up) {
            int j = Math.floorMod(i, RAYS);
            return new Vec3(origin.x + cos[j] * r, TideCurrents.heightAt(heights[j], r) + up, origin.z + sin[j] * r);
        }

        @Override
        public boolean tick() {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) {
                return false;
            }
            RandomSource r = level.random;
            if (age <= expand) {
                // the front throws up fire, sparks, smoke and ash all along its length
                double rr = front(age);
                int n = Math.max(4, (int) (34 * EmberDust.particleScale(mc) * (0.5 + 0.5 * rr / radius)));
                for (int k = 0; k < n; k++) {
                    int i = r.nextInt(RAYS);
                    Vec3 p = at(i, Math.max(0.2, rr + (r.nextDouble() - 0.6) * 0.8), 0.08);
                    double ox = cos[i];
                    double oz = sin[i];
                    float pick = r.nextFloat();
                    if (pick < 0.36F) {
                        level.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, ox * 0.03, 0.03 + r.nextDouble() * 0.07, oz * 0.03);
                    } else if (pick < 0.66F) {
                        double out = 0.06 + r.nextDouble() * 0.12;
                        EmberDust.spark(level, p.x, p.y + 0.05, p.z, ox * out, 0.16 + r.nextDouble() * 0.24, oz * out);
                    } else if (pick < 0.78F) {
                        level.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.2, p.z, ox * 0.02, 0.04 + r.nextDouble() * 0.03, oz * 0.02);
                    } else if (pick < 0.82F) {
                        level.addParticle(ParticleTypes.LAVA, p.x, p.y, p.z, 0.0, 0.0, 0.0);
                    } else {
                        double out = 0.08 + r.nextDouble() * 0.08;
                        EmberDust.ash(level, p.x, p.y + 0.1, p.z, ox * out, 0.03 + r.nextDouble() * 0.05, oz * out);
                    }
                }
            }
            // the glyph burns out: embers flake off its lines, then thin smoke
            if (age < BURN && r.nextFloat() < 0.95F - age / (float) BURN) {
                int k = r.nextInt(3);
                Vec3 p = corner(origin, GLYPH_HALF, yaw, k).lerp(corner(origin, GLYPH_HALF, yaw, (k + 1) % 3), r.nextDouble());
                if (r.nextFloat() < 0.65F) {
                    EmberDust.spark(level, p.x, origin.y + 0.06, p.z, (r.nextDouble() - 0.5) * 0.03, 0.05 + r.nextDouble() * 0.08,
                            (r.nextDouble() - 0.5) * 0.03);
                } else {
                    level.addParticle(ParticleTypes.SMOKE, p.x, origin.y + 0.1, p.z, 0.0, 0.02, 0.0);
                }
            }
            // a second jolt when the front rolls past the local player
            Player me = mc.player;
            if (me != null && !passedSelf && me.getId() != casterId && age <= expand) {
                double d = flat(me.position());
                if (d <= radius + 0.5 && front(age) >= d) {
                    passedSelf = true;
                    ScreenFx.shake((float) (3.5 * (1.0 - d / (radius + 4.0))), 10);
                }
            }
            return ++age < life;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            double rr = front(Math.min(time, expand));
            float spent = (float) (rr / radius);
            float frontFade = time <= expand ? 1.0F : Mth.clamp(1.0F - (time - expand) / 5.0F, 0.0F, 1.0F);
            float trailFade = time <= expand ? 1.0F : Mth.clamp(1.0F - (time - expand) / TRAIL, 0.0F, 1.0F);
            PoseStack.Pose pose = ctx.pose().last();
            Vec3 cam = ctx.cam();
            Vec3 c = ctx.rel(origin);

            // darkness first (alpha blended): the scorched trail behind the front and the charred glyph
            if (trailFade > 0.0F && rr > 0.4) {
                VertexConsumer shade = ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.BEAM));
                band(pose, shade, cam, rr - 4.2, rr - 0.35, 0.03, FxDraw.argb(0.0F, CHAR), FxDraw.argb(0.55F * trailFade, CHAR), false);
            }
            float charA = 0.6F * Mth.clamp(time / 4.0F, 0.0F, 1.0F) * (1.0F - smooth((time - 18.0F) / (BURN - 16.0F)));
            if (charA > 0.0F) {
                VertexConsumer shade = ctx.buffers().getBuffer(FxRenderTypes.shade(GLYPH));
                glyph(pose, shade, c.add(0.0, 0.035, 0.0), GLYPH_HALF * 1.04F, yaw, FxDraw.argb(charA, CHAR));
            }

            // then light (additive): the red-hot ground behind the front, the front itself and its rising heat
            VertexConsumer beam = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            if (trailFade > 0.0F && rr > 0.4) {
                band(pose, beam, cam, rr - 2.6, rr - 0.2, 0.05, FxDraw.argb(0.0F, EMBER_RED),
                        FxDraw.argb(0.45F * trailFade * (1.0F - 0.3F * spent), DEEP), false);
            }
            if (frontFade > 0.0F && rr > 0.05) {
                float e = frontFade * (1.0F - 0.4F * spent);
                int bloom = FxDraw.argb(0.5F * e, DEEP);
                int body = FxDraw.argb(0.9F * e, ORANGE);
                int core = FxDraw.argb(0.95F * e, WHITE_HOT);
                band(pose, beam, cam, rr - 1.5, rr + 1.5, 0.07, bloom, bloom, true);
                band(pose, beam, cam, rr - 0.65, rr + 0.65, 0.08, body, body, true);
                band(pose, beam, cam, rr - 0.25, rr + 0.25, 0.09, core, core, true);
                double tall = 1.6 * (1.0 - 0.35 * spent);
                veil(pose, beam, cam, rr - 0.12, time, tall, FxDraw.argb(0.3F * e, ORANGE), 0.0F);
                veil(pose, beam, cam, rr - 0.5, time + 7.0F, tall * 0.7, FxDraw.argb(0.2F * e, GOLD), 1.7F);
            }

            // the flash at the origin
            if (time < 7.0F) {
                float f = 1.0F - time / 7.0F;
                f *= f;
                VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
                FxDraw.billboard(pose, glow, ctx.camera(), c.add(0.0, 1.0, 0.0), 2.2F + time * 0.7F, 0.0F, FxDraw.argb(0.95F * f, WHITE_HOT));
                FxDraw.billboard(pose, glow, ctx.camera(), c.add(0.0, 0.6, 0.0), 3.5F + time * 1.1F, 0.0F, FxDraw.argb(0.55F * f, ORANGE));
                FxDraw.ground(pose, glow, c.add(0.0, 0.05, 0.0), 3.0F + time * 0.8F, FxDraw.argb(0.7F * f, GOLD));
            }

            // the glyph flashes white-hot at release, then burns out through orange to a dull red
            float glyphA = time < 2.0F ? 1.0F : 1.0F - smooth((time - 2.0F) / (BURN - 2.0F));
            if (glyphA > 0.0F) {
                float burn = Mth.clamp(time / BURN, 0.0F, 1.0F);
                float flick = 0.85F + 0.15F * Mth.sin(time * 2.9F) * Mth.sin(time * 1.3F + 1.0F);
                int hot = burn < 0.15F ? mixRgb(WHITE_HOT, GOLD, burn / 0.15F)
                        : burn < 0.5F ? mixRgb(GOLD, DEEP, (burn - 0.15F) / 0.35F) : mixRgb(DEEP, EMBER_RED, (burn - 0.5F) / 0.5F);
                VertexConsumer vc = ctx.buffers().getBuffer(FxRenderTypes.additive(GLYPH));
                Vec3 g = c.add(0.0, 0.05, 0.0);
                glyph(pose, vc, g, GLYPH_HALF * 1.18F, yaw, FxDraw.argb(0.35F * glyphA * flick, DEEP));
                glyph(pose, vc, g.add(0.0, 0.005, 0.0), GLYPH_HALF, yaw, FxDraw.argb(glyphA * flick, hot));
            }
        }

        /** A ring band hugging the ground from radius r0 to r1; u runs across it (soft: the beam mask, else a flat fill). */
        private void band(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, double r0, double r1, double lift, int c0, int c1,
                          boolean soft) {
            r0 = Math.max(0.0, r0);
            if (r1 <= r0) {
                return;
            }
            float u0 = soft ? 0.0F : 0.5F;
            float u1 = soft ? 1.0F : 0.5F;
            for (int i = 0; i < RAYS; i++) {
                float v0 = (float) i / RAYS;
                float v1 = (float) (i + 1) / RAYS;
                vtx(vc, pose, at(i, r0, lift).subtract(cam), u0, v0, c0);
                vtx(vc, pose, at(i, r1, lift).subtract(cam), u1, v0, c1);
                vtx(vc, pose, at(i + 1, r1, lift).subtract(cam), u1, v1, c1);
                vtx(vc, pose, at(i + 1, r0, lift).subtract(cam), u0, v1, c0);
            }
        }

        /** A translucent sheet of heat standing on the front, fading upwards, its top edge wavering. */
        private void veil(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, double rr, float time, double height, int argb, float phase) {
            if (rr <= 0.1) {
                return;
            }
            int top = argb & 0xFFFFFF;
            for (int i = 0; i < RAYS; i++) {
                Vec3 a = at(i, rr, 0.05).subtract(cam);
                Vec3 b = at(i + 1, rr, 0.05).subtract(cam);
                double ha = height * waver(i, time, phase);
                double hb = height * waver(i + 1, time, phase);
                vtx(vc, pose, a, 0.5F, 0.0F, argb);
                vtx(vc, pose, a.add(0.0, ha, 0.0), 0.5F, 1.0F, top);
                vtx(vc, pose, b.add(0.0, hb, 0.0), 0.5F, 1.0F, top);
                vtx(vc, pose, b, 0.5F, 0.0F, argb);
            }
        }

        private static double waver(int i, float time, float phase) {
            double a = Mth.TWO_PI * i / RAYS;
            return 0.7 + 0.3 * Math.sin(a * 9.0 + time * 1.1 + phase) * Math.sin(a * 4.0 - time * 0.7 + phase * 2.0);
        }
    }
}
