package com.lacuna.mod.client.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.gear.TideCurrents;
import com.lacuna.mod.network.FxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tidecaller Sickle visuals (client), one {@link FxPayload} each:
 * <ul>
 *   <li>{@code tide_wave}: a translucent blue-green breaking wave with a curling lip, a white foam crest, water
 *       streaming up its back and a wet wake behind it; it rolls over the terrain along the same path the server
 *       wave follows ({@link TideCurrents#path}) and crashes at its end. Dense splash, falling water and bubbles.</li>
 *   <li>{@code tide_maelstrom}: a dark whirlpool bowl with turning foam spiral arms, rotating current arcs, a spray
 *       curtain at the rim and a growing spout in the centre; nautilus sparks and splashes stream inward.</li>
 *   <li>{@code tide_geyser}: a roaring column of water that shoots up, sprays and falls back.</li>
 * </ul>
 */
public final class TidecallerFx {
    private static final int DEEP = 0x0B3F5E;
    private static final int TEAL = 0x1C8CA3;
    private static final int CYAN = 0x5ED6E6;
    private static final int PALE = 0xB9F1F7;
    private static final int FOAM = 0xF0FDFF;
    private static final int GLINT = 0x9CF0FF;

    private TidecallerFx() {}

    public static void register() {
        FxHandlers.register(TideCurrents.WAVE_FX, p -> FxManager.add(new Wave(p)));
        FxHandlers.register(TideCurrents.MAELSTROM_FX, p -> FxManager.add(new Maelstrom(p)));
        FxHandlers.register(TideCurrents.GEYSER_FX, p -> FxManager.add(new Geyser(p)));
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isSelf(FxPayload p) {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getId() == p.entityId();
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void vtx(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int argb) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(argb);
    }

    /** Solid translucent quad (samples the middle of the beam mask: a flat fill). Corners in order around the quad. */
    private static void fill(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
        vtx(vc, pose, a, 0.5F, 0.5F, ca);
        vtx(vc, pose, b, 0.5F, 0.5F, cb);
        vtx(vc, pose, c, 0.5F, 0.5F, cc);
        vtx(vc, pose, d, 0.5F, 0.5F, cd);
    }

    /** Flat ribbon from a to b lying in the horizontal plane, soft edges across (u), width w. */
    private static void flatRibbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, float wa, float wb, int ca, int cb) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5) {
            return;
        }
        double sx = -dz / len;
        double sz = dx / len;
        vtx(vc, pose, a.add(-sx * wa * 0.5, 0.0, -sz * wa * 0.5), 0.0F, 0.0F, ca);
        vtx(vc, pose, a.add(sx * wa * 0.5, 0.0, sz * wa * 0.5), 1.0F, 0.0F, ca);
        vtx(vc, pose, b.add(sx * wb * 0.5, 0.0, sz * wb * 0.5), 1.0F, 1.0F, cb);
        vtx(vc, pose, b.add(-sx * wb * 0.5, 0.0, -sz * wb * 0.5), 0.0F, 1.0F, cb);
    }

    /**
     * Open vertical cylinder around {@code c} (camera-relative) from y0 to y1 (offsets from c.y), radius r0 to r1,
     * rotated by {@code spin} with each face's top shifted by {@code slant} radians (a twisting swirl). With
     * {@code soft} each face gets the beam mask across it (bright twisting stripes), otherwise a flat fill.
     */
    private static void swirl(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double y0, double y1, double r0, double r1, int sides,
                              double spin, double slant, int c0, int c1, boolean soft) {
        for (int i = 0; i < sides; i++) {
            double a0 = spin + Mth.TWO_PI * i / sides;
            double a1 = spin + Mth.TWO_PI * (i + 1) / sides;
            float u0 = soft ? 0.0F : 0.5F;
            float u1 = soft ? 1.0F : 0.5F;
            vtx(vc, pose, new Vec3(c.x + Math.cos(a0) * r0, c.y + y0, c.z + Math.sin(a0) * r0), u0, 0.0F, c0);
            vtx(vc, pose, new Vec3(c.x + Math.cos(a0 + slant) * r1, c.y + y1, c.z + Math.sin(a0 + slant) * r1), u0, 1.0F, c1);
            vtx(vc, pose, new Vec3(c.x + Math.cos(a1 + slant) * r1, c.y + y1, c.z + Math.sin(a1 + slant) * r1), u1, 1.0F, c1);
            vtx(vc, pose, new Vec3(c.x + Math.cos(a1) * r0, c.y + y0, c.z + Math.sin(a1) * r0), u1, 0.0F, c0);
        }
    }

    /** BUBBLE only survives in water: in the open air the bubbles pop. */
    private static ParticleOptions bubble(ClientLevel level, double x, double y, double z) {
        return level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER) ? ParticleTypes.BUBBLE : ParticleTypes.BUBBLE_POP;
    }

    // ------------------------------------------------------------------ the tidal wave

    private static final class Wave implements ClientFx {
        /** Side-view profile of the back and the curling lip: forward offset from the crest line, height fraction. */
        private static final float[][] BODY = {
                {-2.7F, 0.0F}, {-1.9F, 0.3F}, {-1.1F, 0.64F}, {-0.45F, 0.9F}, {0.05F, 1.0F}, {0.5F, 0.95F}, {0.82F, 0.78F}, {0.9F, 0.56F}};
        private static final int[] BODY_RGB = {TEAL, TEAL, TEAL, CYAN, PALE, PALE, FOAM, FOAM};
        private static final float[] BODY_A = {0.0F, 0.38F, 0.5F, 0.55F, 0.62F, 0.66F, 0.6F, 0.3F};
        /** The concave face under the lip, from the foam line on the ground up to just under the crest. */
        private static final float[][] FACE = {{1.35F, 0.0F}, {0.9F, 0.08F}, {0.52F, 0.26F}, {0.3F, 0.5F}, {0.2F, 0.76F}, {0.08F, 0.96F}};
        private static final int[] FACE_RGB = {FOAM, TEAL, DEEP, DEEP, TEAL, CYAN};
        private static final float[] FACE_A = {0.5F, 0.42F, 0.5F, 0.5F, 0.45F, 0.4F};
        private static final int COLUMNS = 12;
        private static final float COLLAPSE = 8.0F;

        private final Vec3 origin;
        private final Vec3 dir;
        private final Vec3 side;
        private final double length;
        private final double speed;
        private final double halfWidth;
        private final float crashTime;
        private final int life;
        private final double[][] paths = new double[COLUMNS + 1][];
        private int age;
        private boolean crashed;

        Wave(FxPayload p) {
            this.origin = p.a();
            Vec3 f = p.b().subtract(p.a()).multiply(1, 0, 1);
            this.dir = f.lengthSqr() > 1.0E-6 ? f.normalize() : new Vec3(1, 0, 0);
            this.side = new Vec3(-dir.z, 0.0, dir.x);
            this.length = Math.max(TideCurrents.WAVE_START + 0.5, p.p0());
            this.speed = p.p1() > 0.0F ? p.p1() : Balance.SICKLE_WAVE_SPEED;
            this.halfWidth = p.p2() > 0.0F ? p.p2() : Balance.SICKLE_WAVE_HALF_WIDTH;
            this.crashTime = (float) ((length - TideCurrents.WAVE_START) / speed);
            this.life = Mth.ceil(crashTime) + 20;
            ClientLevel level = Minecraft.getInstance().level;
            for (int j = 0; j <= COLUMNS; j++) {
                double s = lateral(j) * halfWidth;
                Vec3 start = origin.add(side.scale(s));
                // each column of the wave follows its own ground (up to where the centre breaks)
                double y = level == null ? Double.NaN : TideCurrents.surface(level, start.x, start.z, origin.y, TideCurrents.WAVE_CLIMB, 3);
                Vec3 from = new Vec3(start.x, Double.isNaN(y) ? origin.y : y, start.z);
                paths[j] = level == null ? new double[] {origin.y} : TideCurrents.path(level, from, dir, length + 3.0, false);
            }
            if (isSelf(p)) {
                ScreenFx.shake(1.6F, 12);
            }
        }

        private static double lateral(int j) {
            return -1.0 + 2.0 * j / COLUMNS;
        }

        private double front(float time) {
            return Math.min(length, TideCurrents.WAVE_START + speed * Math.max(0.0F, time));
        }

        /** World position on column j at distance d along the wave, {@code up} blocks above that column's ground. */
        private Vec3 point(int j, double d, double up) {
            double s = lateral(j) * halfWidth;
            double ground = TideCurrents.heightAt(paths[j], d);
            return new Vec3(origin.x + dir.x * d + side.x * s, ground + up, origin.z + dir.z * d + side.z * s);
        }

        /** Same, between columns (s in -1..1). */
        private Vec3 pointAt(double s, double d, double up) {
            double fj = (s + 1.0) * 0.5 * COLUMNS;
            int j = Mth.clamp((int) Math.round(fj), 0, COLUMNS);
            double ground = TideCurrents.heightAt(paths[j], d);
            double lat = s * halfWidth;
            return new Vec3(origin.x + dir.x * d + side.x * lat, ground + up, origin.z + dir.z * d + side.z * lat);
        }

        private static float lag(double s) {
            return (float) (0.9 * s * s);
        }

        private float height(double s, float time, float grow, float collapse) {
            return (float) (Balance.SICKLE_WAVE_HEIGHT * grow * (1.0F - 0.7F * collapse) * (1.0 - 0.4 * s * s)
                    * (1.0 + 0.06 * Mth.sin(time * 0.9F + (float) s * 5.0F)));
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                if (age <= crashTime) {
                    spray(level);
                } else if (!crashed) {
                    crashed = true;
                    crash(level);
                }
            }
            return ++age < life;
        }

        private void spray(ClientLevel level) {
            RandomSource r = level.random;
            double d = front(age);
            float h = height(0.0, age, smooth((age + 1) / 5.0F), 0.0F);
            for (int i = 0; i < 4; i++) {
                double s = r.nextDouble() * 2.0 - 1.0;
                double dj = d - lag(s);
                // forward splash skidding off the foot of the wave
                Vec3 foot = pointAt(s, dj + 1.3, 0.1);
                level.addParticle(ParticleTypes.SPLASH, foot.x, foot.y, foot.z, dir.x * 0.3 + side.x * (r.nextDouble() - 0.5) * 0.2,
                        0.0, dir.z * 0.3 + side.z * (r.nextDouble() - 0.5) * 0.2);
                // spray thrown off the crest
                Vec3 crest = pointAt(s, dj + 0.2, h * (1.0 - 0.4 * s * s));
                level.addParticle(ParticleTypes.SPLASH, crest.x, crest.y, crest.z, 0.0, 0.0, 0.0);
            }
            for (int i = 0; i < 3; i++) {
                double s = r.nextDouble() * 2.0 - 1.0;
                double dj = d - lag(s);
                Vec3 lip = pointAt(s, dj + 0.85, h * 0.6 * (1.0 - 0.4 * s * s));
                level.addParticle(ParticleTypes.FALLING_WATER, lip.x, lip.y, lip.z, 0.0, 0.0, 0.0);
                Vec3 body = pointAt(s, dj - 0.6 - r.nextDouble(), h * (0.2 + r.nextDouble() * 0.5));
                level.addParticle(bubble(level, body.x, body.y, body.z), body.x, body.y, body.z, dir.x * 0.08, 0.06, dir.z * 0.08);
            }
            if ((age & 1) == 0) {
                double s = r.nextDouble() * 2.0 - 1.0;
                Vec3 glint = pointAt(s, d - lag(s) - 0.5, h * 0.7);
                level.addParticle(ParticleTypes.DOLPHIN, glint.x, glint.y, glint.z, 0.0, 0.0, 0.0);
            }
        }

        /** The wave breaks: a wall of spray along its whole width. */
        private void crash(ClientLevel level) {
            RandomSource r = level.random;
            for (int i = 0; i < 36; i++) {
                double s = r.nextDouble() * 2.0 - 1.0;
                double dj = length - lag(s);
                Vec3 at = pointAt(s, dj + r.nextDouble() * 1.2, 0.2 + r.nextDouble() * 1.8);
                level.addParticle(ParticleTypes.SPLASH, at.x, at.y, at.z, dir.x * (0.15 + r.nextDouble() * 0.2), 0.0,
                        dir.z * (0.15 + r.nextDouble() * 0.2));
                if (i % 3 == 0) {
                    level.addParticle(ParticleTypes.FALLING_WATER, at.x, at.y + 0.6, at.z, 0.0, 0.0, 0.0);
                    level.addParticle(bubble(level, at.x, at.y, at.z), at.x, at.y, at.z, 0.0, 0.08, 0.0);
                }
                if (i % 6 == 0) {
                    level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, dir.x * 0.06, 0.03, dir.z * 0.06);
                }
            }
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float grow = smooth((time + 1.0F) / 5.0F);
            float collapse = Mth.clamp((time - crashTime) / COLLAPSE, 0.0F, 1.0F);
            float fade = 1.0F - collapse;
            double d = front(time);
            PoseStack.Pose pose = ctx.pose().last();
            Vec3 cam = ctx.cam();

            wake(ctx, pose, time, d);
            if (fade <= 0.0F) {
                return;
            }
            float curl = 0.5F + 0.5F * Mth.sin(time * 0.7F);
            Vec3[][] body = new Vec3[COLUMNS + 1][BODY.length];
            Vec3[][] face = new Vec3[COLUMNS + 1][FACE.length];
            float[] alpha = new float[COLUMNS + 1];
            for (int j = 0; j <= COLUMNS; j++) {
                double s = lateral(j);
                double dj = d - lag(s);
                float h = height(s, time, grow, collapse);
                alpha[j] = (float) (1.0 - s * s * s * s) * fade * (0.4F + 0.6F * grow);
                for (int k = 0; k < BODY.length; k++) {
                    double f = BODY[k][0];
                    double up = BODY[k][1];
                    if (k >= 5) {
                        // the lip curls and, once the wave breaks, pitches forward and falls
                        f += 0.08 * curl * (k - 4) + collapse * 1.3;
                        up -= 0.05 * curl * (k - 4) + collapse * 0.3;
                    }
                    body[j][k] = point(j, dj + f, Math.max(0.0, up) * h).subtract(cam);
                }
                for (int k = 0; k < FACE.length; k++) {
                    face[j][k] = point(j, dj + FACE[k][0] + collapse * 0.6, FACE[k][1] * h).subtract(cam);
                }
            }

            // translucent water: the hollow face under the lip, then the back and the lip
            VertexConsumer water = ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.BEAM));
            for (int j = 0; j < COLUMNS; j++) {
                for (int k = 0; k < FACE.length - 1; k++) {
                    fill(water, pose, face[j][k], face[j][k + 1], face[j + 1][k + 1], face[j + 1][k],
                            FxDraw.argb(FACE_A[k] * alpha[j], FACE_RGB[k]), FxDraw.argb(FACE_A[k + 1] * alpha[j], FACE_RGB[k + 1]),
                            FxDraw.argb(FACE_A[k + 1] * alpha[j + 1], FACE_RGB[k + 1]), FxDraw.argb(FACE_A[k] * alpha[j + 1], FACE_RGB[k]));
                }
                for (int k = 0; k < BODY.length - 1; k++) {
                    fill(water, pose, body[j][k], body[j][k + 1], body[j + 1][k + 1], body[j + 1][k],
                            FxDraw.argb(BODY_A[k] * alpha[j], BODY_RGB[k]), FxDraw.argb(BODY_A[k + 1] * alpha[j], BODY_RGB[k + 1]),
                            FxDraw.argb(BODY_A[k + 1] * alpha[j + 1], BODY_RGB[k + 1]), FxDraw.argb(BODY_A[k] * alpha[j + 1], BODY_RGB[k]));
                }
            }

            // light: foam crest, glowing lip edge, foam line at the foot and water streaming up the back
            VertexConsumer light = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            for (int j = 0; j < COLUMNS; j++) {
                float a0 = alpha[j];
                float a1 = alpha[j + 1];
                FxDraw.ribbon(pose, light, body[j][4], body[j + 1][4], 1.0F, 1.0F, FxDraw.argb(0.22F * a0, CYAN),
                        FxDraw.argb(0.22F * a1, CYAN), 0.0F, 1.0F);
                FxDraw.ribbon(pose, light, body[j][5], body[j + 1][5], 0.42F, 0.42F, FxDraw.argb(0.75F * a0, FOAM),
                        FxDraw.argb(0.75F * a1, FOAM), 0.0F, 1.0F);
                FxDraw.ribbon(pose, light, body[j][7], body[j + 1][7], 0.22F, 0.22F, FxDraw.argb(0.5F * a0, FOAM),
                        FxDraw.argb(0.5F * a1, FOAM), 0.0F, 1.0F);
                FxDraw.ribbon(pose, light, face[j][0].add(0, 0.06, 0), face[j + 1][0].add(0, 0.06, 0), 0.55F, 0.55F,
                        FxDraw.argb(0.45F * a0, FOAM), FxDraw.argb(0.45F * a1, FOAM), 0.0F, 1.0F);
            }
            for (int n = 0; n < 7; n++) {
                int j = 1 + n * (COLUMNS - 2) / 6;
                float q = (time * 0.09F + n * 0.618F) % 1.0F;
                Vec3 a = along(body[j], q);
                Vec3 b = along(body[j], Math.min(1.0F, q + 0.18F));
                FxDraw.ribbon(pose, light, a, b, 0.12F, 0.08F, FxDraw.argb(0.0F, GLINT),
                        FxDraw.argb(0.5F * Mth.sin(q * Mth.PI) * alpha[j], GLINT), 0.0F, 1.0F);
            }
        }

        /** Point at fraction q (0 back foot .. 1 crest) along a column of the back profile. */
        private static Vec3 along(Vec3[] profile, float q) {
            float x = Mth.clamp(q, 0.0F, 1.0F) * 5.0F;
            int i = Math.min(4, (int) x);
            float f = x - i;
            return profile[i].add(profile[i + 1].subtract(profile[i]).scale(f));
        }

        /** A wet sheen left on the ground behind the wave, drying within a second. */
        private void wake(FxRenderContext ctx, PoseStack.Pose pose, float time, double d) {
            double end = d - 2.4;
            double startD = TideCurrents.WAVE_START;
            if (end <= startD) {
                return;
            }
            Vec3 cam = ctx.cam();
            VertexConsumer vc = ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.BEAM));
            for (double a = startD; a < end; a += 1.0) {
                double b = Math.min(end, a + 1.0);
                float wa = wakeAlpha(time, a);
                float wb = wakeAlpha(time, b);
                if (wa <= 0.004F && wb <= 0.004F) {
                    continue;
                }
                for (int j = 0; j < COLUMNS; j++) {
                    float e0 = (float) (1.0 - Math.pow(lateral(j), 4));
                    float e1 = (float) (1.0 - Math.pow(lateral(j + 1), 4));
                    fill(vc, pose, point(j, a, 0.04).subtract(cam), point(j, b, 0.04).subtract(cam),
                            point(j + 1, b, 0.04).subtract(cam), point(j + 1, a, 0.04).subtract(cam),
                            FxDraw.argb(wa * e0, TEAL), FxDraw.argb(wb * e0, TEAL), FxDraw.argb(wb * e1, TEAL), FxDraw.argb(wa * e1, TEAL));
                }
            }
        }

        private float wakeAlpha(float time, double at) {
            float passed = (float) ((at + 2.4 - TideCurrents.WAVE_START) / speed);
            return 0.24F * Mth.clamp(1.0F - (time - passed) / 16.0F, 0.0F, 1.0F) * Mth.clamp((time - passed) / 2.0F + 1.0F, 0.0F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ the maelstrom

    private static final class Maelstrom implements ClientFx {
        private static final int ARMS = 5;
        private static final int RINGS = 6;
        private static final int SEGMENTS = 32;
        private final Vec3 center;
        private final float radius;
        private final int duration;
        private int age;

        Maelstrom(FxPayload p) {
            this.center = p.a();
            this.radius = p.p0() > 0.0F ? p.p0() : (float) Balance.SICKLE_MAELSTROM_RADIUS;
            this.duration = p.p1() > 0.0F ? (int) p.p1() : Balance.SICKLE_MAELSTROM_TICKS;
            if (isSelf(p)) {
                ScreenFx.shake(1.2F, 10);
            }
        }

        private float envelope(float time) {
            return smooth(time / 14.0F) * (1.0F - Mth.clamp((time - duration) / 5.0F, 0.0F, 1.0F));
        }

        /** Height of the whirlpool bowl above the centre at radius r: the rim rises, the middle sinks away. */
        private double bowl(double r, float env) {
            double f = r / radius;
            return 0.06 + 0.5 * f * f * env;
        }

        @Override
        public boolean tick() {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level != null && age < duration) {
                RandomSource r = level.random;
                float env = envelope(age);
                double target = center.y + 1.4;
                for (int i = 0; i < 4; i++) {
                    // conduit sparks spiral in towards the eye
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = radius * (0.5 + 0.5 * r.nextDouble());
                    level.addParticle(ParticleTypes.NAUTILUS, center.x, target, center.z, Math.cos(a) * rad, -1.0 + r.nextDouble(),
                            Math.sin(a) * rad);
                }
                for (int i = 0; i < 6; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = radius * (0.15 + 0.85 * r.nextDouble());
                    double x = center.x + Math.cos(a) * rad;
                    double z = center.z + Math.sin(a) * rad;
                    double y = center.y + bowl(rad, env) + 0.05;
                    // tangential (counter-clockwise from above) plus a little inward
                    double tx = -Math.sin(a);
                    double tz = Math.cos(a);
                    double spin = 0.18 + 0.2 * (1.0 - rad / radius);
                    double vx = tx * spin - Math.cos(a) * 0.08;
                    double vz = tz * spin - Math.sin(a) * 0.08;
                    if (i < 3) {
                        level.addParticle(ParticleTypes.SPLASH, x, y, z, vx, 0.0, vz);
                    } else if (i < 5) {
                        level.addParticle(ParticleTypes.FISHING, x, y, z, vx, 0.02, vz);
                    } else {
                        level.addParticle(bubble(level, x, y - 0.2, z), x, y, z, vx * 0.5, 0.08, vz * 0.5);
                    }
                }
                if ((age & 1) == 0) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = r.nextDouble() * 1.2;
                    level.addParticle(ParticleTypes.BUBBLE_POP, center.x + Math.cos(a) * rad, center.y + 0.3 + r.nextDouble() * 1.5 * env,
                            center.z + Math.sin(a) * rad, 0.0, 0.05, 0.0);
                    level.addParticle(ParticleTypes.DOLPHIN, center.x + Math.cos(a) * radius * 0.7, center.y + 0.4,
                            center.z + Math.sin(a) * radius * 0.7, 0.0, 0.0, 0.0);
                }
                // anyone caught inside sees the water close around them
                Player self = mc.player;
                if (self != null && self.distanceToSqr(center.x, self.getY(), center.z) < radius * radius
                        && Math.abs(self.getY() - center.y) < 6.0) {
                    ScreenFx.hold("tide_maelstrom", 0x0F4F73, 0.45F * env, true);
                }
            }
            return ++age < duration + 6;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float env = envelope(time);
            if (env <= 0.0F) {
                return;
            }
            PoseStack.Pose pose = ctx.pose().last();
            Vec3 c = ctx.rel(center);
            float spin = time * 0.15F;

            // the bowl: deep blue eye fading out to the rim
            VertexConsumer water = ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.BEAM));
            for (int i = 0; i < RINGS; i++) {
                double r0 = radius * i / RINGS;
                double r1 = radius * (i + 1) / RINGS;
                int c0 = ringColor(i / (float) RINGS, env);
                int c1 = ringColor((i + 1) / (float) RINGS, env);
                for (int k = 0; k < SEGMENTS; k++) {
                    double a0 = Mth.TWO_PI * k / SEGMENTS;
                    double a1 = Mth.TWO_PI * (k + 1) / SEGMENTS;
                    fill(water, pose, disc(c, r0, a0, env), disc(c, r1, a0, env), disc(c, r1, a1, env), disc(c, r0, a1, env), c0, c1, c1, c0);
                }
            }
            // translucent body of the central spout
            double spout = (0.6 + 2.2 * Mth.clamp(time / duration, 0.0F, 1.0F)) * env;
            swirl(water, pose, c.add(0, bowl(0.0, env), 0), 0.0, spout, 0.35, 1.05, 12, -spin * 2.0, 0.9,
                    FxDraw.argb(0.55F * env, TEAL), FxDraw.argb(0.0F, CYAN), false);

            VertexConsumer light = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            // foam spiral arms winding into the eye
            for (int arm = 0; arm < ARMS; arm++) {
                double base = arm * Mth.TWO_PI / ARMS + spin * 0.85;
                Vec3 prev = null;
                float prevW = 0.0F;
                int prevC = 0;
                int prevG = 0;
                for (int q = 0; q <= 22; q++) {
                    double f = q / 22.0;
                    double r = radius * 0.97 * (1.0 - f) + 0.25;
                    double a = base + 2.1 * Math.log(radius / r);
                    Vec3 p = disc(c, r, a, env).add(0, 0.03, 0);
                    float fall = (float) Math.sqrt(Math.max(0.0, Math.sin(Math.PI * Math.min(1.0, r / radius * 1.02))));
                    float w = (float) (0.12 + 0.55 * r / radius);
                    int col = FxDraw.argb(0.6F * fall * env, FOAM);
                    int glow = FxDraw.argb(0.16F * fall * env, CYAN);
                    if (prev != null) {
                        flatRibbon(light, pose, prev, p, prevW * 2.6F, w * 2.6F, prevG, glow);
                        flatRibbon(light, pose, prev, p, prevW, w, prevC, col);
                    }
                    prev = p;
                    prevW = w;
                    prevC = col;
                    prevG = glow;
                }
            }
            // current arcs, turning faster towards the eye
            float[] radii = {0.86F, 0.6F, 0.36F};
            float[] speeds = {0.1F, 0.17F, 0.28F};
            for (int i = 0; i < radii.length; i++) {
                float r = radius * radii[i];
                Vec3 at = c.add(0, bowl(r, env) + 0.05, 0);
                for (int half = 0; half < 2; half++) {
                    float a0 = time * speeds[i] + half * Mth.PI + i * 0.9F;
                    FxDraw.arc(pose, light, at, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r, a0, a0 + 1.9F, 0.34F - 0.05F * i, 14,
                            FxDraw.argb(0.42F * env, GLINT));
                }
            }
            // spray curtain whirling round the rim and the bright twisting spout
            double rim = radius * 0.94;
            swirl(light, pose, c.add(0, bowl(rim, env), 0), 0.0, 0.8 * env, rim, rim * 1.04, 28, spin, 0.18,
                    FxDraw.argb(0.32F * env, CYAN), FxDraw.argb(0.0F, FOAM), true);
            swirl(light, pose, c.add(0, bowl(0.0, env), 0), 0.0, spout, 0.3, 1.0, 10, -spin * 2.6, 1.1,
                    FxDraw.argb(0.5F * env, FOAM), FxDraw.argb(0.0F, CYAN), true);
            FxDraw.ground(pose, ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING)), c.add(0, bowl(radius, env) + 0.04, 0),
                    radius * 1.05F, FxDraw.argb(0.3F * env, CYAN));
        }

        private Vec3 disc(Vec3 c, double r, double a, float env) {
            return new Vec3(c.x + Math.cos(a) * r, c.y + bowl(r, env), c.z + Math.sin(a) * r);
        }

        /** Colour of the bowl at fraction f of the radius: a dark eye, blue-green water, transparent at the rim. */
        private static int ringColor(float f, float env) {
            int rgb = f < 0.35F ? DEEP : f < 0.75F ? TEAL : CYAN;
            float a = f < 0.35F ? 0.7F - 0.4F * (f / 0.35F) : 0.3F * (1.0F - (f - 0.35F) / 0.65F) + 0.05F;
            if (f >= 0.999F) {
                a = 0.0F;
            }
            return FxDraw.argb(a * env, rgb);
        }
    }

    // ------------------------------------------------------------------ the geyser

    private static final class Geyser implements ClientFx {
        private static final int LIFE = 32;
        private static final int RISE = 5;
        private static final int HOLD = 14;
        private final Vec3 center;
        private final float radius;
        private final float height;
        private int age;

        Geyser(FxPayload p) {
            this.center = p.a();
            this.radius = p.p0() > 0.0F ? p.p0() : (float) Balance.SICKLE_MAELSTROM_RADIUS;
            this.height = p.p1() > 0.0F ? p.p1() : TideCurrents.GEYSER_HEIGHT;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                double dist = mc.player.position().distanceTo(center);
                if (dist < 18.0) {
                    ScreenFx.shake((float) (3.0 * (1.0 - dist / 18.0)) + 0.4F, 14);
                }
                if (dist < radius) {
                    ScreenFx.flash(0xCFF5FF, 0.25F, 8);
                }
            }
            ClientLevel level = mc.level;
            if (level != null) {
                RandomSource r = level.random;
                for (int i = 0; i < 30; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = 0.5 + r.nextDouble() * 2.5;
                    double x = center.x + Math.cos(a) * rad;
                    double z = center.z + Math.sin(a) * rad;
                    level.addParticle(ParticleTypes.SPLASH, x, center.y + 0.2, z, Math.cos(a) * 0.35, 0.0, Math.sin(a) * 0.35);
                    if (i % 3 == 0) {
                        level.addParticle(ParticleTypes.CLOUD, x, center.y + 0.3, z, Math.cos(a) * 0.12, 0.05, Math.sin(a) * 0.12);
                        level.addParticle(bubble(level, x, center.y, z), x, center.y + 0.3, z, 0.0, 0.15, 0.0);
                    }
                }
            }
        }

        private float columnHeight(float time) {
            if (time < RISE) {
                float t = time / RISE;
                return height * (1.0F - (1.0F - t) * (1.0F - t));
            }
            if (time < HOLD) {
                return height * (1.0F + 0.04F * Mth.sin(time * 1.7F));
            }
            float t = Mth.clamp((time - HOLD) / (LIFE - HOLD), 0.0F, 1.0F);
            return height * (1.0F - t * t);
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age < HOLD + 6) {
                RandomSource r = level.random;
                float h = columnHeight(age);
                for (int i = 0; i < 8; i++) {
                    // spray peeling off the column at every height
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double y = center.y + r.nextDouble() * h;
                    double rad = 1.0 + 0.4 * r.nextDouble();
                    level.addParticle(ParticleTypes.SPLASH, center.x + Math.cos(a) * rad, y, center.z + Math.sin(a) * rad,
                            Math.cos(a) * 0.25, 0.0, Math.sin(a) * 0.25);
                }
                for (int i = 0; i < 6; i++) {
                    // the crown rains back down
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = 0.5 + r.nextDouble() * 2.6;
                    level.addParticle(ParticleTypes.FALLING_WATER, center.x + Math.cos(a) * rad, center.y + h * (0.8 + 0.2 * r.nextDouble()),
                            center.z + Math.sin(a) * rad, 0.0, 0.0, 0.0);
                }
                double a = r.nextDouble() * Mth.TWO_PI;
                level.addParticle(ParticleTypes.CLOUD, center.x + Math.cos(a) * 0.8, center.y + h, center.z + Math.sin(a) * 0.8,
                        Math.cos(a) * 0.1, 0.02, Math.sin(a) * 0.1);
            }
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float h = columnHeight(time);
            float fade = 1.0F - Mth.clamp((time - HOLD) / (LIFE - HOLD), 0.0F, 1.0F);
            if (fade <= 0.0F || h <= 0.05F) {
                return;
            }
            PoseStack.Pose pose = ctx.pose().last();
            Vec3 c = ctx.rel(center);
            float spin = time * 0.35F;
            VertexConsumer water = ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.BEAM));
            swirl(water, pose, c, 0.0, h, 1.45, 0.95, 16, spin, 0.6, FxDraw.argb(0.5F * fade, TEAL), FxDraw.argb(0.3F * fade, PALE), false);
            // a skirt of water bursting out around the foot
            float burst = Mth.clamp(time / 8.0F, 0.0F, 1.0F);
            swirl(water, pose, c, 0.05, 0.4 + 1.0 * (1.0F - burst), 1.5, 1.6 + 2.6 * burst, 20, -spin, 0.0,
                    FxDraw.argb(0.4F * fade * (1.0F - burst * 0.6F), CYAN), FxDraw.argb(0.0F, FOAM), false);

            VertexConsumer light = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            swirl(light, pose, c, 0.0, h, 1.5, 1.0, 12, spin * 1.4, 0.9, FxDraw.argb(0.55F * fade, FOAM),
                    FxDraw.argb(0.35F * fade, GLINT), true);
            swirl(light, pose, c, h * 0.82, h * 1.06, 1.0, 2.2, 14, -spin, 0.4, FxDraw.argb(0.45F * fade, FOAM),
                    FxDraw.argb(0.0F, PALE), true);
            VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            for (int i = 0; i < 6; i++) {
                double a = spin * 0.5 + i * Mth.TWO_PI / 6.0;
                Vec3 puff = c.add(Math.cos(a) * 1.2, h * 1.02, Math.sin(a) * 1.2);
                FxDraw.billboard(pose, glow, ctx.camera(), puff, 1.1F, (float) a, FxDraw.argb(0.3F * fade, FOAM));
            }
            float ring = Mth.clamp(time / 10.0F, 0.0F, 1.0F);
            FxDraw.ground(pose, ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING)), c.add(0, 0.06, 0),
                    1.5F + radius * ring, FxDraw.argb(0.6F * (1.0F - ring), FOAM));
        }
    }
}
