package com.lacuna.mod.client.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.gear.TideCurrents;
import com.lacuna.mod.network.FxPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Tidecaller's Maelstrom (whirlpool + waterspout) and its closing geyser, in the colour of the local water
 * ({@link TideWater}) and drawn with the {@code tide_vortex} shader ({@link TideRenderTypes}): one depth-sorted batch
 * per effect (plus its Fabulous depth twin), every surface a {@link TideMesh} updated in place once per frame.
 * <ul>
 *   <li>{@code tide_maelstrom}: a dark storm canopy gathers over the pool and a funnel ropes down from it to touch the
 *       water. The funnel is not a cone: its axis bends, leans with the drifting cloud and snakes with a kink that
 *       travels up it; its waist is narrow, its foot flares into the water and its top flares into the cloud; its
 *       cross-section is lobed and twists. Two layers - a dense fast core with helical foam streaks rushing up (faster
 *       near the top) and a slower torn spray sheath - and a fresnel rim make it read round. At its foot a bushy spray
 *       cascade boils out over a whirlpool whose arms and foam flecks spiral into a dark eye under a raised foam swell;
 *       mist puffs churn round the base and the canopy. Pixel droplets ride the funnel up and are flung off, spray is
 *       thrown from the cascade, water and bits of the bed (vanilla block particles) are torn up and rain drips from
 *       the cloud. At the end the funnel thins into a writhing rope, lifts off the water and the cloud dissolves.</li>
 *   <li>{@code tide_geyser}: the whirlpool bursts into a column of water blasting up (the same shader), a torn crown
 *       curling over into a falling curtain, a spray skirt and a foam ring racing out, with a burst of droplets.</li>
 * </ul>
 */
final class TideSpout {
    private TideSpout() {}

    // ------------------------------------------------------------------ helpers

    private static float smooth(double t) {
        return TidecallerFx.smooth((float) t);
    }

    private static float hash(int x, int y) {
        int h = x * 0x27D4EB2D ^ y * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0F;
    }

    /** Value noise 0..1, periodic in x every {@code period} cells. */
    private static float noise(double x, double y, int period) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = (float) (x - ix);
        float fy = (float) (y - iy);
        float wx = fx * fx * (3.0F - 2.0F * fx);
        float wy = fy * fy * (3.0F - 2.0F * fy);
        int x0 = Math.floorMod(ix, period);
        int x1 = Math.floorMod(ix + 1, period);
        return Mth.lerp(wy, Mth.lerp(wx, hash(x0, iy), hash(x1, iy)), Mth.lerp(wx, hash(x0, iy + 1), hash(x1, iy + 1)));
    }

    private static boolean wet(ClientLevel level, double x, double y, double z) {
        return level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
    }

    /** Rising bubbles: a real bubble column particle under water, a popping bubble in the open air. */
    private static ParticleOptions rising(ClientLevel level, double x, double y, double z) {
        return wet(level, x, y, z) ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.BUBBLE_POP;
    }

    /** The solid block under the water (the bed) or the ground at the effect, for the torn-up debris; null if none. */
    @Nullable
    private static BlockParticleOption bed(@Nullable ClientLevel level, Vec3 c) {
        if (level == null) {
            return null;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(c.x), Mth.floor(c.y + 0.4), Mth.floor(c.z));
        for (int i = 0; i < 14; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(level, m).isEmpty()) {
                return new BlockParticleOption(ParticleTypes.BLOCK, s);
            }
            m.move(Direction.DOWN);
        }
        return null;
    }

    /** Free height above the effect (centre and four points around it), up to {@code max}. */
    private static double headroom(@Nullable ClientLevel level, Vec3 c, double max) {
        if (level == null) {
            return max;
        }
        double best = max;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k < 5; k++) {
            int dx = k == 1 ? 2 : k == 2 ? -2 : 0;
            int dz = k == 3 ? 2 : k == 4 ? -2 : 0;
            m.set(Mth.floor(c.x) + dx, Mth.floor(c.y) + 1, Mth.floor(c.z) + dz);
            for (int dy = 1; dy <= max + 1; dy++) {
                if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
                    best = Math.min(best, m.getY() - c.y);
                    break;
                }
                m.move(Direction.UP);
            }
        }
        return best;
    }

    private static double dist(Vec3 a) {
        Player p = Minecraft.getInstance().player;
        return p == null ? 1.0E9 : p.position().distanceTo(a);
    }

    // ------------------------------------------------------------------ the maelstrom

    static final class Maelstrom implements ClientFx {
        private static final int SIDES = 28;
        private static final int CORE_ROWS = 34;
        private static final int SHEATH_ROWS = 24;
        private static final int CANOPY_LOW = 8;
        private static final int CANOPY_HIGH = 6;
        private static final int CANOPY_SIDES = 40;
        /** Pool rings (fractions of the radius) and their density: a dark dense eye, the swell (CREST) and its foot. */
        private static final float[] POOL = {0.02F, 0.06F, 0.11F, 0.17F, 0.24F, 0.32F, 0.41F, 0.51F, 0.62F, 0.73F, 0.82F, 0.88F,
                0.94F, 1.0F};
        private static final float[] POOL_A = {0.9F, 0.84F, 0.76F, 0.68F, 0.62F, 0.58F, 0.55F, 0.55F, 0.58F, 0.62F, 0.72F, 0.68F,
                0.4F, 0.0F};
        private static final float CREST = 0.86F;
        private static final int POOL_SIDES = 48;
        private static final int SKIRT_ROWS = 6;
        private static final int SKIRT_SIDES = 32;
        private static final int BASE_PUFFS = 10;
        private static final int TOP_PUFFS = 14;
        private static final int CLIMBERS = 48;
        private static final double NECK = 0.4;
        private static final double MAX_HEIGHT = 10.5;
        private static final double CANOPY_THICKNESS = 2.3;

        private final Vec3 center;
        private final float radius;
        private final int duration;
        private final double height;
        /** Size of the funnel and its spray relative to a full-height spout (low ceilings make a smaller one). */
        private final double scale;
        private final double canopyR;
        private final float seedA;
        private final float seedB;
        private final TideWater water;
        @Nullable
        private final BlockParticleOption bedBits;
        private final TideMesh core = new TideMesh(CORE_ROWS, SIDES, TideMesh.CORE);
        private final TideMesh sheath = new TideMesh(SHEATH_ROWS, SIDES, TideMesh.SHEATH);
        private final TideMesh canopy = new TideMesh(CANOPY_LOW + CANOPY_HIGH, CANOPY_SIDES, TideMesh.CANOPY);
        private final TideMesh pool = new TideMesh(POOL.length, POOL_SIDES, TideMesh.POOL);
        private final TideMesh skirt = new TideMesh(SKIRT_ROWS, SKIRT_SIDES, TideMesh.SKIRT);
        private final TideMesh spray = new TideMesh(SKIRT_ROWS, SKIRT_SIDES, TideMesh.SKIRT);
        private final TideMesh.Emitter emitter = new TideMesh.Emitter();
        private final TideDroplets drops;
        private final float[] canopyRho = new float[CANOPY_LOW + CANOPY_HIGH];
        private final float[] canopyY = new float[CANOPY_LOW + CANOPY_HIGH];
        // droplets riding up the funnel (relative to the centre): height fraction, angle, spin, rise, radius factor, release
        private final float[] cf = new float[CLIMBERS];
        private final float[] ca = new float[CLIMBERS];
        private final float[] cw = new float[CLIMBERS];
        private final float[] cv = new float[CLIMBERS];
        private final float[] ck = new float[CLIMBERS];
        private final float[] cr = new float[CLIMBERS];
        private final float[] csz = new float[CLIMBERS];
        private final int[] cslot = new int[CLIMBERS];
        private final float[] cx = new float[CLIMBERS];
        private final float[] cy = new float[CLIMBERS];
        private final float[] cz = new float[CLIMBERS];
        private final float[] cpx = new float[CLIMBERS];
        private final float[] cpy = new float[CLIMBERS];
        private final float[] cpz = new float[CLIMBERS];
        private int climbers;
        private int age;
        private boolean touched;
        private float builtTime = Float.NaN;
        private double builtX;
        private double builtY;
        private double builtZ;
        // spine sample (set by spine())
        private double sox;
        private double soz;

        Maelstrom(FxPayload p) {
            this.center = p.a();
            this.radius = p.p0() > 0.0F ? p.p0() : (float) Balance.SICKLE_MAELSTROM_RADIUS;
            this.duration = p.p1() > 0.0F ? (int) p.p1() : Balance.SICKLE_MAELSTROM_TICKS;
            ClientLevel level = Minecraft.getInstance().level;
            this.height = Mth.clamp(headroom(level, center, MAX_HEIGHT + CANOPY_THICKNESS) - CANOPY_THICKNESS, 3.0, MAX_HEIGHT);
            this.scale = 0.55 + 0.45 * (height - 3.0) / (MAX_HEIGHT - 3.0);
            this.canopyR = Math.min(6.4, radius * 0.92) * (0.5 + 0.5 * scale);
            this.water = TideWater.at(level, center);
            this.bedBits = bed(level, center);
            this.drops = new TideDroplets(200, center, center.y);
            int seed = Mth.floor(center.x * 31.0 + center.z * 17.0);
            this.seedA = hash(seed, 3) * Mth.TWO_PI;
            this.seedB = hash(seed, 7) * Mth.TWO_PI;
            if (TidecallerFx.isSelf(p)) {
                ScreenFx.shake(1.2F, 10);
            }
        }

        // ---------------------------------------------------------------- the life of the spout

        /** The storm cloud gathering (0..1); it dissolves after the end. */
        private float gather(float t) {
            return smooth(t / 12.0) * (1.0F - dissolve(t));
        }

        private float dissolve(float t) {
            return smooth((t - duration) / 18.0);
        }

        /** Height fraction of the funnel's tip while it ropes down from the cloud: 1 at the cloud .. 0 touching down. */
        private float tip(float t) {
            return 1.0F - smooth((t - 4.0) / 21.0);
        }

        /** The spray at the foot: starts as the tip nears the water. */
        private float touch(float t) {
            return smooth((t - 15.0) / 12.0);
        }

        /** The rope-out: the funnel thins and writhes over its last ticks. */
        private float rope(float t) {
            return smooth((t - (duration - 16.0)) / 16.0);
        }

        /** After the end the rope's foot lifts off the water back into the cloud. */
        private float lift(float t) {
            return smooth((t - duration) / 9.0);
        }

        private float poolEnv(float t) {
            return smooth(t / 14.0) * (1.0F - smooth((t - duration) / 6.0));
        }

        /** Horizontal offset of the funnel's axis at height fraction f (sets sox, soz). */
        private void spine(double f, float t, float rope) {
            double lean = (0.85 + 0.6 * rope) * scale;
            double lx = Math.cos(t * 0.021 + seedA) * lean;
            double lz = Math.sin(t * 0.017 + seedA * 1.7) * lean;
            double wx = 0.25 * Math.sin(t * 0.05 + seedB);
            double wz = 0.25 * Math.cos(t * 0.043 + seedB);
            // a helical kink travelling up the column
            double amp = (0.3 + 0.95 * rope) * scale * Math.sin(Math.PI * f);
            double k = Mth.TWO_PI * (1.1 + 0.5 * rope) * f - t * 0.11;
            double b = (1.0 - f) * (1.0 - f);
            sox = wx * b + lx * f * f + amp * Math.cos(k);
            soz = wz * b + lz * f * f + amp * Math.sin(k);
        }

        /** Funnel radius at height fraction f: a flared foot, a narrow waist, widening up into a flare under the cloud. */
        private double coreRadius(double f, float t, float touch, float rope) {
            double top = smooth((f - 0.78) / 0.22);
            double r = NECK + 1.25 * Math.pow(Math.max(0.0, f), 1.7) + 1.8 * top * top;
            r += 0.8 * (1.0F - smooth(f / 0.08)) * touch;
            r *= 1.0 + 0.07 * Math.sin(f * height * 1.25 - t * 0.42);
            r *= Mth.lerp(rope, 1.0, 0.22 + 0.55 * top * top);
            return r * scale;
        }

        /** Height of the whirlpool above the water at fraction f of its radius: a low eye rising to a swell. */
        private static double bowl(double f, float env) {
            double lift = f <= CREST ? (f / CREST) * (f / CREST) : 1.0 - TidecallerFx.smooth((float) ((f - CREST) / (1.0 - CREST)));
            return 0.04 + 0.38 * lift * env;
        }

        // ---------------------------------------------------------------- ticking: particles and droplets

        @Override
        public boolean tick() {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            drops.tick();
            tickClimbers();
            if (level != null) {
                RandomSource r = level.random;
                if (age < duration) {
                    particles(level, r);
                    // anyone caught inside sees the water close around them
                    Player self = mc.player;
                    if (self != null && self.distanceToSqr(center.x, self.getY(), center.z) < radius * radius
                            && Math.abs(self.getY() - center.y) < 6.0) {
                        ScreenFx.hold("tide_maelstrom", water.raw(TideWater.DEEP), 0.45F * poolEnv(age), true);
                    }
                }
                if (!touched && tip(age) < 0.04F) {
                    touched = true;
                    touchdown(level, r);
                }
            }
            return ++age < duration + 20;
        }

        /** The funnel's foot hits the water: a ring of spray and torn-up water and bed. */
        private void touchdown(ClientLevel level, RandomSource r) {
            double d = dist(center);
            if (d < 24.0) {
                ScreenFx.shake((float) (1.4 * (1.0 - d / 24.0)), 8);
            }
            for (int i = 0; i < 46; i++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double rad = (0.6 + r.nextDouble() * 1.2) * scale;
                double out = 0.18 + r.nextDouble() * 0.3;
                drops.add(center.x + Math.cos(a) * rad, center.y + 0.2, center.z + Math.sin(a) * rad, Math.cos(a) * out - Math.sin(a) * 0.15,
                        0.35 + r.nextDouble() * 0.45, Math.sin(a) * out + Math.cos(a) * 0.15, 0.05F + r.nextFloat() * 0.06F,
                        30 + r.nextInt(16), TideDroplets.spraySlot(r));
                if (i % 3 == 0) {
                    water.splash(center.x + Math.cos(a) * rad, center.y + 0.3, center.z + Math.sin(a) * rad, Math.cos(a) * out, 0.3 + r.nextDouble() * 0.3,
                            Math.sin(a) * out, TideWater.LIGHT);
                }
                if (i % 5 == 0 && bedBits != null) {
                    TideWater.spawn(bedBits, center.x + Math.cos(a) * rad * 0.6, center.y + 0.2, center.z + Math.sin(a) * rad * 0.6,
                            0.0, 0.0, 0.0);
                }
            }
        }

        private void particles(ClientLevel level, RandomSource r) {
            float env = poolEnv(age);
            float touch = touch(age);
            float rope = rope(age);
            double eye = center.y + bowl(0.0, env);
            spine(0.0, age, rope);
            double bx = center.x + sox;
            double bz = center.z + soz;
            if (touch > 0.3F) {
                // spray thrown off the cascade
                for (int i = 0; i < 3; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = (0.7 + r.nextDouble() * 1.1) * scale;
                    double out = 0.12 + r.nextDouble() * 0.2;
                    double spin = 0.22 + r.nextDouble() * 0.12;
                    drops.add(bx + Math.cos(a) * rad, eye + 0.1 + r.nextDouble() * 0.8, bz + Math.sin(a) * rad,
                            Math.cos(a) * out - Math.sin(a) * spin, 0.22 + r.nextDouble() * 0.36, Math.sin(a) * out + Math.cos(a) * spin,
                            0.04F + r.nextFloat() * 0.05F, 22 + r.nextInt(14), TideDroplets.spraySlot(r));
                }
                // droplets caught by the funnel and carried up it
                for (int i = 0; i < 2 && climbers < CLIMBERS; i++) {
                    int k = climbers++;
                    cf[k] = 0.02F + r.nextFloat() * 0.1F;
                    ca[k] = r.nextFloat() * Mth.TWO_PI;
                    cw[k] = 0.2F + r.nextFloat() * 0.2F;
                    cv[k] = 0.18F + r.nextFloat() * 0.2F;
                    ck[k] = 1.12F + r.nextFloat() * 0.4F;
                    cr[k] = 0.35F + r.nextFloat() * 0.6F;
                    csz[k] = 0.045F + r.nextFloat() * 0.05F;
                    cslot[k] = TideDroplets.spraySlot(r);
                    climberPos(k, age);
                    cpx[k] = cx[k];
                    cpy[k] = cy[k];
                    cpz[k] = cz[k];
                }
                // a pixel of water torn off the foot, and bits of the bed
                double a = r.nextDouble() * Mth.TWO_PI;
                double rad = (0.8 + r.nextDouble() * 1.4) * scale;
                water.splash(bx + Math.cos(a) * rad, eye + 0.2, bz + Math.sin(a) * rad, -Math.sin(a) * 0.3 + Math.cos(a) * 0.1,
                        0.3 + r.nextDouble() * 0.3, Math.cos(a) * 0.3 + Math.sin(a) * 0.1, r.nextBoolean() ? TideWater.LIGHT : TideWater.PALE);
                if ((age & 1) == 0 && bedBits != null) {
                    double b = r.nextDouble() * Mth.TWO_PI;
                    double br = (0.6 + r.nextDouble() * 1.6) * scale;
                    var bit = TideWater.spawn(bedBits, bx + Math.cos(b) * br, eye + 0.15, bz + Math.sin(b) * br, 0.0, 0.0, 0.0);
                    if (bit != null) {
                        bit.setParticleSpeed(-Math.sin(b) * 0.28 + Math.cos(b) * 0.08, 0.35 + r.nextDouble() * 0.35,
                                Math.cos(b) * 0.28 + Math.sin(b) * 0.08);
                    }
                }
            }
            // rain dripping from the cloud
            float cloud = gather(age);
            if (cloud > 0.4F) {
                spine(1.0, age, rope);
                for (int i = 0; i < 2; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = canopyR * (0.35 + 0.6 * r.nextDouble());
                    water.drip(center.x + sox + Math.cos(a) * rad, eye + height - 0.3, center.z + soz + Math.sin(a) * rad);
                }
            }
            // wakes circling in over the pool (counter-clockwise from above, like the server pull)
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double rad = radius * (0.25 + 0.7 * r.nextDouble());
                double x = center.x + Math.cos(a) * rad;
                double z = center.z + Math.sin(a) * rad;
                double y = center.y + bowl(rad / radius, env) + 0.05;
                double spin = 0.16 + 0.2 * (1.0 - rad / radius);
                water.particle(ParticleTypes.FISHING, x, y, z, -Math.sin(a) * spin - Math.cos(a) * 0.06, 0.01,
                        Math.cos(a) * spin - Math.sin(a) * 0.06, TideWater.PALE);
            }
            if ((age & 1) == 0) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double rad = r.nextDouble() * 1.4;
                double x = center.x + Math.cos(a) * rad;
                double z = center.z + Math.sin(a) * rad;
                level.addParticle(rising(level, x, center.y - 0.3, z), x, center.y - 0.2, z, 0.0, 0.08, 0.0);
            }
        }

        /** Position of climber k (relative to the centre) at time t: on a helix just outside the funnel. */
        private void climberPos(int k, float t) {
            float rope = rope(t);
            spine(cf[k], t, rope);
            double r = coreRadius(cf[k], t, touch(t), rope) * ck[k] + 0.15;
            cx[k] = (float) (sox + Math.cos(ca[k]) * r);
            cy[k] = (float) (bowl(0.0, poolEnv(t)) + cf[k] * height);
            cz[k] = (float) (soz + Math.sin(ca[k]) * r);
        }

        private void tickClimbers() {
            for (int k = 0; k < climbers; ) {
                cpx[k] = cx[k];
                cpy[k] = cy[k];
                cpz[k] = cz[k];
                cf[k] += cv[k] / (float) height;
                ca[k] += cw[k];
                climberPos(k, age + 1);
                boolean gone = age >= duration + 2;
                if (cf[k] >= cr[k] || gone) {
                    // flung off the funnel: keeps its swirl and is thrown outward
                    double vx = cx[k] - cpx[k];
                    double vy = cy[k] - cpy[k];
                    double vz = cz[k] - cpz[k];
                    double ox = Math.cos(ca[k]) * 0.12;
                    double oz = Math.sin(ca[k]) * 0.12;
                    drops.add(center.x + cx[k], center.y + cy[k], center.z + cz[k], vx + ox, vy * 0.6, vz + oz, csz[k], 40, cslot[k]);
                    int last = --climbers;
                    cf[k] = cf[last];
                    ca[k] = ca[last];
                    cw[k] = cw[last];
                    cv[k] = cv[last];
                    ck[k] = ck[last];
                    cr[k] = cr[last];
                    csz[k] = csz[last];
                    cslot[k] = cslot[last];
                    cx[k] = cx[last];
                    cy[k] = cy[last];
                    cz[k] = cz[last];
                    cpx[k] = cpx[last];
                    cpy[k] = cpy[last];
                    cpz[k] = cpz[last];
                } else {
                    k++;
                }
            }
        }

        // ---------------------------------------------------------------- drawing

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            Vec3 cam = ctx.cam();
            Vec3 c = ctx.rel(center);
            if (c.lengthSqr() > 256.0 * 256.0) {
                return;
            }
            // the meshes are rebuilt once per frame (the Fabulous depth pass reuses them)
            if (time != builtTime || cam.x != builtX || cam.y != builtY || cam.z != builtZ) {
                build(c, time);
                builtTime = time;
                builtX = cam.x;
                builtY = cam.y;
                builtZ = cam.z;
            }
            Camera camera = ctx.camera();
            TideMesh.Emitter e = emitter.begin(ctx.buffers().getBuffer(TideRenderTypes.vortex()), ctx.pose().last(),
                    TideRenderTypes.shaded(), camera.getLeftVector(), camera.getUpVector());
            if (poolEnv(time) > 0.0F) {
                pool.emit(e, 0, POOL.length - 1);
            }
            float touch = touch(time);
            if (touch > 0.0F && lift(time) < 1.0F) {
                skirt.emit(e, 0, SKIRT_ROWS - 1);
                spray.emit(e, 0, SKIRT_ROWS - 1);
            }
            if (tip(time) < 0.98F && lift(time) < 0.98F) {
                core.emit(e, 0, CORE_ROWS - 1);
                sheath.emit(e, 0, SHEATH_ROWS - 1);
            }
            if (gather(time) > 0.0F) {
                canopy.emit(e, 0, CANOPY_LOW + CANOPY_HIGH - 1);
            }
            puffs(e, c, time);
            float partial = ctx.partialTick();
            for (int k = 0; k < climbers; k++) {
                float qx = (float) (c.x + Mth.lerp(partial, cpx[k], cx[k]));
                float qy = (float) (c.y + Mth.lerp(partial, cpy[k], cy[k]));
                float qz = (float) (c.z + Mth.lerp(partial, cpz[k], cz[k]));
                e.square(qx, qy, qz, csz[k], FxDraw.argb(0.92F, water.get(cslot[k])), TideMesh.FLAT, 0.0F);
            }
            drops.render(e, cam, partial, water, 1.0F);
        }

        private void build(Vec3 c, float t) {
            float env = poolEnv(t);
            float touch = touch(t);
            float rope = rope(t);
            buildPool(c, t, env);
            spine(0.0, t, rope);
            double eye = bowl(0.0, env);
            buildSkirt(c.x + sox, c.y + eye, c.z + soz, t, touch, rope);
            buildFunnel(c, t, eye, touch, rope);
            buildCanopy(c, t, eye, rope);
        }

        private void buildPool(Vec3 c, float t, float env) {
            int body = water.get(TideWater.BODY);
            for (int j = 0; j < POOL.length; j++) {
                double f = POOL[j];
                double r = f * radius;
                int j0 = Math.max(0, j - 1);
                int j1 = Math.min(POOL.length - 1, j + 1);
                double dr = (POOL[j1] - POOL[j0]) * radius;
                double dy = bowl(POOL[j1], env) - bowl(POOL[j0], env);
                double len = Math.sqrt(dr * dr + dy * dy);
                double nr = -dy / len;
                double nu = dr / len;
                int colour = FxDraw.argb(POOL_A[j] * env, body);
                pool.v(j, (float) (f * 16.0));
                for (int i = 0; i < POOL_SIDES; i++) {
                    double cos = pool.cos[i];
                    double sin = pool.sin[i];
                    double ripple = 0.04 * f * Math.sin(i * Mth.TWO_PI * 3.0 / POOL_SIDES + t * 0.2 + f * 8.0) * env;
                    pool.set(j, i, c.x + cos * r, c.y + bowl(f, env) + ripple, c.z + sin * r, cos * nr, nu, sin * nr, colour);
                }
            }
        }

        /** The spray cascade at the foot: a boiling bell and a lower, wider ring of spray. */
        private void buildSkirt(double bx, double by, double bz, float t, float touch, float rope) {
            float env = touch * (1.0F - lift(t)) * (1.0F - 0.35F * rope);
            int body = water.get(TideWater.BODY);
            double rise = (0.85 + 0.15 * Math.sin(t * 0.27)) * touch * scale;
            for (int j = 0; j < SKIRT_ROWS; j++) {
                double s = (double) j / (SKIRT_ROWS - 1);
                double rho = (0.75 + 2.2 * Math.pow(s, 0.75)) * scale;
                double y = 2.4 * (1.0 - Math.pow(1.0 - s, 2.2)) * (1.0 - 0.3 * s * s * s) * rise;
                double rho2 = (1.5 + 3.0 * s) * scale;
                double y2 = (1.1 * Math.sin(Math.PI * Math.pow(s, 0.8)) + 0.04) * touch * scale;
                int col = FxDraw.argb((float) (0.78 * Math.pow(1.0 - s, 0.6)) * env, body);
                int col2 = FxDraw.argb((float) (0.5 * Math.sin(Math.PI * s)) * env, body);
                skirt.v(j, (float) (s * 8.0));
                spray.v(j, (float) (s * 8.0 + 3.0));
                double ny = j == 0 ? 0.2 : 0.6 - s;
                for (int i = 0; i < SKIRT_SIDES; i++) {
                    double cos = skirt.cos[i];
                    double sin = skirt.sin[i];
                    double wob = 1.0 + 0.07 * Math.sin(i * Mth.TWO_PI * 3.0 / SKIRT_SIDES + t * 0.21 + s * 3.0);
                    skirt.set(j, i, bx + cos * rho * wob, by + y, bz + sin * rho * wob, cos, ny, sin, col);
                    spray.set(j, i, bx + cos * rho2 * wob, by + y2, bz + sin * rho2 * wob, cos * 0.6, 0.8, sin * 0.6, col2);
                }
            }
        }

        /** The funnel's core and sheath along the bending spine. */
        private void buildFunnel(Vec3 c, float t, double eye, float touch, float rope) {
            float ft = tip(t);
            float fb = lift(t);
            double f0 = Math.max(ft, fb);
            float tipness = smooth(f0 / 0.08);
            float fade = 1.0F - smooth((fb - 0.6) / 0.4);
            int body = water.get(TideWater.BODY);
            for (int j = 0; j < CORE_ROWS; j++) {
                double g = (double) j / (CORE_ROWS - 1);
                double f = f0 + (1.0 - f0) * g;
                double taper = Mth.lerp(tipness, 1.0, Math.pow(smooth(g / 0.2), 0.6));
                float a = 0.86F * (1.0F - 0.45F * smooth((f - 0.86) / 0.14)) * fade
                        * Mth.lerp(tipness, 1.0F, smooth(g / 0.12)) * smooth(t / 3.0);
                ring(core, j, c, t, eye, f, coreRadius(f, t, touch, rope) * taper, 1.0, 0.0, FxDraw.argb(a, body), rope, touch);
            }
            double s0 = Math.max(f0, 0.015);
            double s1 = 0.93;
            float sheathEnv = touch * (1.0F - 0.85F * rope) * fade;
            for (int j = 0; j < SHEATH_ROWS; j++) {
                double g = (double) j / (SHEATH_ROWS - 1);
                double f = s0 + (s1 - s0) * g;
                float a = s1 - s0 < 0.05 ? 0.0F : (float) (0.62 * Math.pow(Math.sin(Math.PI * g), 0.7)) * sheathEnv;
                ring(sheath, j, c, t, eye, f, coreRadius(f, t, touch, rope) * 1.3 + 0.35 * scale, 1.0, 2.1, FxDraw.argb(a, body), rope, touch);
            }
        }

        /**
         * Ring {@code row} of a funnel layer at height fraction f: centred on the spine, perpendicular to it, lobed and
         * twisting (lobe phase {@code phase} sets the layer apart).
         */
        private void ring(TideMesh m, int row, Vec3 c, float t, double eye, double f, double r, double lobes, double phase, int colour,
                          float rope, float touch) {
            spine(f, t, rope);
            double ax = sox;
            double az = soz;
            double df = 0.01;
            spine(Math.min(1.0, f + df), t, rope);
            double bx = sox;
            double bz = soz;
            spine(Math.max(0.0, f - df), t, rope);
            double tx = bx - sox;
            double tz = bz - soz;
            double ty = height * (Math.min(1.0, f + df) - Math.max(0.0, f - df));
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tl;
            ty /= tl;
            tz /= tl;
            // frame: e1 = x axis made perpendicular to the tangent, e2 = tangent x e1
            double e1x = 1.0 - tx * tx;
            double e1y = -tx * ty;
            double e1z = -tx * tz;
            double el = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
            e1x /= el;
            e1y /= el;
            e1z /= el;
            double e2x = ty * e1z - tz * e1y;
            double e2y = tz * e1x - tx * e1z;
            double e2z = tx * e1y - ty * e1x;
            // slope of the radius along the axis, for the normal
            double drds = (coreRadius(Math.min(1.0, f + df), t, touch, rope) - coreRadius(Math.max(0.0, f - df), t, touch, rope))
                    / Math.max(1.0E-3, ty * tl);
            double cxr = c.x + ax;
            double cyr = c.y + eye + f * height;
            double czr = c.z + az;
            m.v(row, (float) (f * height));
            double turn1 = t * 0.13 + f * 4.0 + phase;
            double turn2 = -t * 0.09 + f * 7.0 + phase * 1.7;
            for (int i = 0; i < m.sides; i++) {
                double cos = m.cos[i];
                double sin = m.sin[i];
                double th = Mth.TWO_PI * i / m.sides;
                double lob = 1.0 + lobes * (0.06 * Math.cos(2.0 * th + turn1) + 0.035 * Math.cos(3.0 * th + turn2));
                double dx = e1x * cos + e2x * sin;
                double dy = e1y * cos + e2y * sin;
                double dz = e1z * cos + e2z * sin;
                double rr = r * lob;
                double nx = dx - tx * drds;
                double ny = dy - ty * drds;
                double nz = dz - tz * drds;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                m.set(row, i, cxr + dx * rr, cyr + dy * rr, czr + dz * rr, nx / nl, ny / nl, nz / nl, colour);
            }
        }

        /** The storm canopy: a lumpy lens of dark cloud round the funnel's top, turning slowly. */
        private void buildCanopy(Vec3 c, float t, double eye, float rope) {
            float gather = gather(t);
            float dissolve = dissolve(t);
            spine(1.0, t, rope);
            double cx0 = c.x + sox;
            double cy0 = c.y + eye + height;
            double cz0 = c.z + soz;
            double big = canopyR * (0.4 + 0.6 * smooth(t / 12.0)) * (1.0 + 0.3 * dissolve);
            double rIn = Math.min(coreRadius(1.0, t, 1.0F, rope) * 0.9, big * 0.7);
            int rows = CANOPY_LOW + CANOPY_HIGH;
            for (int j = 0; j < rows; j++) {
                if (j < CANOPY_LOW) {
                    double s = (double) j / (CANOPY_LOW - 1);
                    canopyRho[j] = (float) Mth.lerp(Math.pow(s, 0.85), rIn, big);
                    canopyY[j] = (float) ((-0.3 + 0.95 * Math.pow(s, 1.6)) * scale);
                } else {
                    double s = 1.0 - (double) (j - CANOPY_LOW) / (CANOPY_HIGH - 1);
                    canopyRho[j] = (float) Mth.lerp(s, 0.4, big);
                    canopyY[j] = (float) ((0.65 + 1.4 * (1.0 - s * s)) * scale);
                }
            }
            int body = water.get(TideWater.BODY);
            double spin = t * 0.025;
            for (int j = 0; j < rows; j++) {
                boolean low = j < CANOPY_LOW;
                double s = low ? (double) j / (CANOPY_LOW - 1) : 1.0 - (double) (j - CANOPY_LOW) / (CANOPY_HIGH - 1);
                float a = (float) (low ? 0.95 - 0.55 * Math.pow(s, 1.5) : 0.35 + 0.5 * (1.0 - s)) * gather;
                int colour = FxDraw.argb(a, body);
                int j0 = Math.max(0, j - 1);
                int j1 = Math.min(rows - 1, j + 1);
                double dr = canopyRho[j1] - canopyRho[j0];
                double dy = canopyY[j1] - canopyY[j0];
                double len = Math.max(1.0E-4, Math.sqrt(dr * dr + dy * dy));
                double nr = dy / len;
                double nu = -dr / len;
                canopy.v(j, canopyRho[j]);
                for (int i = 0; i < CANOPY_SIDES; i++) {
                    double cos = canopy.cos[i];
                    double sin = canopy.sin[i];
                    double u = (double) i / CANOPY_SIDES * 6.0 - spin;
                    float n1 = noise(u, canopyRho[j] * 0.5 + t * 0.01, 6);
                    float n2 = noise(u * 2.0 + 3.0, canopyRho[j] * 0.9 - t * 0.015, 12);
                    double rho = canopyRho[j] + 0.6 * s * (n2 - 0.5) * scale;
                    double y = canopyY[j] + (low ? -0.6 * s * n1 * n1 : 0.55 * (1.0 - 0.5 * s) * n1) * scale;
                    canopy.set(j, i, cx0 + cos * rho, cy0 + y, cz0 + sin * rho, cos * nr, nu, sin * nr, colour);
                }
            }
        }

        /** Mist churning round the cascade and under the cloud (pixel puffs of the shader). */
        private void puffs(TideMesh.Emitter e, Vec3 c, float t) {
            float rope = rope(t);
            float base = touch(t) * (1.0F - lift(t)) * (1.0F - 0.4F * rope);
            spine(0.0, t, rope);
            double bx = c.x + sox;
            double bz = c.z + soz;
            double by = c.y + bowl(0.0, poolEnv(t));
            int mist = water.get(TideWater.PALE);
            if (base > 0.0F) {
                for (int i = 0; i < BASE_PUFFS; i++) {
                    float p = (t * 0.012F + (float) i / BASE_PUFFS) % 1.0F;
                    double a = Mth.TWO_PI * i / BASE_PUFFS + t * 0.045 + p * 1.5;
                    double rho = (1.4 + 2.4 * p) * scale;
                    float size = (float) ((0.55 + 0.9 * p) * scale);
                    float alpha = 0.42F * Mth.sin(p * Mth.PI) * base;
                    e.square((float) (bx + Math.cos(a) * rho), (float) (by + (0.25 + 1.9 * p) * scale), (float) (bz + Math.sin(a) * rho), size,
                            FxDraw.argb(alpha, mist), TideMesh.PUFF, i * 2.0F);
                }
            }
            float cloud = gather(t);
            if (cloud > 0.0F) {
                spine(1.0, t, rope);
                double big = canopyR * (0.4 + 0.6 * smooth(t / 12.0)) * (1.0 + 0.3 * dissolve(t));
                double top = c.y + bowl(0.0, poolEnv(t)) + height;
                for (int i = 0; i < TOP_PUFFS; i++) {
                    float h1 = hash(i, 11);
                    float h2 = hash(i, 23);
                    float h3 = hash(i, 37);
                    double a = Mth.TWO_PI * i / TOP_PUFFS + t * 0.012 * (1.0 + h1 * 0.5);
                    double rho = big * (0.45 + 0.6 * h2);
                    float size = (float) ((1.3 + 1.1 * h1) * (1.0 + 0.1 * Math.sin(t * 0.07 + i)) * scale);
                    double y = top + (0.1 + 1.7 * h3 + 0.15 * Math.sin(t * 0.05 + i)) * scale;
                    e.square((float) (c.x + sox + Math.cos(a) * rho), (float) y, (float) (c.z + soz + Math.sin(a) * rho), size,
                            FxDraw.argb(0.55F * cloud, water.cloud(0.55F + 0.45F * h3)), TideMesh.PUFF, (20 + i) * 2.0F);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the geyser

    static final class Geyser implements ClientFx {
        private static final int LIFE = 34;
        private static final int RISE = 5;
        private static final int HOLD = 14;
        private static final int SIDES = 28;
        private static final int COLUMN = 13;
        /** The crown bursting open at the top, curling over (radius, height past the top, times "open"). */
        private static final double[][] CROWN = {{0.0, 0.0}, {0.5, 0.5}, {1.2, 0.75}, {1.8, 0.45}, {2.1, -0.2}};
        private static final int CURTAIN = 6;
        private static final int SKIRT = 5;
        private static final int RING = 3;
        private final Vec3 center;
        private final float radius;
        private final float height;
        private final TideWater water;
        private final TideMesh column = new TideMesh(COLUMN, SIDES, TideMesh.JET);
        private final TideMesh crown = new TideMesh(CROWN.length, SIDES, TideMesh.FALL);
        private final TideMesh curtain = new TideMesh(CURTAIN, SIDES, TideMesh.FALL);
        private final TideMesh skirt = new TideMesh(SKIRT, SIDES, TideMesh.SKIRT);
        private final TideMesh ring = new TideMesh(RING, 40, TideMesh.SKIRT);
        private final TideMesh.Emitter emitter = new TideMesh.Emitter();
        private final TideDroplets drops;
        private int age;

        Geyser(FxPayload p) {
            this.center = p.a();
            this.radius = p.p0() > 0.0F ? p.p0() : (float) Balance.SICKLE_MAELSTROM_RADIUS;
            this.height = p.p1() > 0.0F ? p.p1() : TideCurrents.GEYSER_HEIGHT;
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            this.water = TideWater.at(level, center);
            this.drops = new TideDroplets(260, center, center.y);
            double d = dist(center);
            if (d < 18.0) {
                ScreenFx.shake((float) (3.0 * (1.0 - d / 18.0)) + 0.4F, 14);
            }
            if (d < radius) {
                ScreenFx.flash(water.raw(TideWater.PALE), 0.25F, 8);
            }
            if (level != null) {
                RandomSource r = level.random;
                for (int i = 0; i < 60; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = 0.4 + r.nextDouble() * 1.2;
                    double out = 0.15 + r.nextDouble() * 0.35;
                    drops.add(center.x + Math.cos(a) * rad, center.y + 0.2, center.z + Math.sin(a) * rad, Math.cos(a) * out,
                            0.3 + r.nextDouble() * 0.6, Math.sin(a) * out, 0.05F + r.nextFloat() * 0.07F, 40, TideDroplets.spraySlot(r));
                    if (i % 3 == 0) {
                        water.splash(center.x + Math.cos(a) * rad, center.y + 0.3, center.z + Math.sin(a) * rad, Math.cos(a) * out,
                                0.4 + r.nextDouble() * 0.3, Math.sin(a) * out, TideWater.LIGHT);
                        level.addParticle(rising(level, center.x, center.y - 0.2, center.z), center.x + Math.cos(a) * rad, center.y + 0.3,
                                center.z + Math.sin(a) * rad, 0.0, 0.15, 0.0);
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
            float t = Mth.clamp((time - HOLD) / (LIFE - 4 - HOLD), 0.0F, 1.0F);
            return height * (1.0F - t * t);
        }

        /** Column radius at fraction f of its height: a little narrower at the top, wobbling as it roars. */
        private static double columnRadius(double f, float time) {
            return 1.15 - 0.25 * f + 0.05 * Mth.sin(time * 1.3F + (float) f * 6.0F);
        }

        private float open(float time) {
            return Mth.clamp((time - RISE + 1.0F) / 6.0F, 0.0F, 1.0F);
        }

        @Override
        public boolean tick() {
            drops.tick();
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age < HOLD + 6) {
                RandomSource r = level.random;
                float h = columnHeight(age);
                float open = open(age);
                // spray flung off the crown's lip, raining back down
                for (int i = 0; i < 6; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double lip = columnRadius(1.0, age) + 1.6 * open;
                    double out = 0.08 + r.nextDouble() * 0.25;
                    drops.add(center.x + Math.cos(a) * lip, center.y + h + 0.4 * open, center.z + Math.sin(a) * lip, Math.cos(a) * out,
                            0.05 + r.nextDouble() * 0.3, Math.sin(a) * out, 0.05F + r.nextFloat() * 0.06F, 45, TideDroplets.spraySlot(r));
                }
                for (int i = 0; i < 3; i++) {
                    // water peeling off the column at every height
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double y = center.y + r.nextDouble() * h;
                    double rad = 1.0 + 0.3 * r.nextDouble();
                    water.splash(center.x + Math.cos(a) * rad, y, center.z + Math.sin(a) * rad, Math.cos(a) * 0.25, 0.1, Math.sin(a) * 0.25,
                            r.nextBoolean() ? TideWater.LIGHT : TideWater.PALE);
                    double b = r.nextDouble() * Mth.TWO_PI;
                    double br = 0.5 + r.nextDouble() * 2.6;
                    water.drip(center.x + Math.cos(b) * br, center.y + h * (0.8 + 0.2 * r.nextDouble()), center.z + Math.sin(b) * br);
                }
                double bx = center.x + (r.nextDouble() - 0.5);
                double bz = center.z + (r.nextDouble() - 0.5);
                level.addParticle(rising(level, bx, center.y + 0.5, bz), bx, center.y + 0.5 + r.nextDouble() * h * 0.5, bz, 0.0, 0.1, 0.0);
            }
            return ++age < LIFE || drops.count() > 0 && age < LIFE + 30;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float h = columnHeight(time);
            float fade = 1.0F - Mth.clamp((time - HOLD) / (LIFE - HOLD), 0.0F, 1.0F);
            Camera camera = ctx.camera();
            TideMesh.Emitter e = emitter.begin(ctx.buffers().getBuffer(TideRenderTypes.vortex()), ctx.pose().last(),
                    TideRenderTypes.shaded(), camera.getLeftVector(), camera.getUpVector());
            if (fade > 0.0F && h > 0.05F) {
                Vec3 c = ctx.rel(center).add(0.0, 0.03, 0.0);
                float open = open(time);
                float burst = Mth.clamp(time / 8.0F, 0.0F, 1.0F);
                float spread = Mth.clamp(time / 12.0F, 0.0F, 1.0F);
                int body = water.get(TideWater.BODY);
                build(c, time, h, fade, open, burst, spread, body);
                ring.emit(e, 0, RING - 1);
                skirt.emit(e, 0, SKIRT - 1);
                column.emit(e, 0, COLUMN - 1);
                if (open > 0.0F) {
                    crown.emit(e, 0, CROWN.length - 1);
                    curtain.emit(e, 0, CURTAIN - 1);
                }
            }
            drops.render(e, ctx.cam(), ctx.partialTick(), water, 1.0F);
        }

        private void build(Vec3 c, float time, float h, float fade, float open, float burst, float spread, int body) {
            for (int j = 0; j < COLUMN; j++) {
                double f = (double) j / (COLUMN - 1);
                double r = columnRadius(f, time);
                double dr = columnRadius(Math.min(1.0, f + 0.05), time) - columnRadius(Math.max(0.0, f - 0.05), time);
                float a = 0.82F * fade * (j == 0 ? 0.5F : 1.0F);
                column.v(j, (float) (f * h));
                lathe(column, j, c.x, c.y + f * h, c.z, r, dr, 0.1 * h, FxDraw.argb(a, body));
            }
            double top = columnRadius(1.0, time);
            for (int j = 0; j < CROWN.length; j++) {
                double r = top + CROWN[j][0] * open;
                double y = h + CROWN[j][1] * open;
                int j0 = Math.max(0, j - 1);
                int j1 = Math.min(CROWN.length - 1, j + 1);
                float a = (0.8F - 0.12F * j) * fade * open;
                crown.v(j, (float) y);
                lathe(crown, j, c.x, c.y + y, c.z, r, (CROWN[j1][0] - CROWN[j0][0]) * open, (CROWN[j1][1] - CROWN[j0][1]) * open + 1.0E-3,
                        FxDraw.argb(a, body));
            }
            double lipY = h + CROWN[CROWN.length - 1][1] * open;
            double lipR = top + CROWN[CROWN.length - 1][0] * open;
            for (int j = 0; j < CURTAIN; j++) {
                float g = (float) j / (CURTAIN - 1);
                double y = Mth.lerp(g, lipY, h * 0.2);
                curtain.v(j, (float) y);
                lathe(curtain, j, c.x, c.c(y), c.z, lipR + 0.4 * g, 0.4, -(lipY - h * 0.2), FxDraw.argb(0.75F * (1.0F - g) * fade * open, body));
            }
            // the spray skirt bursting out at the foot
            for (int j = 0; j < SKIRT; j++) {
                double s = (double) j / (SKIRT - 1);
                double rho = 1.0 + (1.2 + 2.6 * burst) * s;
                double y = 1.8 * (1.0 - 0.6 * burst) * Math.sin(Math.PI * 0.9 * s);
                skirt.v(j, (float) (s * 8.0));
                lathe(skirt, j, c.x, c.y + y, c.z, rho, 1.0, 0.6 - s, FxDraw.argb((float) (0.8 * (1.0 - s)) * (1.0F - 0.6F * burst) * fade, body));
            }
            // a ring of foam racing out over the water
            double front = 1.3 + (radius - 0.5) * spread;
            for (int j = 0; j < RING; j++) {
                double rho = front + (j - 1) * 0.55;
                double y = j == 1 ? 0.14 : 0.03;
                ring.v(j, (float) (j * 3.0 + time * 0.2));
                lathe(ring, j, c.x, c.y + y, c.z, Math.max(0.2, rho), 1.0, 0.2, FxDraw.argb(j == 1 ? 0.75F * (1.0F - spread) * fade : 0.0F, body));
            }
        }

        /** A horizontal ring of a surface of revolution round the column's axis; (dr, dy) is the profile's slope. */
        private static void lathe(TideMesh m, int row, double cx, double cy, double cz, double r, double dr, double dy, int colour) {
            double len = Math.sqrt(dr * dr + dy * dy);
            double nr = len < 1.0E-6 ? 1.0 : dy / len;
            double nu = len < 1.0E-6 ? 0.0 : -dr / len;
            for (int i = 0; i < m.sides; i++) {
                double cos = m.cos[i];
                double sin = m.sin[i];
                m.set(row, i, cx + cos * r, cy, cz + sin * r, cos * nr, nu, sin * nr, colour);
            }
        }
    }
}
