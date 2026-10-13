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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Divine Magnifying Glass beam, drawn by every client for every player using a glass: the ray is recomputed each
 * frame with the server's rules ({@link BeamTrace}: range 24, solid-block clip, first living entity, water surface,
 * x0.5 underwater) and the server's colours (day yellow, night white, underwater blue). Shimmering shader core and
 * halo from the lens (offset to the held item in first person, from the hand otherwise) to the hit point, a lens flare,
 * and an impact glow with sparks and smoke (bubbles underwater). Purely visual: damage stays on the server.
 */
public final class MagnifierBeams {
    private static final double MAX_DISTANCE = 128.0;

    private MagnifierBeams() {}

    static boolean beaming(Player player) {
        return player.isUsingItem() && player.getUseItem().is(ModItems.DIVINE_MAGNIFYING_GLASS.get()) && !player.isSpectator();
    }

    static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.BEAM_ENABLED);
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
            boolean firstPerson = player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            Vec3 lens = firstPerson ? firstPersonLens(player, ctx.camera()) : handLens(player, pt);
            draw(ctx, player, trace, lens, firstPerson);
        }
    }

    private static int side(Player player) {
        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    /** Where the lens of the held glass appears on screen in first person (bow-style raised pose). */
    static Vec3 firstPersonLens(Player player, Camera camera) {
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        int side = side(player);
        return camera.getPosition()
                .add(look.x() * 0.62, look.y() * 0.62, look.z() * 0.62)
                .add(-left.x() * 0.2 * side, -left.y() * 0.2 * side, -left.z() * 0.2 * side)
                .add(-up.x() * 0.16, -up.y() * 0.16, -up.z() * 0.16);
    }

    /** Third person and other players: the glass held out in the raised hand, in front of the shoulder. */
    static Vec3 handLens(Player player, float pt) {
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
        Vec3 look = player.getViewVector(pt);
        Vec3 shoulder = player.getEyePosition(pt).add(0.0, -0.32, 0.0).add(right.scale(0.36 * side(player)));
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
        float w = firstPerson ? 0.7F : 1.0F;

        VertexConsumer beam = ctx.buffers().getBuffer(FxRenderTypes.beam());
        FxDraw.ribbon(pose, beam, a, b, 0.12F * w, 0.6F, FxDraw.argb(0.3F * shimmer, rgb), FxDraw.argb(0.38F * shimmer, rgb), 0.0F, len);
        FxDraw.ribbon(pose, beam, a, b, 0.06F * w, 0.26F, FxDraw.argb(0.85F, rgb), FxDraw.argb(0.85F * shimmer, rgb), 0.0F, len);
        FxDraw.ribbon(pose, beam, a, b, 0.025F * w, 0.09F, FxDraw.argb(1.0F, hot), FxDraw.argb(1.0F, hot), 0.0F, len);

        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), a, (firstPerson ? 0.09F : 0.3F) * (0.95F + 0.1F * shimmer), t * 0.05F,
                FxDraw.argb(0.95F, hot));
        if (trace.hitsSomething()) {
            Vec3 hit = ctx.rel(trace.stop().subtract(trace.look().scale(0.05)));
            FxDraw.billboard(pose, glow, ctx.camera(), hit, 0.5F + 0.08F * Mth.sin(t * 2.3F), -t * 0.04F, FxDraw.argb(1.0F, hot));
            FxDraw.billboard(pose, glow, ctx.camera(), hit, 1.3F, t * 0.02F, FxDraw.argb(0.35F * shimmer, rgb));
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
                level.addParticle(ParticleTypes.BUBBLE, hit.x, hit.y, hit.z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
                double f = random.nextDouble();
                Vec3 on = trace.eye().add(trace.stop().subtract(trace.eye()).scale(f));
                level.addParticle(ParticleTypes.BUBBLE, on.x, on.y, on.z, 0.0, 0.04, 0.0);
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
            if (random.nextFloat() < 0.4F) {
                level.addParticle(ModParticles.LIGHT_MOTE.get(), hit.x, hit.y, hit.z, (random.nextDouble() - 0.5) * 0.1,
                        random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.1);
            }
        }
    }
}
