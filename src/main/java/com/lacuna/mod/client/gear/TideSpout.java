package com.lacuna.mod.client.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.gear.TideCurrents;
import com.lacuna.mod.network.FxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Tidecaller's Maelstrom and its closing geyser, drawn exactly like the Tidal Wave ({@link TidecallerFx}).
 * <p>
 * Same palette, same two render types and the same treatment as the wave: smooth translucent water shaded per vertex
 * in {@code shade(BEAM)} (deep blue-green where you look straight into the water, pale and denser where the surface
 * turns away from the eye, lighter streams winding through it) and crisp additive foam lines on the crests with glints
 * streaming up the water in {@code additive(BEAM)} (the nearest-filtered beam mask: no soft glow, hard alpha cut-off).
 * Every surface is a {@link Shell}, a ring-by-ring surface of revolution rebuilt in place each frame; an effect is two
 * draw calls, batched with the wave's, and both types have Fabulous depth twins like the wave.
 * <ul>
 *   <li>{@code tide_maelstrom}: a whirlpool bowl around a dark eye with spiral streams and foam arms turning in, and a
 *       foam-crested swell at the rim; from the eye a waterspout twists up - a flared foot, a narrow neck and a swaying
 *       funnel widening upward, streams of water winding up it and a curling foam crown like the wave's lip. Vanilla
 *       particles: splashes flung off the spout, bubbles whirling up it, water dripping from the crown, wakes circling
 *       the pool.</li>
 *   <li>{@code tide_geyser}: the spout collapses into a column of water that shoots up and bursts into a curling foam
 *       crown with a falling curtain; a foam line races out over the ground and leaves a wet sheen, like the wave's
 *       wake.</li>
 * </ul>
 */
final class TideSpout {
    private static final int DEEP = TidecallerFx.DEEP;
    private static final int TEAL = TidecallerFx.TEAL;
    private static final int CYAN = TidecallerFx.CYAN;
    private static final int PALE = TidecallerFx.PALE;
    private static final int FOAM = TidecallerFx.FOAM;
    private static final int GLINT = TidecallerFx.GLINT;
    /** The water ramp: 0 deep eye, 0.25 the wave's body, 0.5 its lighter water, 0.75 the crest, 1 foam. */
    private static final int[] RAMP = {DEEP, TEAL, CYAN, PALE, FOAM};
    /** Lowest vertex alpha (0..255) that survives the core shader's 0.1 alpha cut-off on the beam mask's centre. */
    private static final int VISIBLE = 27;

    private TideSpout() {}

    // ------------------------------------------------------------------ the wave's look

    /** The wave's water (translucent, depth-sorted). */
    private static RenderType water() {
        return FxRenderTypes.shade(FxRenderTypes.BEAM);
    }

    /** The wave's foam and glints (additive, beam mask across each line). */
    private static RenderType light() {
        return FxRenderTypes.additive(FxRenderTypes.BEAM);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    private static int ramp(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F) * (RAMP.length - 1);
        int i = Math.min(RAMP.length - 2, (int) x);
        return mix(RAMP[i], RAMP[i + 1], x - i);
    }

    /**
     * Water at ramp position t and alpha a, seen at {@code edge} (0 face-on .. 1 grazing): like real water it turns
     * paler and denser where the surface turns away from the eye, which gives the columns their round body.
     */
    private static int tone(float t, float a, float edge) {
        float e = edge * edge;
        return FxDraw.argb(Math.min(0.85F, a * (1.0F + 0.6F * e)), ramp(t + 0.3F * e));
    }

    /** 1 - |cos| of the angle between the view ray to camera-relative point p and the unit normal n. */
    private static float edge(double px, double py, double pz, double nx, double ny, double nz) {
        double len = Math.sqrt(px * px + py * py + pz * pz);
        return len < 1.0E-4 ? 0.0F : 1.0F - (float) Math.min(1.0, Math.abs((px * nx + py * ny + pz * nz) / len));
    }

    /** Per-column weight of the lighter streams winding through the water: n soft stripes round a ring. */
    private static float[] streams(int sides, int n) {
        float[] s = new float[sides];
        for (int i = 0; i < sides; i++) {
            float v = 0.5F + 0.5F * Mth.cos(Mth.TWO_PI * n * i / sides);
            s[i] = v * v;
        }
        return s;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float v, int argb) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setUv(u, v).setColor(argb);
    }

    /** A foam line lying flat on the water from a to b (beam mask across it), widths wa / wb. */
    private static void flat(VertexConsumer vc, PoseStack.Pose pose, double ax, double ay, double az, double bx, double by, double bz,
                             float wa, float wb, int ca, int cb) {
        if ((ca >>> 24) < VISIBLE && (cb >>> 24) < VISIBLE) {
            return;
        }
        double dx = bx - ax;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5) {
            return;
        }
        double sx = -dz / len * 0.5;
        double sz = dx / len * 0.5;
        vertex(vc, pose, ax - sx * wa, ay, az - sz * wa, 0.0F, 0.0F, ca);
        vertex(vc, pose, ax + sx * wa, ay, az + sz * wa, 1.0F, 0.0F, ca);
        vertex(vc, pose, bx + sx * wb, by, bz + sz * wb, 1.0F, 1.0F, cb);
        vertex(vc, pose, bx - sx * wb, by, bz - sz * wb, 0.0F, 1.0F, cb);
    }

    /**
     * Sets ring {@code row} of a shell: centre (cx, cy, cz) camera-relative, radius r, turned by {@code turn} radians,
     * profile slope (dr, dy) for the normal; t / a: ramp position and alpha, with {@code w * stream[i]} lighter water on
     * the streams (the streams follow the mesh's own columns, so a turn that changes with height makes them wind).
     */
    private static void ring(Shell s, int row, double cx, double cy, double cz, double r, double turn, double dr, double dy,
                             float t, float a, float w, float[] stream) {
        double len = Math.sqrt(dr * dr + dy * dy);
        double nr = len < 1.0E-6 ? 1.0 : dy / len;
        double nu = len < 1.0E-6 ? 0.0 : -dr / len;
        double ct = Math.cos(turn);
        double st = Math.sin(turn);
        for (int i = 0; i < s.sides; i++) {
            double cos = s.cos[i] * ct - s.sin[i] * st;
            double sin = s.sin[i] * ct + s.cos[i] * st;
            double x = cx + cos * r;
            double z = cz + sin * r;
            float k = stream == null ? 0.0F : w * stream[i];
            s.set(row, i, x, cy, z, tone(t + 0.2F * k, a * (1.0F + 0.3F * k), edge(x, cy, z, cos * nr, nu, sin * nr)));
        }
    }

    /**
     * A ring-by-ring surface of revolution ({@code rows} rings of {@code sides} vertices), allocated once per effect
     * and rebuilt in place every frame: camera-relative positions and one colour per vertex.
     */
    private static final class Shell {
        final int sides;
        final double[] cos;
        final double[] sin;
        private final float[] x;
        private final float[] y;
        private final float[] z;
        private final int[] argb;

        Shell(int rows, int sides) {
            this.sides = sides;
            this.cos = new double[sides];
            this.sin = new double[sides];
            for (int i = 0; i < sides; i++) {
                cos[i] = Math.cos(Mth.TWO_PI * i / sides);
                sin[i] = Math.sin(Mth.TWO_PI * i / sides);
            }
            int n = rows * sides;
            this.x = new float[n];
            this.y = new float[n];
            this.z = new float[n];
            this.argb = new int[n];
        }

        void set(int row, int i, double px, double py, double pz, int colour) {
            int k = row * sides + i;
            x[k] = (float) px;
            y[k] = (float) py;
            z[k] = (float) pz;
            argb[k] = colour;
        }

        Vec3 at(int row, int i) {
            int k = row * sides + Math.floorMod(i, sides);
            return new Vec3(x[k], y[k], z[k]);
        }

        private void emit(VertexConsumer vc, PoseStack.Pose pose, int k) {
            vc.addVertex(pose, x[k], y[k], z[k]).setUv(0.5F, 0.5F).setColor(argb[k]);
        }

        /** The water between rings {@code from} .. {@code to}: the wave's flat fill (the beam mask's centre). */
        void fill(VertexConsumer vc, PoseStack.Pose pose, int from, int to) {
            for (int row = from; row < to; row++) {
                for (int i = 0; i < sides; i++) {
                    int a = row * sides + i;
                    int b = row * sides + (i + 1) % sides;
                    int c = b + sides;
                    int d = a + sides;
                    if (Math.max(Math.max(argb[a] >>> 24, argb[b] >>> 24), Math.max(argb[c] >>> 24, argb[d] >>> 24)) < VISIBLE) {
                        continue;
                    }
                    emit(vc, pose, a);
                    emit(vc, pose, b);
                    emit(vc, pose, c);
                    emit(vc, pose, d);
                }
            }
        }

        /** The wave's crest line round ring {@code row}: camera-facing ribbon segments through its vertices. */
        void crest(VertexConsumer vc, PoseStack.Pose pose, int row, float width, int colour) {
            if ((colour >>> 24) < VISIBLE) {
                return;
            }
            Vec3 first = at(row, 0);
            Vec3 prev = first;
            for (int i = 1; i <= sides; i++) {
                Vec3 next = i == sides ? first : at(row, i);
                FxDraw.ribbon(pose, vc, prev, next, width, colour, 0.0F, 1.0F);
                prev = next;
            }
        }
    }

    // ------------------------------------------------------------------ particles

    private static boolean wet(ClientLevel level, double x, double y, double z) {
        return level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
    }

    /** Rising bubbles: a real bubble column particle under water, a popping bubble in the open air. */
    private static ParticleOptions rising(ClientLevel level, double x, double y, double z) {
        return wet(level, x, y, z) ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.BUBBLE_POP;
    }

    // ------------------------------------------------------------------ the maelstrom

    static final class Maelstrom implements ClientFx {
        /** Pool rings as fractions of the radius, from the eye out over the swell at the rim (CREST) to its foot. */
        private static final float[] POOL = {0.03F, 0.1F, 0.19F, 0.3F, 0.43F, 0.56F, 0.68F, 0.78F, 0.86F, 0.93F, 1.0F};
        private static final int CREST_ROW = 8;
        private static final float CREST = POOL[CREST_ROW];
        /** Ramp position and alpha of each pool ring: a dark eye, the wave's body water, a pale swell fading at its foot. */
        private static final float[] POOL_T = {0.02F, 0.05F, 0.1F, 0.16F, 0.24F, 0.32F, 0.42F, 0.54F, 0.66F, 0.6F, 0.52F};
        private static final float[] POOL_A = {0.62F, 0.6F, 0.56F, 0.52F, 0.48F, 0.47F, 0.48F, 0.52F, 0.58F, 0.32F, 0.0F};
        private static final int POOL_SIDES = 40;
        private static final int ARMS = 5;
        /** Spiral winding of the pool's streams and foam arms (radians per e-fold of radius) and their turn rate. */
        private static final double ARM_WIND = 1.5;
        private static final float POOL_SPIN = 0.07F;
        private static final int ARM_STEPS = 18;
        private static final double ARM_IN = 0.08;

        private static final int SIDES = 24;
        private static final int STREAMS = 3;
        private static final float[] STREAM = streams(SIDES, STREAMS);
        private static final int FUNNEL = 15;
        /** Height of the neck above the eye: below it the spout flares into a foot drawing up the pool. */
        private static final double NECK = 0.45;
        /** The crown: the funnel's top flares out and curls over like the wave's lip (radius, height past the top). */
        private static final double[][] CROWN = {{0.3, 0.22}, {0.7, 0.32}, {1.0, 0.18}, {1.12, -0.12}};
        private static final float[] CROWN_T = {0.58F, 0.75F, 0.9F, 1.0F};
        private static final float[] CROWN_A = {0.55F, 0.64F, 0.6F, 0.3F};
        private static final int TOP = 2 + FUNNEL;
        private static final int SPOUT_ROWS = TOP + 1 + CROWN.length;
        /** The streams wind this far round the funnel from neck to top, and the whole spout spins this fast. */
        private static final double TWIST = 4.5;
        private static final float SPIN = 0.28F;
        private static final int GLINTS = 10;
        private static final double TOP_HEIGHT = 7.0;

        private final Vec3 center;
        private final float radius;
        private final int duration;
        private final Shell pool = new Shell(POOL.length, POOL_SIDES);
        private final Shell spout = new Shell(SPOUT_ROWS, SIDES);
        private final double[] rowR = new double[SPOUT_ROWS];
        private final double[] rowY = new double[SPOUT_ROWS];
        private final double[] rowF = new double[SPOUT_ROWS];
        private int age;

        Maelstrom(FxPayload p) {
            this.center = p.a();
            this.radius = p.p0() > 0.0F ? p.p0() : (float) Balance.SICKLE_MAELSTROM_RADIUS;
            this.duration = p.p1() > 0.0F ? (int) p.p1() : Balance.SICKLE_MAELSTROM_TICKS;
            if (TidecallerFx.isSelf(p)) {
                ScreenFx.shake(1.2F, 10);
            }
        }

        private float envelope(float time) {
            return TidecallerFx.smooth(time / 14.0F) * (1.0F - Mth.clamp((time - duration) / 6.0F, 0.0F, 1.0F));
        }

        /** Height of the pool above the centre at fraction f of the radius: low at the eye, a swell at the rim. */
        private static double bowl(double f, float env) {
            double lift = f <= CREST ? (f / CREST) * (f / CREST) : 1.0 - TidecallerFx.smooth((float) ((f - CREST) / (1.0 - CREST)));
            return 0.04 + 0.4 * lift * env;
        }

        private double spoutHeight(float time, float env) {
            return (1.5 + (TOP_HEIGHT - 1.5) * TidecallerFx.smooth(time / 30.0F)) * env;
        }

        /** Spout radius at fraction f of its height: a narrow neck widening upward like a waterspout. */
        private static double spoutRadius(double f) {
            return 0.42 + 1.9 * Math.pow(f, 1.6);
        }

        /** The spout's centre line at fraction f (relative to the eye), swaying like a twister. */
        private static Vec3 sway(double f, float time) {
            return new Vec3(Math.sin(time * 0.11 + f * 2.6) * 0.45 * f, 0.0, Math.cos(time * 0.09 + f * 2.1) * 0.45 * f);
        }

        @Override
        public boolean tick() {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level != null && age < duration) {
                particles(level, level.random);
                // anyone caught inside sees the water close around them
                Player self = mc.player;
                if (self != null && self.distanceToSqr(center.x, self.getY(), center.z) < radius * radius
                        && Math.abs(self.getY() - center.y) < 6.0) {
                    ScreenFx.hold("tide_maelstrom", 0x0F4F73, 0.45F * envelope(age), true);
                }
            }
            return ++age < duration + 6;
        }

        private void particles(ClientLevel level, RandomSource r) {
            float env = envelope(age);
            double eye = center.y + bowl(0.0, env);
            double h = spoutHeight(age, env);
            // bubbles whirling up the spout on two helices, splashes flung off its wall
            for (int j = 0; j < 2; j++) {
                double f = (age * 0.045 + j * 0.5) % 1.0;
                double a = age * 0.55 + j * Math.PI;
                Vec3 s = sway(f, age);
                double rad = spoutRadius(f) * 0.85;
                double x = center.x + s.x + Math.cos(a) * rad;
                double y = eye + f * h;
                double z = center.z + s.z + Math.sin(a) * rad;
                level.addParticle(rising(level, x, y, z), x, y, z, 0.0, 0.04, 0.0);
                double ta = a + r.nextDouble() * Mth.TWO_PI;
                double tf = r.nextDouble();
                Vec3 ts = sway(tf, age);
                double tr = spoutRadius(tf);
                level.addParticle(ParticleTypes.SPLASH, center.x + ts.x + Math.cos(ta) * tr, eye + tf * h, center.z + ts.z + Math.sin(ta) * tr,
                        -Math.sin(ta) * 0.3, 0.0, Math.cos(ta) * 0.3);
            }
            // water raining off the crown
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                Vec3 s = sway(1.0, age);
                double rad = spoutRadius(1.0) + 0.3 + r.nextDouble() * 0.6;
                level.addParticle(ParticleTypes.FALLING_WATER, center.x + s.x + Math.cos(a) * rad, eye + h,
                        center.z + s.z + Math.sin(a) * rad, 0.0, 0.0, 0.0);
            }
            // the pool: splashes and wakes circling inward (counter-clockwise from above, like the server pull)
            for (int i = 0; i < 4; i++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double rad = radius * (0.2 + 0.8 * r.nextDouble());
                double x = center.x + Math.cos(a) * rad;
                double z = center.z + Math.sin(a) * rad;
                double y = center.y + bowl(rad / radius, env) + 0.05;
                double spin = 0.16 + 0.2 * (1.0 - rad / radius);
                double vx = -Math.sin(a) * spin - Math.cos(a) * 0.06;
                double vz = Math.cos(a) * spin - Math.sin(a) * 0.06;
                level.addParticle(i < 2 ? ParticleTypes.SPLASH : ParticleTypes.FISHING, x, y, z, vx, i < 2 ? 0.0 : 0.01, vz);
            }
            if ((age & 1) == 0) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double rad = r.nextDouble() * 1.2;
                double x = center.x + Math.cos(a) * rad;
                double z = center.z + Math.sin(a) * rad;
                level.addParticle(rising(level, x, center.y - 0.3, z), x, center.y + 0.1, z, 0.0, 0.06, 0.0);
            }
            if (age % 3 == 0) {
                Vec3 s = sway(1.0, age);
                level.addParticle(ParticleTypes.CLOUD, center.x + s.x + (r.nextDouble() - 0.5) * 2.0, eye + h + 0.2,
                        center.z + s.z + (r.nextDouble() - 0.5) * 2.0, 0.0, 0.02, 0.0);
            }
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
            buildPool(c, time, env);
            double h = spoutHeight(time, env);
            boolean hasSpout = h >= 0.1;
            if (hasSpout) {
                buildSpout(c, time, env, h);
            }
            // one batch of water (sorted back to front with the wave's), then one of foam and glints
            VertexConsumer water = ctx.buffers().getBuffer(water());
            pool.fill(water, pose, 0, POOL.length - 1);
            if (hasSpout) {
                spout.fill(water, pose, 0, SPOUT_ROWS - 1);
            }
            VertexConsumer light = ctx.buffers().getBuffer(light());
            poolFoam(light, pose, c, time, env);
            if (hasSpout) {
                spoutFoam(light, pose, c, time, env, h);
            }
        }

        /** The whirlpool: a bowl darkening into the eye, with lighter spiral streams turning inward. */
        private void buildPool(Vec3 c, float time, float env) {
            for (int j = 0; j < POOL.length; j++) {
                double f = POOL[j];
                double r = f * radius;
                double y = c.y + bowl(f, env);
                int j0 = Math.max(0, j - 1);
                int j1 = Math.min(POOL.length - 1, j + 1);
                double dr = (POOL[j1] - POOL[j0]) * radius;
                double dy = bowl(POOL[j1], env) - bowl(POOL[j0], env);
                double len = Math.sqrt(dr * dr + dy * dy);
                double nr = -dy / len;
                double nu = dr / len;
                float a = POOL_A[j] * env;
                // log-spiral streams: turning the pattern makes them run in towards the eye
                double phase = ARM_WIND * Math.log(f) + time * POOL_SPIN;
                for (int i = 0; i < POOL_SIDES; i++) {
                    double cos = pool.cos[i];
                    double sin = pool.sin[i];
                    double x = c.x + cos * r;
                    double z = c.z + sin * r;
                    float arm = (float) (0.5 + 0.5 * Math.cos(ARMS * (Mth.TWO_PI * i / POOL_SIDES - phase)));
                    arm = arm * arm * arm;
                    pool.set(j, i, x, y, z, tone(POOL_T[j] + 0.18F * arm, a * (1.0F + 0.2F * arm), edge(x, y, z, cos * nr, nu, sin * nr)));
                }
            }
        }

        /** The wave's crest on the swell at the rim, its foam foot, and foam arms riding the streams into the eye. */
        private void poolFoam(VertexConsumer light, PoseStack.Pose pose, Vec3 c, float time, float env) {
            pool.crest(light, pose, CREST_ROW, 1.0F, FxDraw.argb(0.22F * env, CYAN));
            pool.crest(light, pose, CREST_ROW, 0.42F, FxDraw.argb(0.7F * env, FOAM));
            pool.crest(light, pose, POOL.length - 1, 0.55F, FxDraw.argb(0.4F * env, FOAM));
            for (int k = 0; k < ARMS; k++) {
                double base = Mth.TWO_PI * k / ARMS + time * POOL_SPIN;
                double px = 0.0;
                double py = 0.0;
                double pz = 0.0;
                float pw = 0.0F;
                int pc = 0;
                for (int q = 0; q <= ARM_STEPS; q++) {
                    double f = ARM_IN * Math.pow(CREST / ARM_IN, (double) q / ARM_STEPS);
                    double ang = base + ARM_WIND * Math.log(f);
                    double r = f * radius;
                    double x = c.x + Math.cos(ang) * r;
                    double y = c.y + bowl(f, env) + 0.03;
                    double z = c.z + Math.sin(ang) * r;
                    float s = (float) (f / CREST);
                    float w = 0.12F + 0.4F * s;
                    int col = FxDraw.argb(0.6F * env * Mth.sqrt(Mth.sin(s * Mth.PI)), FOAM);
                    if (q > 0) {
                        flat(light, pose, px, py, pz, x, y, z, pw, w, pc, col);
                    }
                    px = x;
                    py = y;
                    pz = z;
                    pw = w;
                    pc = col;
                }
            }
        }

        /** The waterspout: foot, neck, a swaying funnel widening upward and the curling crown, one shell. */
        private void buildSpout(Vec3 c, float time, float env, double h) {
            double k = 0.6 + 0.4 * env;
            double eye = bowl(0.0, env);
            rowR[0] = 1.7 * k;
            rowY[0] = 0.0;
            rowF[0] = 0.0;
            rowR[1] = 0.9 * k;
            rowY[1] = 0.22;
            rowF[1] = 0.0;
            for (int j = 0; j <= FUNNEL; j++) {
                double f = (double) j / FUNNEL;
                rowR[2 + j] = spoutRadius(f) * k;
                rowY[2 + j] = NECK + f * (h - NECK);
                rowF[2 + j] = f;
            }
            for (int j = 0; j < CROWN.length; j++) {
                rowR[TOP + 1 + j] = rowR[TOP] + CROWN[j][0] * k;
                rowY[TOP + 1 + j] = rowY[TOP] + CROWN[j][1] * k;
                rowF[TOP + 1 + j] = 1.0;
            }
            for (int j = 0; j < SPOUT_ROWS; j++) {
                int j0 = Math.max(0, j - 1);
                int j1 = Math.min(SPOUT_ROWS - 1, j + 1);
                float t;
                float a;
                float w;
                if (j == 0) {
                    t = 0.62F;
                    a = 0.1F;
                    w = 0.5F;
                } else if (j == 1) {
                    t = 0.48F;
                    a = 0.46F;
                    w = 0.5F;
                } else if (j <= TOP) {
                    t = 0.24F + 0.2F * (float) rowF[j];
                    a = 0.36F;
                    w = 1.0F;
                } else {
                    t = CROWN_T[j - TOP - 1];
                    a = CROWN_A[j - TOP - 1];
                    w = 0.4F;
                }
                Vec3 s = sway(rowF[j], time);
                ring(spout, j, c.x + s.x, c.y + eye + rowY[j], c.z + s.z, rowR[j], time * SPIN - rowF[j] * TWIST,
                        rowR[j1] - rowR[j0], rowY[j1] - rowY[j0], t, a * env, w, STREAM);
            }
        }

        /** The wave's crest along the crown, a foam foot round the neck and glints streaming up the streams. */
        private void spoutFoam(VertexConsumer light, PoseStack.Pose pose, Vec3 c, float time, float env, double h) {
            spout.crest(light, pose, TOP + 2, 1.0F, FxDraw.argb(0.22F * env, CYAN));
            spout.crest(light, pose, TOP + 3, 0.42F, FxDraw.argb(0.75F * env, FOAM));
            spout.crest(light, pose, TOP + 4, 0.22F, FxDraw.argb(0.5F * env, FOAM));
            spout.crest(light, pose, 1, 0.55F, FxDraw.argb(0.45F * env, FOAM));
            double k = 0.6 + 0.4 * env;
            double eye = c.y + bowl(0.0, env);
            for (int n = 0; n < GLINTS; n++) {
                float q = (time * 0.045F + n * 0.618F) % 1.0F;
                double column = Mth.TWO_PI * (n % STREAMS) / STREAMS + (n / STREAMS) * 0.17;
                float bright = 0.5F * Mth.sin(q * Mth.PI) * env;
                Vec3 a = funnel(c, eye, h, k, q, column, time);
                Vec3 m = funnel(c, eye, h, k, Math.min(1.0F, q + 0.08F), column, time);
                Vec3 b = funnel(c, eye, h, k, Math.min(1.0F, q + 0.16F), column, time);
                FxDraw.ribbon(pose, light, a, m, 0.12F, 0.1F, FxDraw.argb(0.0F, GLINT), FxDraw.argb(0.5F * bright, GLINT), 0.0F, 1.0F);
                FxDraw.ribbon(pose, light, m, b, 0.1F, 0.08F, FxDraw.argb(0.5F * bright, GLINT), FxDraw.argb(bright, GLINT), 0.0F, 1.0F);
            }
        }

        /** A point just outside the funnel at fraction f of its height, on the stream that starts at angle {@code column}. */
        private static Vec3 funnel(Vec3 c, double eye, double h, double k, double f, double column, float time) {
            Vec3 s = sway(f, time);
            double r = spoutRadius(f) * k * 1.05;
            double ang = column + time * SPIN - f * TWIST;
            return new Vec3(c.x + s.x + Math.cos(ang) * r, eye + NECK + f * (h - NECK), c.z + s.z + Math.sin(ang) * r);
        }
    }

    // ------------------------------------------------------------------ the geyser

    static final class Geyser implements ClientFx {
        private static final int LIFE = 32;
        private static final int RISE = 5;
        private static final int HOLD = 14;
        private static final int SIDES = 24;
        private static final float[] STREAM = streams(SIDES, 4);
        private static final float[] FALL = streams(SIDES, 6);
        private static final int COLUMN = 12;
        /** The crown bursting open at the top, curling over like the wave's lip (radius, height past the top, x open). */
        private static final double[][] CROWN = {{0.55, 0.45}, {1.3, 0.65}, {1.9, 0.35}, {2.15, -0.25}};
        private static final float[] CROWN_T = {0.58F, 0.75F, 0.9F, 1.0F};
        private static final float[] CROWN_A = {0.55F, 0.64F, 0.6F, 0.3F};
        private static final int TOP = COLUMN;
        private static final int ROWS = TOP + 1 + CROWN.length;
        private static final int CURTAIN = 6;
        private static final int GLINTS = 10;
        private static final int STREAKS = 8;
        private final Vec3 center;
        private final float radius;
        private final float height;
        private final Shell column = new Shell(ROWS, SIDES);
        private final Shell curtain = new Shell(CURTAIN, SIDES);
        private final Shell skirt = new Shell(2, SIDES);
        private final Shell sheen = new Shell(2, 32);
        private final double[] rowR = new double[ROWS];
        private final double[] rowY = new double[ROWS];
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
                        level.addParticle(rising(level, x, center.y - 0.2, z), x, center.y + 0.3, z, 0.0, 0.15, 0.0);
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

        /** Column radius at fraction f of its height: a little narrower at the top, wobbling as it roars. */
        private static double columnRadius(double f, float time) {
            return 1.15 - 0.25 * f + 0.05 * Mth.sin(time * 1.3F + (float) f * 6.0F);
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
                double bx = center.x + (r.nextDouble() - 0.5);
                double bz = center.z + (r.nextDouble() - 0.5);
                level.addParticle(rising(level, bx, center.y + 0.5, bz), bx, center.y + 0.5 + r.nextDouble() * h * 0.5, bz, 0.0, 0.1, 0.0);
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
            Vec3 c = ctx.rel(center).add(0.0, 0.03, 0.0);
            float open = Mth.clamp((time - RISE + 1.0F) / 6.0F, 0.0F, 1.0F);
            float burst = Mth.clamp(time / 8.0F, 0.0F, 1.0F);
            float spread = Mth.clamp(time / 12.0F, 0.0F, 1.0F);
            buildColumn(c, time, h, fade, open);
            boolean falling = open > 0.0F;
            if (falling) {
                buildCurtain(c, time, h, fade, open);
            }
            // a skirt of water bursting out at the foot
            double skirtTop = 0.3 + 0.9 * (1.0F - burst);
            double skirtOut = 1.6 + 2.4 * burst;
            ring(skirt, 0, c.x, c.y, c.z, 1.2, -time * 0.3, skirtOut - 1.2, skirtTop, 0.45F, 0.5F * fade * (1.0F - 0.5F * burst), 0.5F,
                    STREAM);
            ring(skirt, 1, c.x, c.y + skirtTop, c.z, skirtOut, -time * 0.3, skirtOut - 1.2, skirtTop, 0.7F, 0.0F, 0.5F, STREAM);
            // a foam line racing out over the ground, leaving a wet sheen that dries (the wave's foot and wake)
            double front = 1.3 + (radius - 0.5) * spread;
            float wet = 0.24F * (1.0F - spread) * fade;
            ring(sheen, 0, c.x, c.y + 0.01, c.z, 1.1, 0.0, front - 1.1, 0.0, 0.25F, wet, 0.0F, null);
            ring(sheen, 1, c.x, c.y + 0.01, c.z, front, 0.0, front - 1.1, 0.0, 0.25F, wet, 0.0F, null);

            VertexConsumer water = ctx.buffers().getBuffer(water());
            sheen.fill(water, pose, 0, 1);
            skirt.fill(water, pose, 0, 1);
            column.fill(water, pose, 0, ROWS - 1);
            if (falling) {
                curtain.fill(water, pose, 0, CURTAIN - 1);
            }

            VertexConsumer light = ctx.buffers().getBuffer(light());
            column.crest(light, pose, TOP + 2, 1.0F, FxDraw.argb(0.22F * fade, CYAN));
            column.crest(light, pose, TOP + 3, 0.42F, FxDraw.argb(0.75F * fade, FOAM));
            column.crest(light, pose, TOP + 4, 0.22F, FxDraw.argb(0.5F * fade, FOAM));
            column.crest(light, pose, 0, 0.55F, FxDraw.argb(0.45F * fade, FOAM));
            sheen.crest(light, pose, 1, 0.55F, FxDraw.argb(0.45F * (1.0F - spread) * fade, FOAM));
            // glints rushing up the column on its streams
            for (int n = 0; n < GLINTS; n++) {
                float q = (time * 0.09F + n * 0.618F) % 1.0F;
                double col = Mth.TWO_PI * n / GLINTS;
                float bright = 0.5F * Mth.sin(q * Mth.PI) * fade;
                Vec3 a = columnPoint(c, h, q, col, time);
                Vec3 b = columnPoint(c, h, Math.min(1.0F, q + 0.16F), col, time);
                FxDraw.ribbon(pose, light, a, b, 0.12F, 0.08F, FxDraw.argb(0.0F, GLINT), FxDraw.argb(bright, GLINT), 0.0F, 1.0F);
            }
            if (falling) {
                // foam streaking down the falling curtain
                for (int n = 0; n < STREAKS; n++) {
                    float g = (time * 0.08F + n * 0.618F) % 1.0F;
                    double col = Mth.TWO_PI * n / STREAKS + 0.2;
                    float bright = 0.4F * Mth.sin(g * Mth.PI) * fade * open;
                    Vec3 a = curtainPoint(c, h, open, Math.max(0.0F, g - 0.2F), col, time);
                    Vec3 b = curtainPoint(c, h, open, g, col, time);
                    FxDraw.ribbon(pose, light, a, b, 0.09F, 0.14F, FxDraw.argb(0.0F, FOAM), FxDraw.argb(bright, FOAM), 0.0F, 1.0F);
                }
            }
        }

        /** The column and its crown, one shell; lighter bands rush up it. */
        private void buildColumn(Vec3 c, float time, float h, float fade, float open) {
            for (int j = 0; j <= TOP; j++) {
                double f = (double) j / COLUMN;
                rowR[j] = columnRadius(f, time);
                rowY[j] = f * h;
            }
            for (int j = 0; j < CROWN.length; j++) {
                rowR[TOP + 1 + j] = rowR[TOP] + CROWN[j][0] * open;
                rowY[TOP + 1 + j] = h + CROWN[j][1] * open;
            }
            for (int j = 0; j < ROWS; j++) {
                int j0 = Math.max(0, j - 1);
                int j1 = Math.min(ROWS - 1, j + 1);
                float f = (float) Math.min(1.0, (double) j / COLUMN);
                float t;
                float a;
                float w;
                if (j <= TOP) {
                    float pulse = 0.5F + 0.5F * Mth.sin((float) rowY[j] * 1.4F - time * 1.5F);
                    t = 0.28F + 0.2F * f + 0.1F * pulse;
                    a = 0.4F + 0.05F * pulse;
                    w = 1.0F;
                } else {
                    t = CROWN_T[j - TOP - 1];
                    a = CROWN_A[j - TOP - 1];
                    w = 0.4F;
                }
                ring(column, j, c.x, c.y + rowY[j], c.z, rowR[j], time * 0.5 - f * 2.0, rowR[j1] - rowR[j0], rowY[j1] - rowY[j0],
                        t, a * fade, w, STREAM);
            }
        }

        /** Water falling back from the crown's lip all round the column, thinning out as it falls. */
        private void buildCurtain(Vec3 c, float time, float h, float fade, float open) {
            double top = h + CROWN[CROWN.length - 1][1] * open;
            double bottom = h * 0.25;
            for (int j = 0; j < CURTAIN; j++) {
                float g = (float) j / (CURTAIN - 1);
                double y = Mth.lerp(g, top, bottom);
                float pulse = 0.5F + 0.5F * Mth.sin((float) y * 1.8F + time * 1.3F);
                ring(curtain, j, c.x, c.y + y, c.z, curtainRadius(time, open, g), -time * 0.05, 0.35, bottom - top,
                        0.72F - 0.12F * g, 0.4F * (1.0F - g) * fade * open * (0.85F + 0.3F * pulse), 0.6F, FALL);
            }
        }

        private double curtainRadius(float time, float open, float g) {
            return columnRadius(1.0, time) + CROWN[CROWN.length - 1][0] * open + 0.35 * g;
        }

        private static Vec3 columnPoint(Vec3 c, float h, double f, double col, float time) {
            double r = columnRadius(f, time) * 1.05;
            double ang = col + time * 0.5 - f * 2.0;
            return new Vec3(c.x + Math.cos(ang) * r, c.y + f * h, c.z + Math.sin(ang) * r);
        }

        private Vec3 curtainPoint(Vec3 c, float h, float open, float g, double col, float time) {
            double top = h + CROWN[CROWN.length - 1][1] * open;
            double r = curtainRadius(time, open, g) * 1.03;
            double ang = col - time * 0.05;
            return new Vec3(c.x + Math.cos(ang) * r, c.y + Mth.lerp(g, top, h * 0.25), c.z + Math.sin(ang) * r);
        }
    }
}
