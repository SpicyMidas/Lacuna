package com.lacuna.mod.client.gear;

import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.gear.GravemawGreatswordItem;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Gravemaw Greatsword visuals (client): shadow gathering onto the blade and a growing ground aura while any player
 * charges, a flare when the charge is ready, and the crescent wave - a curved wall of umbral energy with a glowing
 * crest that sweeps outward, tearing up the ground under it.
 */
public final class GravemawFx {
    private static final String FEATURE = "gravemaw_fx";
    private static final int VIOLET = 0x9B5CFF;
    private static final int PALE = 0xE6D2FF;

    private GravemawFx() {}

    public static void register() {
        FxHandlers.register("gravemaw_ready", GravemawFx::ready);
        FxHandlers.register("gravemaw_wave", p -> FxManager.add(new Wave(p)));
    }

    private static boolean charging(Player p) {
        return p.isUsingItem() && p.getUseItem().getItem() instanceof GravemawGreatswordItem;
    }

    private static float chargeOf(Player p, float partial) {
        return Mth.clamp((p.getTicksUsingItem() + partial) / 40.0F, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ charging
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            RandomSource r = level.random;
            for (Player p : level.players()) {
                if (!charging(p)) {
                    continue;
                }
                float f = chargeOf(p, 0.0F);
                Vec3 blade = p.position().add(0, 1.3, 0).add(p.getLookAngle().multiply(1, 0, 1).scale(0.5));
                int n = 2 + (int) (f * 3);
                for (int i = 0; i < n; i++) {
                    // violet wisps start on a shell around the blade and stream into it
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    Vec3 start = blade.add(dir.scale(1.8 + r.nextDouble()));
                    Vec3 v = blade.subtract(start).scale(0.11);
                    level.addParticle(ModParticles.UMBRAL_WISP.get(), start.x, start.y, start.z, v.x, v.y, v.z);
                }
                if (r.nextInt(4) == 0) {
                    Vec3 start = blade.add(r.nextGaussian() * 0.8, r.nextGaussian() * 0.5, r.nextGaussian() * 0.8);
                    level.addParticle(ModParticles.SHADOW_MOTE.get(), start.x, start.y, start.z, 0.0, 0.01, 0.0);
                }
                if (f >= 0.5F && r.nextFloat() < f) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double rad = 0.8 + 2.2 * f;
                    level.addParticle(ModParticles.UMBRAL_SHARD.get(), p.getX() + Math.cos(a) * rad, p.getY() + 0.1,
                            p.getZ() + Math.sin(a) * rad, 0.0, 0.08 + r.nextDouble() * 0.08, 0.0);
                }
            }
        });
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        PoseStack pose = event.getPoseStack();
        if (level == null || pose == null) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 cam = event.getCamera().getPosition();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            boolean any = false;
            for (Player p : level.players()) {
                if (!charging(p)) {
                    continue;
                }
                any = true;
                float f = chargeOf(p, partial);
                float t = (level.getGameTime() + partial) * 0.25F;
                float pulse = f >= 1.0F ? 0.75F + 0.25F * Mth.sin(t * 2.0F) : 1.0F;
                Vec3 feet = p.getPosition(partial).add(0, 0.04, 0).subtract(cam);
                VertexConsumer ring = buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
                FxDraw.ground(pose.last(), ring, feet, 0.9F + 2.4F * f, FxDraw.argb((0.25F + 0.55F * f) * pulse, VIOLET));
                FxDraw.ground(pose.last(), ring, feet.add(0, 0.01, 0), (0.6F + 1.4F * f) * (0.9F + 0.1F * Mth.sin(t)),
                        FxDraw.argb(0.3F * f * pulse, PALE));
                VertexConsumer glow = buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
                FxDraw.ground(pose.last(), glow, feet, 1.2F + 2.0F * f, FxDraw.argb(0.18F * f * pulse, VIOLET));
            }
            if (any) {
                buffers.endBatch();
                RenderSystem.defaultBlendFunc();
            }
        });
    }

    private static void ready(FxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        boolean full = p.p0() >= 1.0F;
        Entity e = mc.level.getEntity(p.entityId());
        Vec3 at = e != null ? e.position().add(0, 1.2, 0) : p.a().add(0, 1.2, 0);
        for (int i = 0; i < (full ? 24 : 12); i++) {
            double a = i * Mth.TWO_PI / (full ? 24 : 12);
            mc.level.addParticle(ModParticles.UMBRAL_WISP.get(), at.x, at.y, at.z, Math.cos(a) * 0.22, 0.02, Math.sin(a) * 0.22);
        }
        if (mc.player != null && mc.player.getId() == p.entityId()) {
            ScreenFx.flash(VIOLET, full ? 0.18F : 0.1F, 6);
        }
    }

    // ------------------------------------------------------------------ the crescent wave
    private static final class Wave implements ClientFx {
        private static final int LIFE = 12;
        private final Vec3 origin;
        private final double yaw;
        private final float strength;
        private final float range;
        private final float half;
        private int age;

        Wave(FxPayload p) {
            this.origin = p.a();
            Vec3 f = p.b().subtract(p.a());
            this.yaw = Math.atan2(f.z, f.x);
            this.strength = Mth.clamp(p.p0(), 0.0F, 1.0F);
            this.range = p.p1() > 0 ? p.p1() : 8.0F;
            this.half = (float) Math.toRadians(p.p2() > 0 ? p.p2() : 35.0F);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == p.entityId()) {
                ScreenFx.shake(2.0F + 2.0F * strength, 12);
            }
        }

        private float radius(float time) {
            float t = Mth.clamp(time / 7.0F, 0.0F, 1.0F);
            return 1.0F + (range - 1.0F) * (1.0F - (1.0F - t) * (1.0F - t));
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && age <= 7) {
                RandomSource r = level.random;
                float rad = radius(age);
                for (int i = 0; i < 7; i++) {
                    double a = yaw + (r.nextDouble() * 2.0 - 1.0) * half;
                    double x = origin.x + Math.cos(a) * rad;
                    double z = origin.z + Math.sin(a) * rad;
                    BlockPos ground = BlockPos.containing(x, origin.y - 0.5, z);
                    BlockState state = level.getBlockState(ground);
                    if (!state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE) {
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, origin.y + 0.1, z,
                                (r.nextDouble() - 0.5) * 0.2, 0.25 + r.nextDouble() * 0.25, (r.nextDouble() - 0.5) * 0.2);
                    }
                    if (r.nextFloat() < 0.6F) {
                        level.addParticle(ModParticles.UMBRAL_SHARD.get(), x, origin.y + 0.3 + r.nextDouble() * 1.5, z,
                                Math.cos(a) * 0.15, 0.1, Math.sin(a) * 0.15);
                    }
                }
            }
            return ++age < LIFE;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float fade = 1.0F - Mth.clamp((time - 6.0F) / (LIFE - 6.0F), 0.0F, 1.0F);
            if (fade <= 0.0F) {
                return;
            }
            float height = 1.4F + 1.4F * strength;
            Vec3 base = ctx.rel(origin).add(0, 0.05, 0);
            PoseStack.Pose pose = ctx.pose().last();
            // two fading echoes behind the front give the wave its motion
            for (int echo = 2; echo >= 0; echo--) {
                float r = radius(time - echo * 0.9F);
                float a = fade * (echo == 0 ? 1.0F : 0.35F / echo);
                // dark body (two layers for depth), violet glow through it, a bright violet crest and a thin pale edge
                VertexConsumer dark = ctx.buffers().getBuffer(FxRenderTypes.shade(FxRenderTypes.BEAM));
                band(pose, dark, base, r, 0.0F, height, FxDraw.argb(a, 0x0A0512));
                band(pose, dark, base, r - 0.3F, 0.0F, height * 0.85F, FxDraw.argb(0.8F * a, 0x160A26));
                VertexConsumer light = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
                band(pose, light, base, r + 0.05F, 0.0F, height, FxDraw.argb(0.45F * a, VIOLET));
                band(pose, light, base, r + 0.1F, height * 0.72F, height * 1.04F, FxDraw.argb(0.85F * a, 0xB27CFF));
                band(pose, light, base, r + 0.12F, height * 0.9F, height * 1.02F, FxDraw.argb(0.6F * a, PALE));
            }
            // a short shockwave ring where the blade struck the ground
            float ringFade = 1.0F - Mth.clamp(time / 6.0F, 0.0F, 1.0F);
            if (ringFade > 0.0F) {
                FxDraw.ground(pose, ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING)), base.add(0, 0.02, 0),
                        1.0F + time * 0.45F, FxDraw.argb(0.7F * ringFade, VIOLET));
            }
        }

        /** A vertical band along the arc at radius r, from y0 to y1 above the base; u runs bottom (0) to top (1). */
        private void band(PoseStack.Pose pose, VertexConsumer vc, Vec3 base, float r, float y0, float y1, int argb) {
            int segments = 16;
            int alpha = (argb >>> 24) & 0xFF;
            for (int i = 0; i < segments; i++) {
                float s0 = (float) i / segments;
                float s1 = (float) (i + 1) / segments;
                double a0 = yaw + (s0 * 2.0 - 1.0) * half;
                double a1 = yaw + (s1 * 2.0 - 1.0) * half;
                // ends taper (lower and fainter) so the crescent has points
                float t0 = Mth.sin(s0 * Mth.PI);
                float t1 = Mth.sin(s1 * Mth.PI);
                int c0 = ((int) (alpha * t0) << 24) | (argb & 0xFFFFFF);
                int c1 = ((int) (alpha * t1) << 24) | (argb & 0xFFFFFF);
                double x0 = base.x + Math.cos(a0) * r;
                double z0 = base.z + Math.sin(a0) * r;
                double x1 = base.x + Math.cos(a1) * r;
                double z1 = base.z + Math.sin(a1) * r;
                float top0 = y0 + (y1 - y0) * (0.35F + 0.65F * t0);
                float top1 = y0 + (y1 - y0) * (0.35F + 0.65F * t1);
                vc.addVertex(pose, (float) x0, (float) (base.y + y0), (float) z0).setUv(0.0F, 0.0F).setColor(c0);
                vc.addVertex(pose, (float) x0, (float) (base.y + top0), (float) z0).setUv(1.0F, 0.0F).setColor(c0);
                vc.addVertex(pose, (float) x1, (float) (base.y + top1), (float) z1).setUv(1.0F, 1.0F).setColor(c1);
                vc.addVertex(pose, (float) x1, (float) (base.y + y0), (float) z1).setUv(0.0F, 1.0F).setColor(c1);
            }
        }
    }
}
