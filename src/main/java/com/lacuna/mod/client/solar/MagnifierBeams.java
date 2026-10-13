package com.lacuna.mod.client.solar;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.beam.BeamTrace;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Divine Magnifying Glass beam, drawn by every client for every player using a glass: the ray is recomputed each
 * frame with the server's rules ({@link BeamTrace}: range 24, solid-block clip, first living entity, water surface,
 * x0.5 underwater) and the server's colours (day yellow, night white, underwater blue). Sunlight gathered by the lens
 * converges from the lens (found exactly on the drawn item in first person, {@link HandPose}; at the raised hand
 * otherwise) onto a white-hot point: a shimmering shader cone and core, a lens flare at the glass, and a starburst
 * with sparks and smoke (bubbles underwater) where it burns. Purely visual: damage stays on the server.
 */
public final class MagnifierBeams {
    private static final double MAX_DISTANCE = 128.0;
    /** Sprite pixel at the centre of the lens of the glass texture. */
    private static final float LENS_X = 9.5F;
    private static final float LENS_Y = 6.0F;

    private MagnifierBeams() {}

    static boolean beaming(Player player) {
        return player.isUsingItem() && player.getUseItem().is(ModItems.DIVINE_MAGNIFYING_GLASS.get()) && !player.isSpectator();
    }

    static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.BEAM_ENABLED);
    }

    static boolean firstPerson(Minecraft mc, Player player) {
        return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    // ------------------------------------------------------------------ per frame

    static void render(FxRenderContext ctx) {
        if (!enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float pt = ctx.partialTick();
        for (Player player : ctx.level().players()) {
            if (!beaming(player) || player.position().distanceToSqr(ctx.cam()) > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            BeamTrace trace = BeamTrace.of(ctx.level(), player, player.getEyePosition(pt), player.getViewVector(pt), pt);
            boolean first = firstPerson(mc, player) && player instanceof LocalPlayer;
            Vec3 lens = first ? HandPose.bowPixel((LocalPlayer) player, ctx.camera(), pt, LENS_X, LENS_Y) : handLens(player, pt);
            draw(ctx, player, trace, lens, first);
        }
    }

    /** Third person and other players: the glass held out in the raised hand, in front of the shoulder. */
    static Vec3 handLens(Player player, float pt) {
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
        Vec3 look = player.getViewVector(pt);
        Vec3 shoulder = player.getEyePosition(pt).add(0.0, -0.32, 0.0).add(right.scale(0.36 * HandPose.side(player)));
        return shoulder.add(look.scale(0.85));
    }

    private static void draw(FxRenderContext ctx, Player player, BeamTrace trace, Vec3 lens, boolean firstPerson) {
        PoseStack.Pose pose = ctx.pose().last();
        int rgb = trace.beam().color();
        int hot = mix(rgb, 0xFFFFFF, 0.7F);
        Vec3 a = ctx.rel(lens);
        Vec3 b = ctx.rel(trace.stop());
        float len = (float) lens.distanceTo(trace.stop());
        float t = ctx.time(player.tickCount);
        float shimmer = 0.9F + 0.1F * Mth.sin(t * 1.9F) * Mth.sin(t * 0.7F + 1.0F);
        // the lens is about as wide as the cone where it starts (much closer to the eye in first person)
        float lensWidth = firstPerson ? 0.2F : 0.42F;

        // a faint covering veil keeps the colour readable against a bright daytime sky
        VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
        FxDraw.ribbon(pose, over, a, b, lensWidth * 1.2F, 0.45F, FxDraw.argb(0.16F * shimmer, rgb), FxDraw.argb(0.22F * shimmer, rgb),
                0.0F, len);
        // the cone of gathered light, its body and the white-hot core
        VertexConsumer beam = ctx.buffers().getBuffer(FxRenderTypes.beam());
        FxDraw.ribbon(pose, beam, a, b, lensWidth, 0.3F, FxDraw.argb(0.35F * shimmer, rgb), FxDraw.argb(0.55F * shimmer, rgb), 0.0F, len);
        FxDraw.ribbon(pose, beam, a, b, lensWidth * 0.45F, 0.12F, FxDraw.argb(0.8F, rgb), FxDraw.argb(0.95F * shimmer, rgb), 0.0F, len);
        FxDraw.ribbon(pose, beam, a, b, firstPerson ? 0.018F : 0.03F, 0.045F, FxDraw.argb(1.0F, hot), FxDraw.argb(1.0F, hot), 0.0F, len);

        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), a, (firstPerson ? 0.08F : 0.3F) * (0.95F + 0.1F * shimmer), t * 0.05F,
                FxDraw.argb(0.9F, hot));
        if (trace.hitsSomething()) {
            Vec3 hit = ctx.rel(trace.stop().subtract(trace.look().scale(0.06)));
            VertexConsumer flare = ctx.buffers().getBuffer(SolarRenderTypes.flare());
            FxDraw.billboard(pose, flare, ctx.camera(), hit, 1.1F + 0.12F * Mth.sin(t * 2.3F), t * 0.03F, FxDraw.argb(0.9F * shimmer, rgb));
            glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
            FxDraw.billboard(pose, glow, ctx.camera(), hit, 0.45F + 0.06F * Mth.sin(t * 3.1F), -t * 0.04F, FxDraw.argb(1.0F, hot));
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ per tick

    /** Impact sparks / smoke / bubbles for every beam in view (client particles, nothing sent by the server). */
    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || !enabled() || mc.isPaused()) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource random = level.random;
        for (Player player : level.players()) {
            if (!beaming(player) || player.position().distanceToSqr(cam) > 64.0 * 64.0) {
                continue;
            }
            BeamTrace trace = BeamTrace.of(level, player, player.getEyePosition(), player.getViewVector(1.0F), 1.0F);
            Vec3 hit = trace.stop().subtract(trace.look().scale(0.08));
            if (trace.beam().underwater()) {
                if (trace.hitsSomething()) {
                    level.addParticle(ParticleTypes.BUBBLE, hit.x, hit.y, hit.z, (random.nextDouble() - 0.5) * 0.1, 0.05,
                            (random.nextDouble() - 0.5) * 0.1);
                }
                // bubbles stream along the beam, never right in front of the eyes
                double length = trace.stop().distanceTo(trace.eye());
                if (length > 2.5 && player.tickCount % 2 == 0) {
                    double along = 2.0 + random.nextDouble() * (length - 2.0);
                    Vec3 on = trace.eye().add(trace.look().scale(along));
                    if (on.distanceToSqr(cam) > 9.0) {
                        level.addParticle(ParticleTypes.BUBBLE, on.x, on.y - 0.1, on.z, 0.0, 0.04, 0.0);
                    }
                }
                continue;
            }
            if (!trace.hitsSomething() || trace.waterHit() != null) {
                continue;
            }
            level.addParticle(ModParticles.SOLAR_GLINT.get(), hit.x, hit.y, hit.z, 0.0, 0.0, 0.0);
            if (player.tickCount % 2 == 0) {
                level.addParticle(ParticleTypes.SMOKE, hit.x, hit.y, hit.z, 0.0, 0.03, 0.0);
            }
            if (player.tickCount % 3 == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, hit.x, hit.y, hit.z, (random.nextDouble() - 0.5) * 0.08,
                        random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.08);
            }
            if (random.nextFloat() < 0.5F) {
                level.addParticle(ModParticles.LIGHT_MOTE.get(), hit.x, hit.y, hit.z, (random.nextDouble() - 0.5) * 0.12,
                        random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.12);
            }
        }
    }
}
