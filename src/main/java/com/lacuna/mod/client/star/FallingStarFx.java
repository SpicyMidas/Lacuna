package com.lacuna.mod.client.star;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.GroundDecal;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.star.FallingStarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Everything seen and heard of a falling star until it strikes:
 * <ul>
 *   <li>while it appears: a new star twinkling into being high in the sky, and a faint ring on the ground where it
 *       will fall (under the locked-on creature, following it);</li>
 *   <li>while it falls: a white-hot core in a gold halo with a turning star flare, a long tapering trail of light, a
 *       shower of sparks, flames and smoke, a whistle that swells and drops in pitch, and the sky warming slightly as
 *       it nears;</li>
 *   <li>once it has struck, the trail is drawn into the impact point and fades.</li>
 * </ul>
 * Sizes grow with distance so the star stays visible from far away.
 */
final class FallingStarFx implements ClientFx {
    static final ResourceLocation FLARE = Lacuna.id("textures/effect/star_flare.png");
    static final int WHITE_HOT = 0xFFF8E6;
    static final int GOLD = 0xFFD47A;
    static final int AMBER = 0xFF9A3C;
    static final int EMBER = 0xD0441A;
    static final int STARLIGHT = 0xD9CCFF;
    private static final int TRAIL = 16;

    private final FallingStarEntity star;
    private final int id;
    /** Recent positions, newest first (the newest is the star's current tick position). */
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private Vec3 last;
    @Nullable
    private Vec3 landed;
    /** The terrain the landing marker lies on. */
    private final GroundDecal decal = new GroundDecal();
    private int age;
    private int gone = -1;
    private boolean fell;

    FallingStarFx(FallingStarEntity star) {
        this.star = star;
        this.id = star.getId();
        this.last = star.position();
    }

    /** The server reported the impact point: the trail ends there. */
    void land(Vec3 at) {
        landed = at;
    }

    private boolean alive(ClientLevel level) {
        return !star.isRemoved() && star.level() == level;
    }

    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            StarClient.STARS.remove(id, this);
            return false;
        }
        age++;
        if (gone < 0 && alive(level)) {
            Vec3 p = star.position();
            if (star.isFalling()) {
                if (!fell) {
                    fell = true;
                    startFall(mc, p);
                }
                fallTick(level, p);
                trail.addFirst(p);
                while (trail.size() > TRAIL) {
                    trail.removeLast();
                }
            } else if (level.random.nextFloat() < 0.5F) {
                RandomSource r = level.random;
                level.addAlwaysVisibleParticle(ModParticles.SOLAR_GLINT.get(), true, p.x + r.nextGaussian() * 0.6,
                        p.y + r.nextGaussian() * 0.6, p.z + r.nextGaussian() * 0.6, 0.0, 0.0, 0.0);
            }
            last = p;
            return true;
        }
        if (gone < 0) {
            gone = 0;
            if (landed != null) {
                trail.addFirst(landed);
            }
        }
        gone++;
        // the trail is drawn into the impact point
        for (int i = 0; i < 3 && trail.size() > 1; i++) {
            trail.removeLast();
        }
        boolean more = gone < 6 && trail.size() > 1;
        if (!more) {
            StarClient.STARS.remove(id, this);
        }
        return more;
    }

    private void startFall(Minecraft mc, Vec3 p) {
        mc.getSoundManager().play(new StarWhistleSound(star, SoundEvents.ELYTRA_FLYING, 2.0F, 0.9F, 1.0F));
        mc.getSoundManager().play(new StarWhistleSound(star, SoundEvents.BEACON_AMBIENT, 2.0F, 1.4F, 0.6F));
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(p);
        if (d < 160.0) {
            ScreenFx.flash(0xEEF0FF, 0.1F * (float) (1.0 - d / 160.0), 10);
        }
    }

    private void fallTick(ClientLevel level, Vec3 p) {
        RandomSource r = level.random;
        Vec3 prev = trail.isEmpty() ? p : trail.peekFirst();
        Vec3 v = p.subtract(prev);
        int steps = 3;
        for (int i = 0; i < steps; i++) {
            Vec3 at = prev.add(v.scale((i + 1) / (double) steps));
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, at.x + r.nextGaussian() * 0.12, at.y + r.nextGaussian() * 0.12,
                    at.z + r.nextGaussian() * 0.12, -v.x * 0.03 + r.nextGaussian() * 0.02, -v.y * 0.03 + r.nextGaussian() * 0.02,
                    -v.z * 0.03 + r.nextGaussian() * 0.02);
            level.addAlwaysVisibleParticle(ModParticles.LIGHT_MOTE.get(), true, at.x + r.nextGaussian() * 0.15,
                    at.y + r.nextGaussian() * 0.15, at.z + r.nextGaussian() * 0.15, r.nextGaussian() * 0.02, r.nextGaussian() * 0.02,
                    r.nextGaussian() * 0.02);
        }
        if (r.nextFloat() < 0.7F) {
            level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, p.x, p.y, p.z, r.nextGaussian() * 0.12,
                    r.nextGaussian() * 0.12, r.nextGaussian() * 0.12);
        }
        if (r.nextFloat() < 0.6F) {
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, p.x - v.x * 0.5, p.y - v.y * 0.5, p.z - v.z * 0.5,
                    r.nextGaussian() * 0.03, r.nextGaussian() * 0.03, r.nextGaussian() * 0.03);
        }
        if (age % 2 == 0) {
            level.addAlwaysVisibleParticle(ModParticles.SOLAR_GLINT.get(), true, p.x, p.y, p.z, 0.0, 0.0, 0.0);
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, p.x - v.x, p.y - v.y, p.z - v.z, 0.0, 0.02, 0.0);
        }
        // the sky warms as the star comes near
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double near = Mth.clamp(1.0 - cam.distanceTo(p) / 96.0, 0.0, 1.0);
        if (near > 0.0) {
            ScreenFx.hold("starfall", 0xFFD9A0, (float) (0.16 * near * (0.3 + 0.7 * star.fallProgress(0.0F))), false);
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Size factor keeping the star visible at a distance. */
    private static float distanceScale(Vec3 rel) {
        return 1.0F + (float) rel.length() / 40.0F;
    }

    @Override
    public void render(FxRenderContext ctx) {
        float pt = ctx.partialTick();
        boolean alive = gone < 0 && alive(ctx.level());
        Vec3 headWorld = alive ? star.getPosition(pt) : landed != null ? landed : last;
        Vec3 head = ctx.rel(headWorld);
        float s = distanceScale(head);
        float time = ctx.time(age);
        PoseStack.Pose pose = ctx.pose().last();
        if (alive) {
            marker(ctx, pose, time);
        }
        if (alive && !star.isFalling()) {
            appear(ctx, pose, head, s, star.appearProgress(pt), time);
            return;
        }
        float fade = gone < 0 ? 1.0F : Mth.clamp(1.0F - (gone - 1 + pt) / 5.0F, 0.0F, 1.0F);
        trail(ctx, pose, head, s, fade);
        if (gone < 0) {
            core(ctx, pose, head, s, time, 1.0F);
        }
    }

    /** A new star twinkling into being: a point of light that swells into a turning flare. */
    private void appear(FxRenderContext ctx, PoseStack.Pose pose, Vec3 head, float s, float f, float time) {
        float e = f * f * (3.0F - 2.0F * f);
        float twinkle = 0.75F + 0.25F * Mth.sin(time * 1.7F);
        VertexConsumer halo = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        FxDraw.billboard(pose, halo, ctx.camera(), head, (0.4F + 1.4F * e) * s, 0.0F, FxDraw.argb(0.45F * e * twinkle, GOLD));
        VertexConsumer flare = ctx.buffers().getBuffer(FxRenderTypes.additive(FLARE));
        FxDraw.billboard(pose, flare, ctx.camera(), head, (0.3F + 1.6F * e) * s, time * 0.12F, FxDraw.argb(e * twinkle, WHITE_HOT));
        FxDraw.billboard(pose, flare, ctx.camera(), head, (0.2F + 1.0F * e) * s, -time * 0.08F + 0.4F,
                FxDraw.argb(0.6F * e, STARLIGHT));
        VertexConsumer core = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, core, ctx.camera(), head, (0.2F + 0.4F * e) * s, 0.0F, FxDraw.argb(e, WHITE_HOT));
    }

    /** The head of the falling star: halo, core and a turning star flare. */
    private void core(FxRenderContext ctx, PoseStack.Pose pose, Vec3 head, float s, float time, float alpha) {
        float flicker = 0.9F + 0.1F * Mth.sin(time * 3.1F) * Mth.sin(time * 1.3F + 0.5F);
        VertexConsumer halo = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        FxDraw.billboard(pose, halo, ctx.camera(), head, 2.2F * s, 0.0F, FxDraw.argb(0.4F * alpha * flicker, AMBER));
        FxDraw.billboard(pose, halo, ctx.camera(), head, 1.2F * s, 0.0F, FxDraw.argb(0.6F * alpha * flicker, GOLD));
        VertexConsumer flare = ctx.buffers().getBuffer(FxRenderTypes.additive(FLARE));
        FxDraw.billboard(pose, flare, ctx.camera(), head, 1.5F * s, time * 0.15F, FxDraw.argb(0.85F * alpha, WHITE_HOT));
        FxDraw.billboard(pose, flare, ctx.camera(), head, 1.0F * s, -time * 0.1F + 0.4F, FxDraw.argb(0.5F * alpha, STARLIGHT));
        VertexConsumer core = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, core, ctx.camera(), head, 0.7F * s, 0.0F, FxDraw.argb(alpha, WHITE_HOT));
    }

    /** The trail: a wide amber glow around a white-hot beam, both tapering to nothing along the recent path. */
    private void trail(FxRenderContext ctx, PoseStack.Pose pose, Vec3 head, float s, float fade) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(head);
        Iterator<Vec3> it = trail.iterator();
        if (it.hasNext()) {
            it.next(); // the newest entry is the current tick position, ahead of the interpolated head
        }
        while (it.hasNext()) {
            pts.add(ctx.rel(it.next()));
        }
        int n = pts.size();
        if (n < 2) {
            return;
        }
        // one render type at a time: the shared buffer source ends a batch when another type is requested
        VertexConsumer outer = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
        float along = 0.0F;
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = pts.get(i);
            Vec3 b = pts.get(i + 1);
            float ta = i / (float) (n - 1);
            float tb = (i + 1) / (float) (n - 1);
            float len = (float) a.distanceTo(b);
            FxDraw.ribbon(pose, outer, a, b, 1.6F * s * (0.25F + 0.75F * taper(ta)), 1.6F * s * (0.25F + 0.75F * taper(tb)),
                    FxDraw.argb(0.55F * fade * (1.0F - ta), mix(GOLD, EMBER, ta)), FxDraw.argb(0.55F * fade * (1.0F - tb), mix(GOLD, EMBER, tb)),
                    along, along + len);
            along += len;
        }
        VertexConsumer inner = ctx.buffers().getBuffer(FxRenderTypes.beam());
        along = 0.0F;
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = pts.get(i);
            Vec3 b = pts.get(i + 1);
            float ta = i / (float) (n - 1);
            float tb = (i + 1) / (float) (n - 1);
            float len = (float) a.distanceTo(b);
            FxDraw.ribbon(pose, inner, a, b, 0.55F * s * taper(ta) + 0.02F, 0.55F * s * taper(tb) + 0.02F,
                    FxDraw.argb(fade * (1.0F - ta), mix(WHITE_HOT, AMBER, ta)), FxDraw.argb(fade * (1.0F - tb), mix(WHITE_HOT, AMBER, tb)),
                    along, along + len);
            along += len;
        }
    }

    private static float taper(float t) {
        return (1.0F - t) * (1.0F - t);
    }

    /** A faint ring where the star will strike, pulsing faster and brighter as it comes. */
    private void marker(FxRenderContext ctx, PoseStack.Pose pose, float time) {
        Vec3 ground;
        Entity target = star.targetId() >= 0 ? ctx.level().getEntity(star.targetId()) : null;
        if (target != null) {
            ground = target.getPosition(ctx.partialTick());
        } else {
            ground = star.aim();
        }
        float p = star.isFalling() ? star.fallProgress(ctx.partialTick()) : 0.0F;
        float a = star.isFalling() ? 0.25F + 0.5F * p : 0.25F * star.appearProgress(ctx.partialTick());
        float pulse = 0.5F + 0.5F * Mth.sin(time * (0.3F + 0.9F * p));
        VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
        GroundDecal g = decal.at(ctx.level(), ground, 2.2);
        g.square(ctx, ring, ground.x, ground.z, 1.9F + 0.25F * pulse, 0.006F, FxDraw.argb(a * (0.6F + 0.4F * pulse), AMBER));
        g.square(ctx, ring, ground.x, ground.z, 1.0F + 0.8F * (1.0F - p), 0.012F, FxDraw.argb(a * 0.7F, GOLD));
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        g.square(ctx, glow, ground.x, ground.z, 1.6F, 0.0F, FxDraw.argb(a * 0.35F, EMBER));
    }

    static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
