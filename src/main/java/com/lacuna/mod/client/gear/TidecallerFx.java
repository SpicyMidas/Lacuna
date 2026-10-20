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
 *   <li>{@code tide_maelstrom} / {@code tide_geyser}: the whirlpool, its waterspout and the closing geyser, drawn
 *       exactly like the wave (same palette, render types and foam crests) in {@link TideSpout}.</li>
 * </ul>
 */
public final class TidecallerFx {
    // the tide's palette, shared with the whirlpool, waterspout and geyser (TideSpout)
    static final int DEEP = 0x0B3F5E;
    static final int TEAL = 0x1C8CA3;
    static final int CYAN = 0x5ED6E6;
    static final int PALE = 0xB9F1F7;
    static final int FOAM = 0xF0FDFF;
    static final int GLINT = 0x9CF0FF;

    private TidecallerFx() {}

    public static void register() {
        FxHandlers.register(TideCurrents.WAVE_FX, p -> FxManager.add(new Wave(p)));
        // the maelstrom's whirlpool and waterspout and the closing geyser are drawn in the wave's style (TideSpout)
        FxHandlers.register(TideCurrents.MAELSTROM_FX, p -> FxManager.add(new TideSpout.Maelstrom(p)));
        FxHandlers.register(TideCurrents.GEYSER_FX, p -> FxManager.add(new TideSpout.Geyser(p)));
    }

    // ------------------------------------------------------------------ helpers

    static boolean isSelf(FxPayload p) {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getId() == p.entityId();
    }

    static float smooth(float t) {
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
}
