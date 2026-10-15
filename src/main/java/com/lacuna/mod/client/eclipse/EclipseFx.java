package com.lacuna.mod.client.eclipse;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client effects of the Eclipser ("black sword dash"), triggered by server {@link FxPayload}s and seen by every
 * nearby player:
 * <ul>
 *   <li>{@code eclipse_dash}: black ribbon trail with a crimson rim, large black crescent slashes with a crimson
 *       cutting edge along the path, speed streaks, dark afterimages of the dasher and shadow/spark bursts. For the
 *       dashing player it also starts the {@link DashDriver} and a dark screen flash.</li>
 *   <li>{@code eclipse_hit}: crossing X-slashes on a cut entity, a crimson flare and sparks (camera shake for the
 *       dasher).</li>
 *   <li>{@code eclipse_end} / {@code shadow_walk_end}: a shockwave of shadows where the dash ends / Shadow Walk ends.</li>
 * </ul>
 */
public final class EclipseFx {
    static final ResourceLocation SLASH_MASK = Lacuna.id("textures/effect/eclipse_slash.png");
    static final ResourceLocation STREAK = Lacuna.id("textures/effect/eclipse_streak.png");
    static final int BLACK = 0x060309;
    static final int CRIMSON = 0xB3122E;
    static final int CRIMSON_HOT = 0xE03A52;
    static final int HOT_CORE = 0xFFD2D8;
    static final int GHOST = 0x1C0A14;

    private EclipseFx() {}

    public static void register() {
        FxHandlers.register("eclipse_dash", EclipseFx::dash);
        FxHandlers.register("eclipse_hit", EclipseFx::hit);
        FxHandlers.register("eclipser_slash", p -> FxManager.add(new MeleeFx(p.a(), p.b(), (int) p.p0(), p.p1(), p.entityId())));
        FxHandlers.register("eclipse_end", p -> end(p, false));
        FxHandlers.register("shadow_walk_end", p -> end(p, true));
    }

    private static boolean isSelf(FxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && p.entityId() == mc.player.getId();
    }

    private static void dash(FxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        boolean self = isSelf(p);
        if (self) {
            DashDriver.start(p.a(), p.b(), (int) p.p0());
            ScreenFx.flash(0x000000, 0.4F, 7);
            ScreenFx.shake(0.9F, 6);
        }
        ResourceLocation skin = DefaultPlayerSkin.getDefaultTexture();
        boolean slim = false;
        Entity e = mc.level == null ? null : mc.level.getEntity(p.entityId());
        if (e instanceof AbstractClientPlayer player) {
            PlayerSkin s = player.getSkin();
            skin = s.texture();
            slim = s.model() == PlayerSkin.Model.SLIM;
        }
        FxManager.add(new DashFx(p.a(), p.b(), Math.max(1, (int) p.p0()), Mth.clamp(p.p1() / 100.0F, 0.0F, 1.0F), p.p2(), skin, slim));
    }

    private static void hit(FxPayload p) {
        FxManager.add(new HitFx(p.a(), p.b(), p.p0(), p.p1(), (int) p.p2()));
        if (DashDriver.active() || DashDriver.punch(0.0F) > 0.05F) {
            ScreenFx.shake(1.8F, 8);
        }
    }

    private static void end(FxPayload p, boolean shadowWalk) {
        FxManager.add(new BurstFx(p.a(), shadowWalk));
        if (isSelf(p)) {
            if (shadowWalk) {
                ScreenFx.flash(0x000000, 0.3F, 10);
            } else {
                ScreenFx.shake(1.2F, 8);
                ScreenFx.flash(0x2A0008, 0.22F, 8);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Fades geometry that would sit right on the camera (the dasher's own first-person view). */
    private static float nearFade(FxRenderContext ctx, Vec3 world, double from, double span) {
        return Mth.clamp((float) ((ctx.cam().distanceTo(world) - from) / span), 0.0F, 1.0F);
    }

    private static Vec3 perp(Vec3 dir) {
        Vec3 ref = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return dir.cross(ref).normalize();
    }

    /**
     * A crescent band around {@code c} in the plane (e1, e2), from angle {@code a0} to {@code a1}. Its local half-width
     * tapers to points at both ends (thicker towards the leading end, like a sword trail); the band covers the
     * fraction {@code lo..hi} of that half-width around radius {@code r} (-1..1 = the whole body, 0.5..1 = the outer
     * cutting edge). u runs inner to outer, v along the stroke.
     */
    static void band(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, Vec3 e1, Vec3 e2, float r, float a0, float a1,
                     float width, float lo, float hi, int segments, int argb) {
        if (Math.abs(a1 - a0) < 1.0E-3 || (argb >>> 24) == 0) {
            return;
        }
        Vec3 pin = null;
        Vec3 pout = null;
        float pv = 0.0F;
        for (int i = 0; i <= segments; i++) {
            float s = (float) i / segments;
            float taper = (float) Math.pow(Mth.sin(s * Mth.PI), 0.8) * (0.55F + 0.45F * s);
            float hw = width * 0.5F * taper;
            float t = Mth.lerp(s, a0, a1);
            Vec3 d = e1.scale(Mth.cos(t)).add(e2.scale(Mth.sin(t)));
            Vec3 in = c.add(d.scale(r + hw * lo));
            Vec3 out = c.add(d.scale(r + hw * hi));
            if (pin != null) {
                vertex(vc, pose, pin, 0.0F, pv, argb);
                vertex(vc, pose, pout, 1.0F, pv, argb);
                vertex(vc, pose, out, 1.0F, s, argb);
                vertex(vc, pose, in, 0.0F, s, argb);
            }
            pin = in;
            pout = out;
            pv = s;
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int argb) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(argb);
    }

    private static void particles(ClientLevel level, ParticleOptions type, Vec3 at, int count, double spread, Vec3 drift,
                                  double speed, RandomSource rnd) {
        for (int i = 0; i < count; i++) {
            double ox = (rnd.nextDouble() - 0.5) * 2.0 * spread;
            double oy = (rnd.nextDouble() - 0.5) * 2.0 * spread;
            double oz = (rnd.nextDouble() - 0.5) * 2.0 * spread;
            level.addParticle(type, at.x + ox, at.y + oy, at.z + oz,
                    drift.x + (rnd.nextDouble() - 0.5) * speed, drift.y + (rnd.nextDouble() - 0.5) * speed,
                    drift.z + (rnd.nextDouble() - 0.5) * speed);
        }
    }

    private static void burst(ClientLevel level, Vec3 at, int count, double speed, ParticleOptions type, RandomSource rnd) {
        for (int i = 0; i < count; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double up = (rnd.nextDouble() - 0.3) * 0.5;
            double sp = speed * (0.5 + rnd.nextDouble() * 0.7);
            level.addParticle(type, at.x, at.y + (rnd.nextDouble() - 0.5) * 0.8, at.z,
                    Math.cos(ang) * sp, up * sp, Math.sin(ang) * sp);
        }
    }

    // ------------------------------------------------------------------------------------------ dash

    /** The whole dash: trail, slashes, streaks, afterimages and particles along the path. */
    static final class DashFx implements ClientFx {
        private static final int TRAIL_FADE = 15;
        private static final int SLASH_LIFE = 13;
        private static final int IMAGE_LIFE = 14;
        private static final int STREAK_LIFE = 7;
        private static final double CHEST = 1.0;

        private final Vec3 start;
        private final Vec3 end;
        private final Vec3 dir;
        private final Vec3 side;
        private final Vec3 up;
        private final double length;
        private final int ticks;
        private final float power;
        private final float yaw;
        private final float lean;
        private final ResourceLocation skin;
        private final boolean slim;
        private final RandomSource rnd;
        private final float[][] slashes; // frac, roll, tilt, sweep sign
        private final float[][] streaks; // frac, span, angle, radius, height
        private final int images;
        private int age;

        DashFx(Vec3 start, Vec3 end, int ticks, float power, float fallbackYaw, ResourceLocation skin, boolean slim) {
            this.start = start;
            this.end = end;
            this.length = start.distanceTo(end);
            Vec3 d = end.subtract(start);
            if (length > 1.0E-3) {
                this.dir = d.normalize();
            } else {
                float r = fallbackYaw * Mth.DEG_TO_RAD;
                this.dir = new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
            }
            this.side = perp(dir);
            this.up = side.cross(dir).normalize();
            this.ticks = ticks;
            this.power = power;
            this.yaw = dir.x * dir.x + dir.z * dir.z > 0.01 ? (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) : fallbackYaw;
            this.lean = Mth.clamp(25.0F - (float) Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0, 1.0))) * 0.6F, -30.0F, 80.0F);
            this.skin = skin;
            this.slim = slim;
            this.rnd = RandomSource.create(Double.doubleToLongBits(start.x * 31.0 + start.z * 17.0 + start.y));
            int count = Math.max(2, (int) Math.round(length / 2.3));
            this.slashes = new float[count][];
            for (int j = 0; j < count; j++) {
                float sign = j % 2 == 0 ? 1.0F : -1.0F;
                float roll = (j % 2 == 0 ? 0.0F : Mth.PI) + (rnd.nextFloat() - 0.5F) * 1.4F;
                float tilt = 0.45F + rnd.nextFloat() * 0.45F;
                slashes[j] = new float[] {(j + 0.5F) / count, roll, tilt * sign, sign};
            }
            this.streaks = new float[10][];
            for (int k = 0; k < streaks.length; k++) {
                streaks[k] = new float[] {rnd.nextFloat() * 0.8F, 1.5F + rnd.nextFloat() * 2.0F, rnd.nextFloat() * Mth.TWO_PI,
                        0.9F + rnd.nextFloat() * 1.1F, (rnd.nextFloat() - 0.5F) * 0.8F};
            }
            this.images = Mth.clamp((int) Math.round(length / 1.7), 2, 8);
        }

        private Vec3 at(double frac) {
            return start.add(end.subtract(start).scale(frac));
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            age++;
            if (level != null) {
                if (age == 1) {
                    Vec3 c = start.add(0, CHEST, 0);
                    burst(level, c, 18, 0.35, ModParticles.SHADOW_MOTE.get(), rnd);
                    burst(level, c, 12, 0.5, ModParticles.CRIMSON_SPARK.get(), rnd);
                }
                if (age <= ticks) {
                    // shed shadows just behind the head of the dash
                    double f0 = (age - 1) / (double) ticks;
                    double f1 = age / (double) ticks;
                    for (int i = 0; i < 4; i++) {
                        Vec3 p = at(Mth.lerp(rnd.nextDouble(), f0, f1)).add(0, CHEST, 0);
                        particles(level, ModParticles.SHADOW_MOTE.get(), p, 1, 0.35, dir.scale(-0.04), 0.06, rnd);
                    }
                    particles(level, ModParticles.CRIMSON_SPARK.get(), at(f0).add(0, CHEST, 0), 3, 0.4, dir.scale(0.08), 0.25, rnd);
                    if (rnd.nextInt(2) == 0) {
                        particles(level, ModParticles.CRIMSON_SPARK.get(), at(f0).add(0, CHEST, 0), 1, 0.3, Vec3.ZERO, 0.2, rnd);
                    }
                }
            }
            return age < ticks + 24;
        }

        @Override
        public void render(FxRenderContext ctx) {
            // the dasher starts moving on the tick after the payload: lag the timeline by one tick to stay on it
            float t = Math.max(0.0F, ctx.time(age) - 1.0F);
            float head = Mth.clamp(t / ticks, 0.0F, 1.0F);
            PoseStack.Pose pose = ctx.pose().last();

            // ground shockwave where the dash starts
            if (t < 12.0F) {
                float k = t / 12.0F;
                float radius = 0.6F + 2.8F * (1.0F - (1.0F - k) * (1.0F - k));
                Vec3 g = ctx.rel(start.add(0, 0.06, 0));
                FxDraw.ground(pose, ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.RING)), g, radius, FxDraw.argb(0.85F * (1.0F - k), BLACK));
                FxDraw.ground(pose, ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING)), g, radius * 1.06F,
                        FxDraw.argb(0.9F * (1.0F - k), CRIMSON));
            }

            renderTrail(ctx, pose, t, head, true);
            renderSlashes(ctx, pose, t, 0);
            renderStreaks(ctx, pose, t, true);
            renderTrail(ctx, pose, t, head, false);
            renderSlashes(ctx, pose, t, 1);
            renderStreaks(ctx, pose, t, false);
            renderSlashes(ctx, pose, t, 2);
            if (LacunaClientConfig.get(LacunaClientConfig.ECLIPSE_AFTERIMAGES)) {
                renderImages(ctx, t);
            }
        }

        /** Camera-facing ribbon from the start to the head of the dash; the tail fades and thins over 15 ticks. */
        private void renderTrail(FxRenderContext ctx, PoseStack.Pose pose, float t, float head, boolean glow) {
            int m = Math.max(4, (int) Math.ceil(length * 1.5));
            VertexConsumer vc = ctx.buffers().getBuffer(glow ? FxRenderTypes.additive(FxRenderTypes.BEAM) : FxRenderTypes.shade(STREAK));
            Vec3 prev = null;
            float prevW = 0.0F;
            int prevC = 0;
            double prevV = 0.0;
            for (int i = 0; i <= m; i++) {
                float f = Math.min(head, (float) i / m);
                boolean last = (float) i / m >= head;
                float born = f * ticks;
                float life = Mth.clamp((t - born) / TRAIL_FADE, 0.0F, 1.0F);
                float a = (float) Math.pow(1.0F - life, 1.5);
                Vec3 world = at(f).add(0, CHEST, 0);
                a *= nearFade(ctx, world, 0.7, 2.0);
                float w = (glow ? 1.75F : 0.95F) * (0.3F + 0.7F * (1.0F - life)) * (i == 0 ? 0.35F : 1.0F);
                int color = glow ? FxDraw.argb(0.9F * a, CRIMSON) : FxDraw.argb(a, BLACK);
                Vec3 p = ctx.rel(world);
                double v = f * length;
                if (prev != null && (((prevC >>> 24) | (color >>> 24)) != 0)) {
                    FxDraw.ribbon(pose, vc, prev, p, prevW, w, prevC, color, (float) prevV, (float) v);
                }
                prev = p;
                prevW = w;
                prevC = color;
                prevV = v;
                if (last) {
                    break;
                }
            }
        }

        /** pass 0: crimson glow, 1: black blade body, 2: crimson cutting edge and hot core. */
        private void renderSlashes(FxRenderContext ctx, PoseStack.Pose pose, float t, int pass) {
            VertexConsumer vc = ctx.buffers().getBuffer(pass == 1 ? FxRenderTypes.shade(SLASH_MASK) : FxRenderTypes.additive(FxRenderTypes.BEAM));
            for (float[] s : slashes) {
                float born = s[0] * ticks + 0.3F;
                float life = (t - born) / SLASH_LIFE;
                if (life < 0.0F || life >= 1.0F) {
                    continue;
                }
                Vec3 c0 = at(s[0]).add(0, CHEST + 0.15, 0);
                float alpha = (1.0F - smooth(0.35F, 1.0F, life)) * nearFade(ctx, c0, 1.4, 2.2);
                if (alpha <= 0.004F) {
                    continue;
                }
                Vec3 e1 = side.scale(Mth.cos(s[1])).add(up.scale(Mth.sin(s[1])));
                Vec3 n = dir.cross(e1).normalize();
                Vec3 e2 = dir.scale(Mth.cos(s[2])).add(n.scale(Mth.sin(s[2]))).normalize().scale(s[3]);
                float r = (1.5F + 0.8F * power) * (1.0F + 0.14F * life);
                float w = 0.75F + 0.5F * power;
                Vec3 c = ctx.rel(c0.subtract(e1.scale(r * 0.62F)));
                float a0 = -1.4F;
                float a1 = 1.4F;
                float drawn = Mth.lerp(Mth.clamp((t - born) / 2.2F, 0.0F, 1.0F), a0, a1);
                switch (pass) {
                    case 0 -> band(pose, vc, c, e1, e2, r, a0, drawn, w, 0.3F, 2.0F, 20, FxDraw.argb(0.55F * alpha, CRIMSON));
                    case 1 -> band(pose, vc, c, e1, e2, r, a0, drawn, w, -1.0F, 1.0F, 24, FxDraw.argb(alpha, BLACK));
                    default -> {
                        band(pose, vc, c, e1, e2, r, a0, drawn, w, 0.55F, 1.1F, 24, FxDraw.argb(alpha, CRIMSON_HOT));
                        band(pose, vc, c, e1, e2, r, a0, drawn, w, 0.86F, 1.0F, 24, FxDraw.argb(0.8F * alpha, HOT_CORE));
                    }
                }
            }
        }

        /** Thin speed streaks parallel to the dash, snapping in as the head passes them. */
        private void renderStreaks(FxRenderContext ctx, PoseStack.Pose pose, float t, boolean glow) {
            if (length < 1.0) {
                return;
            }
            VertexConsumer vc = ctx.buffers().getBuffer(glow ? FxRenderTypes.additive(FxRenderTypes.BEAM) : FxRenderTypes.shade(STREAK));
            for (float[] s : streaks) {
                float born = s[0] * ticks;
                float life = (t - born) / STREAK_LIFE;
                if (life < 0.0F || life >= 1.0F) {
                    continue;
                }
                Vec3 off = side.scale(Mth.cos(s[2]) * s[3]).add(up.scale(Mth.sin(s[2]) * s[3])).add(0, CHEST + s[4], 0);
                double f1 = Math.min(1.0, s[0] + s[1] / length);
                Vec3 a = at(s[0]).add(off);
                Vec3 b = at(Mth.lerp(Math.min(1.0F, life * 3.0F), s[0], (float) f1)).add(off);
                float alpha = (1.0F - life) * nearFade(ctx, a.add(b).scale(0.5), 0.8, 1.5);
                FxDraw.ribbon(pose, vc, ctx.rel(a), ctx.rel(b), glow ? 0.16F : 0.07F, glow ? 0.16F : 0.07F,
                        FxDraw.argb(0.0F, glow ? CRIMSON : BLACK), FxDraw.argb((glow ? 0.9F : 0.95F) * alpha, glow ? CRIMSON : BLACK), 0.0F, 1.0F);
            }
        }

        /** Dark translucent copies of the dasher left along the path. */
        private void renderImages(FxRenderContext ctx, float t) {
            for (int k = 0; k < images; k++) {
                float frac = (float) k / images;
                float born = frac * ticks;
                float life = (t - born) / IMAGE_LIFE;
                if (life < 0.0F || life >= 1.0F) {
                    continue;
                }
                Vec3 feet = at(frac);
                float alpha = 0.62F * (float) Math.pow(1.0F - life, 1.3) * nearFade(ctx, feet.add(0, 0.9, 0), 1.1, 1.6);
                if (alpha <= 0.01F) {
                    continue;
                }
                // drifts a little backwards as it dissolves
                Vec3 drift = feet.subtract(dir.scale(0.35 * life));
                Afterimage.render(ctx, drift, yaw, lean, k % 2 == 1, FxDraw.argb(alpha, GHOST), skin, slim);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ hit

    /** Two crossing crescent cuts on an entity hit by the dash, facing the camera. */
    static final class HitFx implements ClientFx {
        private static final int LIFE = 16;
        private final Vec3 center;
        private final float size;
        private final float roll;
        private int age;

        HitFx(Vec3 center, Vec3 dir, float height, float width, int index) {
            this.center = center;
            this.size = Mth.clamp(height * 0.75F + width * 0.4F, 0.9F, 3.5F);
            this.roll = (index * 0.83F) % 0.9F - 0.45F;
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                RandomSource rnd = level.random;
                particles(level, ModParticles.CRIMSON_SPARK.get(), center, 14, 0.3 * size, dir.scale(0.2), 0.45, rnd);
                particles(level, ModParticles.SHADOW_MOTE.get(), center, 8, 0.4 * size, dir.scale(0.05), 0.1, rnd);
                particles(level, ModParticles.CRIMSON_SPARK.get(), center, 5, 0.25 * size, dir.scale(0.12), 0.35, rnd);
                level.addParticle(ModParticles.ECLIPSE_SLASH.get(), center.x, center.y, center.z, 0, 0, 0);
            }
        }

        @Override
        public boolean tick() {
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float t = ctx.time(age);
            float alpha = 1.0F - smooth(0.4F, 1.0F, t / LIFE);
            if (alpha <= 0.004F) {
                return;
            }
            PoseStack.Pose pose = ctx.pose().last();
            Vector3f lv = ctx.camera().getLeftVector();
            Vector3f uv = ctx.camera().getUpVector();
            Vec3 left = new Vec3(lv.x(), lv.y(), lv.z());
            Vec3 upv = new Vec3(uv.x(), uv.y(), uv.z());
            Vec3 c = ctx.rel(center);

            if (t < 7.0F) {
                float k = t / 7.0F;
                FxDraw.billboard(pose, ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.GLOW)), ctx.camera(), c,
                        size * (0.7F + 0.6F * k), 0.0F, FxDraw.argb(0.55F * (1.0F - k), BLACK));
                FxDraw.billboard(pose, ctx.buffers().getBuffer(FxRenderTypes.glowShader()), ctx.camera(), c,
                        size * (1.0F + 0.5F * k), 0.0F, FxDraw.argb(0.9F * (1.0F - k), CRIMSON));
            }
            for (int pass = 0; pass < 3; pass++) {
                VertexConsumer vc = ctx.buffers().getBuffer(pass == 1 ? FxRenderTypes.shade(SLASH_MASK) : FxRenderTypes.additive(FxRenderTypes.BEAM));
                for (int stroke = 0; stroke < 2; stroke++) {
                    float theta = (stroke == 0 ? Mth.PI * 0.25F : -Mth.PI * 0.25F) + roll;
                    Vec3 tan = left.scale(Mth.cos(theta)).add(upv.scale(Mth.sin(theta)));
                    Vec3 nrm = left.scale(-Mth.sin(theta)).add(upv.scale(Mth.cos(theta))).scale(stroke == 0 ? 1.0 : -1.0);
                    float r = size * 2.6F * (1.0F + 0.06F * t / LIFE);
                    float half = (float) Math.asin(Math.min(0.95, size * 1.05 / r));
                    Vec3 arcC = c.subtract(nrm.scale(r * Mth.cos(half)));
                    float start = stroke == 0 ? 0.0F : 1.6F;
                    float drawn = Mth.lerp(Mth.clamp((t - start) / 2.0F, 0.0F, 1.0F), -half, half);
                    if (t < start) {
                        continue;
                    }
                    float w = 0.42F * size;
                    switch (pass) {
                        case 0 -> band(pose, vc, arcC, nrm, tan, r, -half, drawn, w, 0.3F, 2.2F, 14, FxDraw.argb(0.55F * alpha, CRIMSON));
                        case 1 -> band(pose, vc, arcC, nrm, tan, r, -half, drawn, w, -1.0F, 1.0F, 16, FxDraw.argb(alpha, BLACK));
                        default -> {
                            band(pose, vc, arcC, nrm, tan, r, -half, drawn, w, 0.55F, 1.12F, 16, FxDraw.argb(alpha, CRIMSON_HOT));
                            band(pose, vc, arcC, nrm, tan, r, -half, drawn, w, 0.86F, 1.0F, 16, FxDraw.argb(0.85F * alpha, HOT_CORE));
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ melee

    /**
     * The Eclipser's ordinary swing: one wide crescent cut through the target, black blade, crimson glow and edge,
     * alternating forehand / backhand diagonals. Drawn in about two ticks, then it fades with sparks and shadow motes.
     */
    static final class MeleeFx implements ClientFx {
        private static final int LIFE = 11;
        private final Vec3 center;
        private final Vec3 forward;
        private final Vec3 side;
        private final Vec3 up;
        private final float sign;
        private final float tilt;
        private final float strength;
        private final int attacker;
        private int age;

        MeleeFx(Vec3 from, Vec3 target, int swing, float strength, int attacker) {
            Vec3 d = target.subtract(from);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            this.forward = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
            this.side = perp(forward);
            this.up = side.cross(forward).normalize();
            this.sign = (swing & 1) == 0 ? 1.0F : -1.0F;
            this.tilt = sign * (0.35F + 0.12F * ((swing >> 1) % 3));
            this.strength = Mth.clamp(strength, 0.3F, 1.0F);
            this.attacker = attacker;
            // the cut passes through the target, centred a little in front of it
            this.center = target.subtract(forward.scale(0.35));
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                RandomSource rnd = level.random;
                particles(level, ModParticles.CRIMSON_SPARK.get(), target, 10, 0.3, side.scale(0.25 * sign), 0.4, rnd);
                particles(level, ModParticles.SHADOW_MOTE.get(), target, 4, 0.35, forward.scale(0.05), 0.08, rnd);
                level.addParticle(ModParticles.ECLIPSE_SLASH.get(), target.x, target.y, target.z, 0, 0, 0);
            }
        }

        @Override
        public boolean tick() {
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float t = ctx.time(age);
            float alpha = (1.0F - smooth(0.3F, 1.0F, t / LIFE)) * (0.65F + 0.35F * strength);
            Minecraft mc = Minecraft.getInstance();
            boolean own = mc.player != null && mc.player.getId() == attacker && mc.options.getCameraType().isFirstPerson();
            alpha *= own ? 0.8F : nearFade(ctx, center, 0.6, 1.2);
            if (alpha <= 0.004F) {
                return;
            }
            PoseStack.Pose pose = ctx.pose().last();
            // the swing plane: across the attacker's body, tipped into a diagonal
            Vec3 e1 = forward;
            Vec3 e2 = side.scale(Mth.cos(tilt)).add(up.scale(Mth.sin(tilt))).scale(sign);
            float r = 1.35F + 0.35F * strength;
            float w = 0.55F + 0.35F * strength;
            Vec3 c = ctx.rel(center.subtract(forward.scale(r * 0.85)));
            float a0 = -1.25F;
            float a1 = 1.25F;
            float drawn = Mth.lerp(Mth.clamp(t / 2.0F, 0.0F, 1.0F), a0, a1);
            for (int pass = 0; pass < 3; pass++) {
                VertexConsumer vc = ctx.buffers().getBuffer(pass == 1 ? FxRenderTypes.shade(SLASH_MASK) : FxRenderTypes.additive(FxRenderTypes.BEAM));
                switch (pass) {
                    case 0 -> band(pose, vc, c, e1, e2, r, a0, drawn, w, 0.3F, 2.1F, 22, FxDraw.argb(0.5F * alpha, CRIMSON));
                    case 1 -> band(pose, vc, c, e1, e2, r, a0, drawn, w, -1.0F, 1.0F, 24, FxDraw.argb(alpha, BLACK));
                    default -> {
                        band(pose, vc, c, e1, e2, r, a0, drawn, w, 0.55F, 1.1F, 24, FxDraw.argb(alpha, CRIMSON_HOT));
                        band(pose, vc, c, e1, e2, r, a0, drawn, w, 0.86F, 1.0F, 24, FxDraw.argb(0.8F * alpha, HOT_CORE));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ bursts

    /** Shockwave of shadows: the end of the dash (crimson rim, flare) or the end of Shadow Walk (darker, softer). */
    static final class BurstFx implements ClientFx {
        private static final int LIFE = 18;
        private final Vec3 pos;
        private final boolean shadowWalk;
        private int age;

        BurstFx(Vec3 pos, boolean shadowWalk) {
            this.pos = pos;
            this.shadowWalk = shadowWalk;
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                RandomSource rnd = level.random;
                Vec3 c = pos.add(0, 1.0, 0);
                burst(level, c, shadowWalk ? 30 : 22, shadowWalk ? 0.3 : 0.4, ModParticles.SHADOW_MOTE.get(), rnd);
                burst(level, c, shadowWalk ? 6 : 16, 0.55, ModParticles.CRIMSON_SPARK.get(), rnd);
                particles(level, ModParticles.CRIMSON_SPARK.get(), c, shadowWalk ? 3 : 7, 0.4, new Vec3(0, 0.1, 0), 0.4, rnd);
            }
        }

        @Override
        public boolean tick() {
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float t = ctx.time(age);
            float k = Mth.clamp(t / LIFE, 0.0F, 1.0F);
            float ease = 1.0F - (1.0F - k) * (1.0F - k);
            PoseStack.Pose pose = ctx.pose().last();
            Vec3 g = ctx.rel(pos.add(0, 0.06, 0));
            float radius = 0.5F + (shadowWalk ? 3.0F : 3.8F) * ease;
            FxDraw.ground(pose, ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.RING)), g, radius, FxDraw.argb(0.9F * (1.0F - k), BLACK));
            if (!shadowWalk || t < 10.0F) {
                FxDraw.ground(pose, ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING)), g, radius * 1.05F,
                        FxDraw.argb((shadowWalk ? 0.4F : 0.9F) * (1.0F - k), CRIMSON));
            }
            Vec3 c = ctx.rel(pos.add(0, 1.0, 0));
            float fade = nearFade(ctx, pos.add(0, 1.0, 0), 0.8, 1.5);
            FxDraw.billboard(pose, ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.GLOW)), ctx.camera(), c,
                    1.0F + 1.6F * ease, 0.0F, FxDraw.argb(0.75F * (1.0F - k) * fade, BLACK));
            if (!shadowWalk && t < 6.0F) {
                FxDraw.billboard(pose, ctx.buffers().getBuffer(FxRenderTypes.glowShader()), ctx.camera(), c,
                        1.8F, 0.0F, FxDraw.argb(0.8F * (1.0F - t / 6.0F) * fade, CRIMSON));
            }
        }
    }
}
