package com.lacuna.mod.client.gear;

import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.FxTubes;
import com.lacuna.mod.beam.BeamTrace;
import com.lacuna.mod.gear.AnimatedAbilities;
import com.lacuna.mod.gear.AnimatedItemEntity;
import com.lacuna.mod.gear.QuickeningBolt;
import com.lacuna.mod.gear.QuickeningWandItem;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Wand of Quickening visuals ({@value QuickeningWandItem#BOLT_FX}): the spark of life leaves the wand as a glowing
 * shader orb (soft pink halo, white-hot heart, flickering), flies to the item along a gentle spiralling arc trailing a
 * tapering ribbon of light, follows the item wherever it moves (a tossed off-hand item is chased through the air), and
 * bursts on arrival in a ring of light with a few hearts. The flight time comes with the payload (computed on the server
 * by {@link QuickeningBolt#flightTicks}, the same rule this side falls back on), so the burst lands on the tick the
 * server wakes the item; an item that vanishes early (the spark is lost) gets a small grey fizzle instead.
 */
public final class QuickeningFx {
    private static final int PINK = 0xFF6BC7;
    private static final int ROSE = 0xFFA9DE;
    private static final int WHITE = 0xFFF4FB;
    /** Length of the spark's trail, in ticks of its flight. */
    private static final int TRAIL = 14;
    /** Points sampled along the trail every frame (on the flight curve itself, so it slides without steps). */
    private static final int TRAIL_POINTS = 20;

    private QuickeningFx() {}

    public static void register() {
        FxHandlers.register(QuickeningWandItem.BOLT_FX, p -> FxManager.add(new Bolt(p)));
        FxHandlers.register(AnimatedAbilities.BEAM_FX, p -> FxManager.add(new SunBeam(p)));
    }

    /**
     * An animated Divine Magnifying Glass focusing sunlight on its target ({@value AnimatedAbilities#BEAM_FX}): the
     * beam follows both creatures and ends where the shared {@link BeamTrace} stops, in the beam's own colour, until the
     * item stops glowing (the server ended the power) or its time is up.
     */
    private static final class SunBeam implements ClientFx {
        private final int userId;
        private final int targetId;
        private final int life;
        private int age;

        SunBeam(FxPayload p) {
            this.userId = p.entityId();
            this.targetId = (int) p.p0();
            this.life = Math.max(1, (int) p.p1()) + 4;
        }

        @Nullable
        private AnimatedItemEntity user() {
            ClientLevel level = Minecraft.getInstance().level;
            return level != null && level.getEntity(userId) instanceof AnimatedItemEntity e && !e.isRemoved() ? e : null;
        }

        @Nullable
        private Entity target() {
            ClientLevel level = Minecraft.getInstance().level;
            Entity e = level != null ? level.getEntity(targetId) : null;
            return e != null && !e.isRemoved() ? e : null;
        }

        @Override
        public boolean tick() {
            age++;
            AnimatedItemEntity user = user();
            ClientLevel level = Minecraft.getInstance().level;
            if (user == null || target() == null || level == null || age > life || (age > 6 && user.clientPower < 0.5F)) {
                return false;
            }
            BeamTrace trace = trace(user, 1.0F);
            if (trace != null && trace.hitsSomething()) {
                Vec3 hit = trace.stop().subtract(trace.look().scale(0.08));
                level.addParticle(ModParticles.SOLAR_GLINT.get(), hit.x, hit.y, hit.z, 0.0, 0.0, 0.0);
            }
            return true;
        }

        @Nullable
        private BeamTrace trace(AnimatedItemEntity user, float pt) {
            Entity target = target();
            if (target == null) {
                return null;
            }
            Vec3 eye = user.getEyePosition(pt);
            Vec3 d = target.getPosition(pt).add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(eye);
            if (d.lengthSqr() < 1.0E-6) {
                return null;
            }
            return BeamTrace.of(user.level(), user, eye, d.normalize(), pt);
        }

        @Override
        public void render(FxRenderContext ctx) {
            AnimatedItemEntity user = user();
            if (user == null) {
                return;
            }
            float pt = ctx.partialTick();
            BeamTrace trace = trace(user, pt);
            if (trace == null) {
                return;
            }
            float t = ctx.time(age);
            float in = Mth.clamp(t / 4.0F, 0.0F, 1.0F) * Mth.clamp((life - t) / 4.0F, 0.0F, 1.0F);
            float shimmer = (0.9F + 0.1F * Mth.sin(t * 1.9F)) * in;
            int rgb = trace.beam().color();
            Vec3 lens = trace.eye().add(trace.look().scale(0.25));
            Vec3 a = ctx.rel(lens);
            Vec3 b = ctx.rel(trace.stop());
            float len = (float) lens.distanceTo(trace.stop());
            PoseStack.Pose pose = ctx.pose().last();
            // the beam's halo, body and white core as nested volumetric rays (FxTubes), or the flat ribbons
            RenderType beam = FxRenderTypes.beam();
            FxTubes.beamOrRibbon(pose, ctx.buffers(), beam, a, b, 0.32F, 0.22F, FxDraw.argb(0.35F * shimmer, rgb),
                    FxDraw.argb(0.5F * shimmer, rgb), 0.0F, len, 0);
            FxTubes.beamOrRibbon(pose, ctx.buffers(), beam, a, b, 0.14F, 0.09F, FxDraw.argb(0.85F * shimmer, rgb),
                    FxDraw.argb(0.95F * shimmer, rgb), 0.0F, len, 0);
            FxTubes.beamOrRibbon(pose, ctx.buffers(), beam, a, b, 0.03F, 0.04F, FxDraw.argb(in, WHITE), FxDraw.argb(in, WHITE), 0.0F, len, 0);
            VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
            FxDraw.billboard(pose, glow, ctx.camera(), a, 0.28F * shimmer, t * 0.05F, FxDraw.argb(0.9F * in, WHITE));
            if (trace.hitsSomething()) {
                FxDraw.billboard(pose, glow, ctx.camera(), b, 0.5F + 0.08F * Mth.sin(t * 3.1F), -t * 0.04F, FxDraw.argb(0.9F * shimmer, rgb));
            }
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    private static final class Bolt implements ClientFx {
        private static final int BURST_TICKS = 10;
        private final int casterId;
        private final int targetId;
        private final Vec3 start;
        private final Vec3 fallbackEnd;
        private final int flight;
        private final float spin;
        /** The orb's position at the last tick (for the sparks it sheds and where a lost spark gutters out). */
        private Vec3 last;
        /** Where the item was last seen (it is gone once it wakes). */
        private Vec3 lastEnd;
        private int age;
        private int burstAge = -1;
        /** The flight age at which the burst began: the flight time, or earlier for a lost spark. */
        private int stopAge;
        private boolean fizzle;

        Bolt(FxPayload p) {
            this.targetId = p.entityId();
            this.casterId = (int) p.p0();
            this.fallbackEnd = p.b();
            Vec3 from = p.a();
            Player self = Minecraft.getInstance().player;
            if (self != null && self.getId() == casterId && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                // in first person the orb leaves the wand drawn low in the screen corner
                Vec3 look = self.getViewVector(1.0F);
                Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
                Vec3 up = right.cross(look).normalize();
                from = self.getEyePosition().add(look.scale(0.9)).add(right.scale(0.45)).add(up.scale(-0.35));
            }
            this.start = from;
            // the server's flight time, never the first-person start above: both sides must agree on the tick of impact
            this.flight = p.p2() >= 1.0F ? Math.round(p.p2()) : QuickeningBolt.flightTicks(p.a().distanceTo(p.b()));
            this.lastEnd = fallbackEnd;
            this.spin = p.p1();
            this.last = from;
        }

        @Nullable
        private Entity target() {
            ClientLevel level = Minecraft.getInstance().level;
            Entity e = level != null ? level.getEntity(targetId) : null;
            return e != null && !e.isRemoved() ? e : null;
        }

        private Vec3 end(float partial) {
            Entity e = target();
            return e != null ? e.getPosition(partial).add(0.0, Math.max(0.25, e.getBbHeight() * 0.6), 0.0) : lastEnd;
        }

        /**
         * The tapering trail of light from the orb ({@code headClock}, in ticks of flight) back to {@code tailClock}: a
         * volumetric chain (FxTubes; it may start right at the eye in first person: faded there), or the flat ribbons.
         */
        private void trail(FxRenderContext ctx, PoseStack.Pose pose, Vec3 end, float headClock, float tailClock) {
            Vec3[] points = new Vec3[TRAIL_POINTS];
            float[] fades = new float[TRAIL_POINTS];
            for (int i = 0; i < TRAIL_POINTS; i++) {
                float s = Mth.lerp(i / (float) (TRAIL_POINTS - 1), headClock, tailClock);
                points[i] = ctx.rel(at(s / flight, end));
                fades[i] = 1.0F - (headClock - s) / TRAIL;
            }
            if (FxTubes.chain().begin(pose, TRAIL_POINTS, 0.3F, FxTubes.FLOW | FxTubes.NEAR_FADE)) {
                FxTubes.Chain chain = FxTubes.chain();
                float v = 0.0F;
                for (int i = 0; i < TRAIL_POINTS; i++) {
                    if (i > 0) {
                        v += (float) points[i].distanceTo(points[i - 1]);
                    }
                    int argb = FxDraw.argb(0.85F * fades[i], mix(PINK, ROSE, i / (float) (TRAIL_POINTS - 1)));
                    chain.point(points[i], 0.11F * fades[i], argb, FxTubes.hot(argb), v);
                }
                chain.end();
                return;
            }
            VertexConsumer vc = ctx.buffers().getBuffer(FxRenderTypes.beam());
            float v = 0.0F;
            for (int i = 1; i < TRAIL_POINTS; i++) {
                float seg = (float) points[i - 1].distanceTo(points[i]);
                FxDraw.ribbon(pose, vc, points[i - 1], points[i], 0.22F * fades[i - 1], 0.22F * fades[i],
                        FxDraw.argb(0.85F * fades[i - 1], PINK), FxDraw.argb(0.85F * fades[i], ROSE), v, v + seg);
                v += seg;
            }
        }

        /** The orb on its arc at progress {@code t}: an ease-in flight bowed upwards with a slow corkscrew. */
        private Vec3 at(float t, Vec3 end) {
            float e = t * t * (3.0F - 2.0F * t) * 0.35F + t * 0.65F;
            Vec3 line = start.lerp(end, e);
            Vec3 dir = end.subtract(start);
            double len = Math.max(0.001, dir.length());
            Vec3 d = dir.scale(1.0 / len);
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(d).normalize();
            double bow = Math.sin(Math.PI * t) * Math.min(1.2, len * 0.18);
            double a = spin + t * Math.PI * 3.0;
            double wob = Math.sin(Math.PI * t) * 0.22;
            return line.add(up.scale(bow + Math.sin(a) * wob)).add(side.scale(Math.cos(a) * wob));
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return false;
            }
            age++;
            RandomSource r = level.random;
            if (burstAge >= 0) {
                burstAge++;
                return burstAge < BURST_TICKS;
            }
            if (target() != null) {
                lastEnd = end(1.0F);
            } else if (age < flight - 2) {
                fizzle = true; // the item is gone long before the spark could reach it
            }
            Vec3 end = end(1.0F);
            last = at(Math.min(1.0F, age / (float) flight), end);
            if (r.nextFloat() < 0.5F) {
                level.addParticle(ParticleTypes.END_ROD, last.x, last.y, last.z, (r.nextDouble() - 0.5) * 0.02,
                        (r.nextDouble() - 0.5) * 0.02, (r.nextDouble() - 0.5) * 0.02);
            }
            if (age >= flight || fizzle) {
                burstAge = 0;
                stopAge = Math.min(age, flight);
                if (fizzle) {
                    for (int i = 0; i < 6; i++) {
                        level.addParticle(ParticleTypes.SMOKE, last.x, last.y, last.z, (r.nextDouble() - 0.5) * 0.05, 0.03,
                                (r.nextDouble() - 0.5) * 0.05);
                    }
                    return true;
                }
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.HEART, end.x + (r.nextDouble() - 0.5) * 0.5, end.y + 0.2 + r.nextDouble() * 0.3,
                            end.z + (r.nextDouble() - 0.5) * 0.5, 0.0, 0.05, 0.0);
                }
                for (int i = 0; i < 10; i++) {
                    level.addParticle(ParticleTypes.END_ROD, end.x, end.y, end.z, (r.nextDouble() - 0.5) * 0.16,
                            (r.nextDouble() - 0.3) * 0.12, (r.nextDouble() - 0.5) * 0.16);
                }
            }
            return true;
        }

        @Override
        public void render(FxRenderContext ctx) {
            PoseStack.Pose pose = ctx.pose().last();
            float pt = ctx.partialTick();
            float time = ctx.time(age);
            // the orb and its trail lie on the flight curve itself, sampled every frame at the frame's own time (not at
            // the last ticks' positions), so the spark glides and its trail flows without steps or popping
            Vec3 end = fizzle ? lastEnd : end(pt);
            float clock = (burstAge >= 0 ? stopAge + burstAge : age) + pt;
            float headClock = burstAge >= 0 ? stopAge : Math.min(clock, flight);
            float tailClock = burstAge >= 0
                    ? Mth.lerp(Math.min(1.0F, (burstAge + pt) / BURST_TICKS), stopAge - TRAIL, stopAge) : clock - TRAIL;
            tailClock = Math.max(0.0F, tailClock);
            Vec3 head = fizzle && burstAge >= 0 ? last : at(headClock / flight, end);
            if (headClock - tailClock > 0.05F) {
                trail(ctx, pose, end, headClock, tailClock);
            }
            Vec3 c = ctx.rel(head);
            VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
            if (burstAge < 0) {
                float flicker = 0.85F + 0.15F * Mth.sin(time * 1.7F);
                FxDraw.billboard(pose, glow, ctx.camera(), c, 0.55F * flicker, time * 0.08F, FxDraw.argb(0.7F, PINK));
                FxDraw.billboard(pose, glow, ctx.camera(), c, 0.22F, -time * 0.12F, FxDraw.argb(1.0F, WHITE));
                VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
                FxDraw.billboard(pose, ring, ctx.camera(), c, 0.32F + 0.05F * Mth.sin(time * 0.9F), time * 0.2F,
                        FxDraw.argb(0.45F, ROSE));
            } else {
                // the burst: a flash and a widening ring of light that fades (a lost spark only gutters out)
                float f = (burstAge + pt) / BURST_TICKS;
                if (fizzle) {
                    FxDraw.billboard(pose, glow, ctx.camera(), c, 0.4F * (1.0F - f), 0.0F, FxDraw.argb(0.5F * (1.0F - f), ROSE));
                    return;
                }
                FxDraw.billboard(pose, glow, ctx.camera(), c, 0.5F + 0.9F * f, 0.0F, FxDraw.argb(0.9F * (1.0F - f), PINK));
                FxDraw.billboard(pose, glow, ctx.camera(), c, 0.35F * (1.0F - f), 0.0F, FxDraw.argb(1.0F - f, WHITE));
                VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
                FxDraw.billboard(pose, ring, ctx.camera(), c, 0.3F + 1.4F * f, 0.0F, FxDraw.argb(0.8F * (1.0F - f), ROSE));
            }
        }
    }
}
