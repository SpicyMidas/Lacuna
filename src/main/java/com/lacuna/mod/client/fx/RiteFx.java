package com.lacuna.mod.client.fx;

import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Travel rites: the {@code rift} (Totem of Banishing crossings: a pale vortex that swallows or releases the traveller,
 * with a fade-from-grey flash for them) and the {@code rewind} (Chronoshard Pocket Watch: a golden clock ring
 * turning backwards and a thread of light back along the jump).
 */
public final class RiteFx {
    private RiteFx() {}

    public static void register() {
        FxHandlers.register("rift", p -> {
            FxManager.add(new Rift(p.a(), p.color() & 0xFFFFFF));
            flashIfHere(p.a(), p.color() & 0xFFFFFF, 0.55F, 24);
        });
        FxHandlers.register("rewind", p -> {
            FxManager.add(new Rewind(p.a(), p.b()));
            flashIfHere(p.a(), 0xFFD27A, 0.35F, 14);
        });
    }

    private static void flashIfHere(Vec3 at, int rgb, float alpha, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.position().distanceToSqr(at) < 2.5 * 2.5) {
            ScreenFx.flash(rgb, alpha, ticks);
        }
    }

    /** Pale vortex: three tilted rings spinning inward around a flare, motes spiralling into the centre. */
    private static final class Rift implements ClientFx {
        private static final int LIFE = 30;
        private final Vec3 at;
        private final int rgb;
        private int age;

        Rift(Vec3 at, int rgb) {
            this.at = at.add(0, 1.0, 0);
            this.rgb = rgb;
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age < 18) {
                RandomSource r = level.random;
                for (int i = 0; i < 4; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = 1.6 + r.nextDouble() * 0.8;
                    Vec3 p = at.add(Math.cos(a) * rad, (r.nextDouble() - 0.5) * 2.0, Math.sin(a) * rad);
                    Vec3 v = at.subtract(p).scale(0.12).add(-Math.sin(a) * 0.12, 0, Math.cos(a) * 0.12);
                    level.addParticle(ModParticles.LIGHT_MOTE.get(), p.x, p.y, p.z, v.x, v.y, v.z);
                }
            }
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float t = ctx.time(age);
            float life = t / LIFE;
            float fade = life < 0.15F ? life / 0.15F : 1.0F - Mth.clamp((life - 0.5F) / 0.5F, 0.0F, 1.0F);
            Vec3 c = ctx.rel(at);
            PoseStack.Pose pose = ctx.pose().last();
            VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
            for (int k = 0; k < 3; k++) {
                double spin = t * (0.25 + 0.08 * k) + k * 2.1;
                float size = (1.8F - 0.45F * k) * (1.0F - 0.35F * life);
                Vec3 u = new Vec3(Math.cos(spin), 0.35 * Math.sin(spin * 0.7 + k), Math.sin(spin)).normalize().scale(size);
                Vec3 w = new Vec3(-Math.sin(spin) * 0.35, 1.0, Math.cos(spin) * 0.35).normalize().scale(size);
                FxDraw.quad(pose, ring, c, u, w, FxDraw.argb(0.8F * fade, rgb));
            }
            FxDraw.billboard(pose, ctx.buffers().getBuffer(FxRenderTypes.glowShader()), ctx.camera(), c, 1.6F * (1.0F - 0.4F * life), 0.0F,
                    FxDraw.argb(0.9F * fade, rgb));
            FxDraw.ground(pose, ring, c.add(0, -0.95, 0), 1.2F + t * 0.12F, FxDraw.argb(0.5F * fade, rgb));
        }
    }

    /** Golden clock ring turning backwards around the traveller, twelve hour glints and a thread back to the start. */
    private static final class Rewind implements ClientFx {
        private static final int LIFE = 26;
        private final Vec3 dest;
        private final Vec3 from;
        private int age;

        Rewind(Vec3 dest, Vec3 from) {
            this.dest = dest.add(0, 1.0, 0);
            this.from = from.add(0, 1.0, 0);
        }

        @Override
        public boolean tick() {
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float t = ctx.time(age);
            float fade = 1.0F - Mth.clamp(t / LIFE, 0.0F, 1.0F);
            Vec3 c = ctx.rel(dest);
            PoseStack.Pose pose = ctx.pose().last();
            VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
            FxDraw.ground(pose, ring, c.add(0, -0.95, 0), 1.4F, FxDraw.argb(0.8F * fade, 0xFFD27A));
            FxDraw.ground(pose, ring, c.add(0, -0.94, 0), 1.0F, FxDraw.argb(0.5F * fade, 0xFFF2C8));
            VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
            for (int h = 0; h < 12; h++) {
                double a = -t * 0.35 + h * Mth.TWO_PI / 12.0; // counter-clockwise: time runs back
                Vec3 p = c.add(Math.cos(a) * 1.25, -0.9, Math.sin(a) * 1.25);
                FxDraw.billboard(pose, glow, ctx.camera(), p, h % 3 == 0 ? 0.35F : 0.2F, 0.0F, FxDraw.argb(0.9F * fade, 0xFFD27A));
            }
            if (from.distanceToSqr(dest) < 96 * 96 && from.distanceToSqr(dest) > 1.0) {
                FxDraw.ribbon(pose, ctx.buffers().getBuffer(FxRenderTypes.beam()), ctx.rel(from), c, 0.25F, 0.6F,
                        FxDraw.argb(0.0F, 0xFFD27A), FxDraw.argb(0.8F * fade, 0xFFD27A), 0.0F, (float) from.distanceTo(dest));
            }
        }
    }
}
