package com.lacuna.mod.client.eclipse;

import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.network.FxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The air wake of the Eclipser dash, like the air a speeding train shoves aside:
 * <ul>
 *   <li>with every {@code eclipse_dash}: pale wind lines peeling away from both sides of the path behind the dasher
 *       (a V-shaped wake that widens as it fades), cloud puffs and small gusts thrown sideways from the head of the
 *       dash, and dust kicked off the ground on both sides;</li>
 *   <li>{@code eclipse_gust}: the rush of air over a creature the wake throws aside (a big gust on a cut creature,
 *       a small one on a creature merely pushed), blowing the way it flies.</li>
 * </ul>
 */
final class EclipseWakeFx {
    private static final int AIR = 0xDCE6F2;

    private EclipseWakeFx() {}

    static void register() {
        FxHandlers.register("eclipse_gust", EclipseWakeFx::gust);
    }

    /** Starts the wake of a dash from {@code start} to {@code end} lasting {@code ticks}; {@code power} 0..1 (charge). */
    static void dash(Vec3 start, Vec3 end, int ticks, float power) {
        if (start.distanceToSqr(end) > 0.25) {
            FxManager.add(new Wake(start, end, Math.max(1, ticks), power));
        }
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static float nearFade(FxRenderContext ctx, Vec3 world, double from, double span) {
        return Mth.clamp((float) ((ctx.cam().distanceTo(world) - from) / span), 0.0F, 1.0F);
    }

    private static void puff(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.addParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    /** Dust of the block under {@code feet}, blown along {@code push}; nothing over air or liquids. */
    private static void dust(ClientLevel level, Vec3 feet, Vec3 push, int count, RandomSource rnd) {
        BlockPos below = BlockPos.containing(feet.x, feet.y - 0.2, feet.z);
        BlockState state = level.getBlockState(below);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE || !state.getFluidState().isEmpty()
                || feet.y - (below.getY() + 1.0) > 0.6) {
            return;
        }
        BlockParticleOption option = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < count; i++) {
            double s = 0.6 + rnd.nextDouble() * 0.8;
            level.addParticle(option, feet.x + (rnd.nextDouble() - 0.5) * 0.6, below.getY() + 1.05, feet.z + (rnd.nextDouble() - 0.5) * 0.6,
                    push.x * s, 0.12 + rnd.nextDouble() * 0.15, push.z * s);
        }
    }

    // ------------------------------------------------------------------------------------------ gust on a creature

    /** a = creature centre, b = its new velocity, p0/p1 = height/width, p2 = 1 if it was cut (bigger burst). */
    private static void gust(FxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource rnd = level.random;
        Vec3 c = p.a();
        Vec3 flat = new Vec3(p.b().x, 0.0, p.b().z);
        double speed = flat.length();
        Vec3 d = speed > 1.0E-4 ? flat.scale(1.0 / speed) : new Vec3(1.0, 0.0, 0.0);
        Vec3 across = new Vec3(-d.z, 0.0, d.x);
        float h = Math.max(0.3F, p.p0());
        float w = Math.max(0.3F, p.p1());
        boolean cut = p.p2() > 0.5F;
        double force = Mth.clamp(speed, 0.2, 1.4);
        // air rushing past the creature from the dash side, in the direction it is thrown
        int puffs = cut ? 9 : 5;
        for (int i = 0; i < puffs; i++) {
            Vec3 at = c.add(d.scale(-(w * 0.5 + 0.2) + rnd.nextDouble() * 0.4))
                    .add(across.scale((rnd.nextDouble() - 0.5) * (w + 0.6)))
                    .add(0.0, (rnd.nextDouble() - 0.5) * h * 0.9, 0.0);
            Vec3 v = d.scale((0.12 + rnd.nextDouble() * 0.22) * (0.6 + force)).add(0.0, 0.01 + rnd.nextDouble() * 0.03, 0.0);
            puff(level, i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF, at, v.scale(i % 3 == 0 ? 0.6 : 1.0));
        }
        Vec3 burst = c.add(d.scale(w * 0.35));
        puff(level, cut ? ParticleTypes.GUST : ParticleTypes.SMALL_GUST, burst, Vec3.ZERO);
        if (cut) {
            puff(level, ParticleTypes.SWEEP_ATTACK, c.add(d.scale(-0.2)), Vec3.ZERO);
        }
        dust(level, c.subtract(0.0, h * 0.5, 0.0), d.scale(0.25 * (0.6 + force)), cut ? 8 : 4, rnd);
    }

    // ------------------------------------------------------------------------------------------ wake of the dash

    /** Wind lines on both sides of the path and the particles thrown sideways off it. */
    static final class Wake implements ClientFx {
        private static final int FADE = 14;
        private static final int LINES_PER_SIDE = 4;

        private final Vec3 start;
        private final Vec3 end;
        private final Vec3 dir;
        private final Vec3 side;
        private final double length;
        private final int ticks;
        private final float power;
        private final RandomSource rnd;
        /** per wind line: side sign, height, offset at the head, spread speed, delay (ticks), width */
        private final float[][] lines;
        private int age;

        Wake(Vec3 start, Vec3 end, int ticks, float power) {
            this.start = start;
            this.end = end;
            this.length = start.distanceTo(end);
            this.dir = end.subtract(start).normalize();
            Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
            if (flat.lengthSqr() > 0.0225) {
                flat = flat.normalize();
                this.side = new Vec3(-flat.z, 0.0, flat.x);
            } else {
                this.side = dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
            }
            this.ticks = ticks;
            this.power = power;
            this.rnd = RandomSource.create(Double.doubleToLongBits(start.x * 13.0 + start.z * 29.0 + start.y * 7.0));
            this.lines = new float[LINES_PER_SIDE * 2][];
            for (int k = 0; k < lines.length; k++) {
                float sign = k < LINES_PER_SIDE ? 1.0F : -1.0F;
                int layer = k % LINES_PER_SIDE;
                lines[k] = new float[] {sign, 0.2F + layer * 0.5F + rnd.nextFloat() * 0.2F, 0.5F + rnd.nextFloat() * 0.3F,
                        (0.16F + rnd.nextFloat() * 0.08F) * (0.8F + 0.6F * power), rnd.nextFloat() * 1.2F, 0.06F + rnd.nextFloat() * 0.06F};
            }
        }

        private Vec3 at(double frac) {
            return start.add(end.subtract(start).scale(frac));
        }

        @Override
        public boolean tick() {
            age++;
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age >= 2 && age <= ticks + 1) {
                // the dasher starts moving the tick after the payload: throw the air off the head of the dash
                double f0 = (age - 2) / (double) ticks;
                double f1 = Math.min(1.0, (age - 1) / (double) ticks);
                double force = 0.7 + 0.6 * power;
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 out = side.scale(s);
                    for (int i = 0; i < 3; i++) {
                        Vec3 p = at(Mth.lerp(rnd.nextDouble(), f0, f1)).add(out.scale(0.55 + rnd.nextDouble() * 0.35))
                                .add(0.0, 0.15 + rnd.nextDouble() * 1.7, 0.0);
                        Vec3 v = out.scale((0.16 + rnd.nextDouble() * 0.16) * force).add(dir.scale(0.05)).add(0.0, 0.01, 0.0);
                        puff(level, i == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF, p, i == 0 ? v.scale(0.7) : v);
                    }
                    if (rnd.nextInt(3) != 0) {
                        Vec3 p = at(f1).add(out.scale(1.0 + rnd.nextDouble() * 0.5)).add(0.0, 0.5 + rnd.nextDouble() * 0.9, 0.0);
                        puff(level, ParticleTypes.SMALL_GUST, p, Vec3.ZERO);
                    }
                    dust(level, at(Mth.lerp(rnd.nextDouble(), f0, f1)).add(out.scale(0.5)), out.scale(0.22 * force), 2, rnd);
                }
            }
            return age < ticks + FADE + 4;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float t = Math.max(0.0F, ctx.time(age) - 1.0F);
            float head = Mth.clamp(t / ticks, 0.0F, 1.0F);
            if (head <= 0.0F) {
                return;
            }
            PoseStack.Pose pose = ctx.pose().last();
            VertexConsumer vc = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
            int m = Math.max(4, (int) Math.ceil(length * 1.3));
            for (float[] line : lines) {
                Vec3 prev = null;
                int prevC = 0;
                float prevW = 0.0F;
                double prevV = 0.0;
                for (int i = 0; i <= m; i++) {
                    float f = head * i / m;
                    float since = t - (f * ticks + line[4]);
                    // the wake opens behind the head: the further behind, the wider and fainter
                    float life = Mth.clamp(since / FADE, 0.0F, 1.0F);
                    float open = Math.max(0.0F, since);
                    double off = line[2] + Math.min(3.4F, line[3] * (float) Math.pow(open, 0.85));
                    Vec3 world = at(f).add(side.scale(line[0] * off)).add(0.0, line[1], 0.0);
                    float alpha = 0.5F * (float) Math.pow(1.0F - life, 1.4) * Mth.clamp(open / 1.5F, 0.0F, 1.0F)
                            * nearFade(ctx, world, 0.6, 1.4) * (0.75F + 0.25F * power);
                    int color = FxDraw.argb(alpha, AIR);
                    float w = line[5] * (1.0F + 1.5F * life);
                    Vec3 p = ctx.rel(world);
                    double v = f * length;
                    if (prev != null && ((prevC >>> 24) | (color >>> 24)) != 0) {
                        FxDraw.ribbon(pose, vc, prev, p, prevW, w, prevC, color, (float) prevV, (float) v);
                    }
                    prev = p;
                    prevC = color;
                    prevW = w;
                    prevV = v;
                }
            }
        }
    }
}
