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
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Divine Magnifying Glass beam, drawn by every client for every player using a glass: the ray is recomputed each
 * frame with the server's rules ({@link BeamTrace}: range 24, solid-block clip, first living entity, water surface,
 * x0.5 underwater) and the server's colours (day yellow, night white, underwater blue). The ray leaves the lens centre
 * (found exactly on the drawn item: in first person by {@link HandPose}, in third person and for other players by
 * {@link MagnifierLens} on the arm as rendered this frame) and always runs straight along the player's view direction,
 * never bending towards whatever it hits; on a block it ends exactly where that ray meets the face. A shimmering 3D
 * beam ({@link SolarTubes}: veil, cone, inner light and white-hot core, round from every angle; flat shader ribbons
 * without the tube shader), a lens flare at the glass, a starburst with sparks and smoke (bubbles underwater) where it
 * burns, and a short-lived pixel scorch trail on the faces it touches ({@link MagnifierScorch}). Purely visual: damage
 * stays on the server.
 */
public final class MagnifierBeams {
    private static final double MAX_DISTANCE = 128.0;
    /** Sprite pixel at the centre of the lens of the glass texture. */
    static final float LENS_X = 9.5F;
    static final float LENS_Y = 6.0F;

    /** Lens position relative to the eye from the last frame, so per-tick impact particles sit on the drawn beam. */
    private static final java.util.Map<Player, Vec3> LENS_OFFSET = new java.util.WeakHashMap<>();

    private MagnifierBeams() {}

    /** Where the drawn ray ends: from the lens, straight along the view, as deep as the view ray reached. */
    static Vec3 parallelStop(BeamTrace trace, Vec3 lens) {
        double depth = Math.max(0.0, trace.stop().subtract(lens).dot(trace.look()));
        return lens.add(trace.look().scale(depth));
    }

    /** The drawn ray: where it ends, and the block face it touches there (null on a creature, water or thin air). */
    record Ray(Vec3 end, @Nullable BlockHitResult face) {}

    /**
     * The drawn ray from {@code lens}: on a block the view ray hit, exactly where the ray from the lens (parallel to the
     * view, a little off it) meets the surface, so the beam touches the face and its scorch marks lie under it;
     * otherwise as deep as the view ray reached.
     */
    static Ray ray(Level level, Player player, BeamTrace trace, Vec3 lens) {
        Vec3 parallel = parallelStop(trace, lens);
        if (!trace.hitsBlock()) {
            return new Ray(parallel, null);
        }
        BlockHitResult hit = level.clip(new ClipContext(lens, parallel.add(trace.look()), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.isInside()) {
            return new Ray(parallel, null);
        }
        return new Ray(hit.getLocation(), hit);
    }

    /** The lens of {@code player}'s glass in the world (last frame's offset from the eye), for the per-tick effects. */
    static Vec3 lensAt(Player player, Vec3 eye) {
        Vec3 offset = LENS_OFFSET.get(player);
        return offset == null || offset.lengthSqr() > 4.0 ? eye : eye.add(offset);
    }

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
            Vec3 lens = first ? HandPose.bowPixel((LocalPlayer) player, ctx.camera(), pt, LENS_X, LENS_Y) : thirdPersonLens(player, pt);
            LENS_OFFSET.put(player, lens.subtract(player.getEyePosition(pt)));
            draw(ctx, player, trace, lens, first);
        }
        MagnifierScorch.render(ctx);
    }

    /**
     * Third person and other players: the lens centre of the glass as drawn on the arm this frame
     * ({@link MagnifierLens}); the hand estimate while the player is not drawn (off screen).
     */
    static Vec3 thirdPersonLens(Player player, float pt) {
        Vec3 drawn = MagnifierLens.drawn(player);
        return drawn != null ? drawn : handLens(player, pt);
    }

    /** Estimate (player not drawn this frame): the glass held out in the raised hand, in front of the shoulder. */
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
        Ray ray = ray(ctx.level(), player, trace, lens);
        Vec3 end = ray.end();
        scorch(ctx, player, trace, ray);
        Vec3 a = ctx.rel(lens);
        Vec3 b = ctx.rel(end);
        float len = (float) lens.distanceTo(end);
        float t = ctx.time(player.tickCount);
        float shimmer = 0.9F + 0.1F * Mth.sin(t * 1.9F) * Mth.sin(t * 0.7F + 1.0F);
        // the lens is about as wide as the cone where it starts (much closer to the eye in first person)
        float lensWidth = firstPerson ? 0.2F : 0.42F;

        float coreWidth = firstPerson ? 0.018F : 0.03F;
        if (len > 1.0E-3F && SolarTubes.room(4)) {
            // as 3D light (round from every angle, exactly as thick seen from the side as along it): a faint outer veil,
            // the cone of gathered light, its inner light and the white-hot core glowing white in its body. Seen
            // along its length from the eye (first person) a round beam gathers more light than a flat ribbon did: the
            // outer layers are dimmed there; anyone else's beam fades right at the eye (aimed past the camera)
            float k = firstPerson ? 0.65F : 1.0F;
            int flags = firstPerson ? 0 : SolarTubes.EYE_FADE;
            SolarTubes.straight(pose, a, b, lensWidth * 0.6F, 0.225F, FxDraw.argb(0.12F * k * shimmer, rgb),
                    FxDraw.argb(0.16F * k * shimmer, rgb), 0.0F, len, SolarTubes.SUNBEAM, flags);
            SolarTubes.straight(pose, a, b, lensWidth * 0.5F, 0.15F, FxDraw.argb(0.3F * k * shimmer, rgb), FxDraw.argb(0.48F * k * shimmer, rgb),
                    0.0F, len, SolarTubes.BEAM, flags);
            SolarTubes.straight(pose, a, b, lensWidth * 0.225F, 0.06F, FxDraw.argb(0.7F * k, rgb), FxDraw.argb(0.85F * k * shimmer, rgb), 0.0F,
                    len, SolarTubes.BEAM, flags);
            SolarTubes.straight(pose, a, b, coreWidth * 0.5F, 0.0225F, FxDraw.argb(1.0F, hot), FxDraw.argb(1.0F, hot), 0.0F, len,
                    SolarTubes.BEAM, flags, 0.55F, 0.0F, 0.0F);
        } else {
            // a faint outer veil of coloured light
            VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
            FxDraw.ribbon(pose, over, a, b, lensWidth * 1.2F, 0.45F, FxDraw.argb(0.1F * shimmer, rgb), FxDraw.argb(0.14F * shimmer, rgb),
                    0.0F, len);
            // the cone of gathered light, its body and the white-hot core
            VertexConsumer beam = ctx.buffers().getBuffer(FxRenderTypes.beam());
            FxDraw.ribbon(pose, beam, a, b, lensWidth, 0.3F, FxDraw.argb(0.35F * shimmer, rgb), FxDraw.argb(0.55F * shimmer, rgb), 0.0F, len);
            FxDraw.ribbon(pose, beam, a, b, lensWidth * 0.45F, 0.12F, FxDraw.argb(0.8F, rgb), FxDraw.argb(0.95F * shimmer, rgb), 0.0F, len);
            FxDraw.ribbon(pose, beam, a, b, coreWidth, 0.045F, FxDraw.argb(1.0F, hot), FxDraw.argb(1.0F, hot), 0.0F, len);
        }

        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), a, (firstPerson ? 0.08F : 0.3F) * (0.95F + 0.1F * shimmer), t * 0.05F,
                FxDraw.argb(0.9F, hot));
        if (trace.hitsSomething()) {
            Vec3 hit = ctx.rel(end.subtract(trace.look().scale(0.06)));
            VertexConsumer flare = ctx.buffers().getBuffer(SolarRenderTypes.flare());
            FxDraw.billboard(pose, flare, ctx.camera(), hit, 1.1F + 0.12F * Mth.sin(t * 2.3F), t * 0.03F, FxDraw.argb(0.9F * shimmer, rgb));
            glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
            FxDraw.billboard(pose, glow, ctx.camera(), hit, 0.45F + 0.06F * Mth.sin(t * 3.1F), -t * 0.04F, FxDraw.argb(1.0F, hot));
        }
        // a spot of light lying on the ground it burns (top faces, the water surface), following the terrain
        if ((ray.face() != null && ray.face().getDirection() == Direction.UP) || trace.waterHit() != null) {
            Vec3 spot = ray.face() != null ? end : new Vec3(end.x, trace.stop().y, end.z);
            SolarGround.mark(ctx, SolarGround.at(ctx, player, spot, 1.0), ctx.buffers().getBuffer(SolarRenderTypes.ring()), spot,
                    0.8F + 0.06F * Mth.sin(t * 2.3F), SolarRenderTypes.POOL, 0.0F, FxDraw.argb(0.6F * shimmer, rgb));
        }
    }

    /**
     * The burning spot of the drawn beam leaves a short-lived pixel scorch trail on the block faces it sweeps (not
     * underwater, not on creatures): on the face the drawn ray meets, or, if that ray found none, on the face the view
     * ray hit, at the beam's end.
     */
    private static void scorch(FxRenderContext ctx, Player player, BeamTrace trace, Ray ray) {
        Direction face = null;
        Vec3 at = ray.end();
        if (!trace.beam().underwater()) {
            if (ray.face() != null) {
                face = ray.face().getDirection();
            } else if (trace.hitsBlock()) {
                face = trace.blockHit().getDirection();
                Vec3 surface = trace.blockHit().getLocation();
                at = switch (face.getAxis()) {
                    case X -> new Vec3(surface.x, at.y, at.z);
                    case Y -> new Vec3(at.x, surface.y, at.z);
                    case Z -> new Vec3(at.x, at.y, surface.z);
                };
            }
        }
        MagnifierScorch.touch(ctx.level(), player, at, face, ctx.level().random);
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
        MagnifierScorch.tick();
        for (Player player : level.players()) {
            if (!beaming(player) || player.position().distanceToSqr(cam) > 64.0 * 64.0) {
                continue;
            }
            BeamTrace trace = BeamTrace.of(level, player, player.getEyePosition(), player.getViewVector(1.0F), 1.0F);
            Ray ray = ray(level, player, trace, lensAt(player, trace.eye()));
            Vec3 hit = ray.end().subtract(trace.look().scale(0.08));
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
            if (player.tickCount % 9 == 0 && trace.beam().ignites()) {
                // the burning point sizzles and crackles
                level.playLocalSound(hit.x, hit.y, hit.z, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, net.minecraft.sounds.SoundSource.PLAYERS,
                        0.35F, 1.3F + random.nextFloat() * 0.3F, false);
            }
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
