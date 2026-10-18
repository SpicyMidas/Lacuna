package com.lacuna.mod.client.gear;

import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.gear.QuickeningWandItem;
import com.lacuna.mod.network.FxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Wand of Quickening visuals ({@value QuickeningWandItem#BOLT_FX}): the spark of life leaves the wand as a glowing
 * shader orb (soft pink halo, white-hot heart, flickering), flies to the item along a gentle spiralling arc trailing a
 * tapering ribbon of light, follows the item if it already hops away, and bursts on arrival in a ring of light with a
 * few hearts. Purely visual: the item is woken on the server as before.
 */
public final class QuickeningFx {
    private static final int PINK = 0xFF6BC7;
    private static final int ROSE = 0xFFA9DE;
    private static final int WHITE = 0xFFF4FB;
    private static final int TRAIL = 14;

    private QuickeningFx() {}

    public static void register() {
        FxHandlers.register(QuickeningWandItem.BOLT_FX, p -> FxManager.add(new Bolt(p)));
    }

    private static final class Bolt implements ClientFx {
        private final int casterId;
        private final int targetId;
        private final Vec3 start;
        private final Vec3 fallbackEnd;
        private final int flight;
        private final float spin;
        private final Vec3[] trail = new Vec3[TRAIL];
        private int trailCount;
        private Vec3 last;
        private Vec3 prev;
        private int age;
        private int burstAge = -1;

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
            this.flight = Mth.clamp((int) Math.ceil(from.distanceTo(fallbackEnd) / 0.85), 5, 24);
            this.spin = p.p1();
            this.last = from;
            this.prev = from;
        }

        private Vec3 end(float partial) {
            ClientLevel level = Minecraft.getInstance().level;
            Entity e = level != null ? level.getEntity(targetId) : null;
            return e != null ? e.getPosition(partial).add(0.0, e.getBbHeight() * 0.6, 0.0) : fallbackEnd;
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
                if (trailCount > 0) {
                    trailCount--;
                }
                return burstAge < 10;
            }
            prev = last;
            Vec3 end = end(1.0F);
            last = at(Math.min(1.0F, age / (float) flight), end);
            System.arraycopy(trail, 0, trail, 1, TRAIL - 1);
            trail[0] = last;
            trailCount = Math.min(TRAIL, trailCount + 1);
            if (r.nextFloat() < 0.5F) {
                level.addParticle(ParticleTypes.END_ROD, last.x, last.y, last.z, (r.nextDouble() - 0.5) * 0.02,
                        (r.nextDouble() - 0.5) * 0.02, (r.nextDouble() - 0.5) * 0.02);
            }
            if (age >= flight) {
                burstAge = 0;
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
            Vec3 head = burstAge >= 0 ? end(pt) : prev.lerp(last, pt);
            // tapering ribbon of light through the last positions
            if (trailCount > 1) {
                VertexConsumer vc = ctx.buffers().getBuffer(FxRenderTypes.beam());
                Vec3 a = ctx.rel(head);
                float v = 0.0F;
                for (int i = 1; i < trailCount; i++) {
                    Vec3 b = ctx.rel(trail[i]);
                    float fa = 1.0F - (i - 1) / (float) TRAIL;
                    float fb = 1.0F - i / (float) TRAIL;
                    float seg = (float) a.distanceTo(b);
                    FxDraw.ribbon(pose, vc, a, b, 0.22F * fa, 0.22F * fb, FxDraw.argb(0.85F * fa, PINK),
                            FxDraw.argb(0.85F * fb, ROSE), v, v + seg);
                    v += seg;
                    a = b;
                }
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
                // the burst: a flash and a widening ring of light that fades
                float f = (burstAge + pt) / 10.0F;
                FxDraw.billboard(pose, glow, ctx.camera(), c, 0.5F + 0.9F * f, 0.0F, FxDraw.argb(0.9F * (1.0F - f), PINK));
                FxDraw.billboard(pose, glow, ctx.camera(), c, 0.35F * (1.0F - f), 0.0F, FxDraw.argb(1.0F - f, WHITE));
                VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
                FxDraw.billboard(pose, ring, ctx.camera(), c, 0.3F + 1.4F * f, 0.0F, FxDraw.argb(0.8F * (1.0F - f), ROSE));
            }
        }
    }
}
