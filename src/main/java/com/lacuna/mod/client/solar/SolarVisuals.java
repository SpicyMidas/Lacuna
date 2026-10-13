package com.lacuna.mod.client.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.solar.SolarRayEntity;
import com.lacuna.mod.solar.SunsFuryEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The light of Sun's Fury, drawn after particles (so it also works under Fabulous graphics) for every spear / column
 * whose entity renderer ran this frame (vanilla culling decides what is visible). Everything is GPU shader light
 * ({@link SolarRenderTypes}): camera-facing ribbons read as volumetric shafts, flat procedural marks as rings and pools.
 * <ul>
 *   <li>flight: a tapering golden comet trail and a starburst on the spear head;</li>
 *   <li>focus: a wide cone of sunlight leaning toward the climbing sun, soft sunbeams that spiral in from a vast ring
 *       high above and converge on the spear (a magnifying glass gathering sunlight), a thin focus beam that forms in
 *       the last moments, a turning target reticle that shrinks and brightens, pull-in rings, a ground light pool and
 *       the spear's own swelling glow;</li>
 *   <li>strike: a sky-to-ground pillar of five layered column-shader ribbons (amber haze, golden halo, striated body,
 *       pale inner light, white-hot core), rings of light pouring down it, a glowing aperture where it leaves the sky,
 *       an impact starburst, a dome of light and a molten light pool on the ground, and shockwaves racing outward;</li>
 *   <li>fade: the pillar thins into a golden thread and its light drains away (the motes are spawned by the entity).</li>
 * </ul>
 * Well under a few hundred quads per effect.
 */
public final class SolarVisuals {
    private static final List<Entity> QUEUE = new ArrayList<>();

    static final int AMBER = 0xFF9A2A;
    static final int GOLD = 0xFFC24A;
    static final int WARM = 0xFFD98A;
    static final int PALE = 0xFFEFC4;
    static final int WHITE = 0xFFFFF4;

    private SolarVisuals() {}

    /** Called by the entity renderers: draw this entity's light in the after-particles pass of this frame. */
    static void queue(Entity entity) {
        if (!QUEUE.contains(entity) && QUEUE.size() < 64) {
            QUEUE.add(entity);
        }
    }

    static void clear() {
        QUEUE.clear();
    }

    static void render(FxRenderContext ctx) {
        try {
            for (Entity e : QUEUE) {
                if (e.isRemoved()) {
                    continue;
                }
                if (e instanceof SunsFuryEntity spear) {
                    if (spear.isFocusing()) {
                        focus(spear, ctx);
                    } else {
                        flight(spear, ctx);
                    }
                } else if (e instanceof SolarRayEntity ray) {
                    column(ray, ctx);
                }
            }
        } finally {
            QUEUE.clear();
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    /** A flat {@code solar_ring} mark of half-size {@code half} centred at {@code c} in the plane of unit {@code e1}, {@code e2}. */
    static void mark(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, Vec3 e1, Vec3 e2, float half, int mode, int argb) {
        float u0 = mode * 2.0F;
        Vec3 a = e1.scale(half);
        Vec3 b = e2.scale(half);
        vertex(vc, pose, c.subtract(a).subtract(b), u0, 0.0F, argb);
        vertex(vc, pose, c.subtract(a).add(b), u0, 1.0F, argb);
        vertex(vc, pose, c.add(a).add(b), u0 + 1.0F, 1.0F, argb);
        vertex(vc, pose, c.add(a).subtract(b), u0 + 1.0F, 0.0F, argb);
    }

    /** Horizontal mark (ground rings, pools). */
    static void groundMark(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, float half, int mode, int argb) {
        mark(pose, vc, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), half, mode, argb);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int argb) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(argb);
    }

    /**
     * Camera-facing {@code solar_column} ribbon in the soft sunbeam profile (u 2..3: no white core line), from
     * {@code a} to {@code b} (camera-relative), v from {@code vA} to {@code vB}.
     */
    static void sunbeam(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, float widthA, float widthB, int argbA, int argbB,
                        float vA, float vB) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 sideA = facing(dir, a).scale(widthA * 0.5);
        Vec3 sideB = facing(dir, b).scale(widthB * 0.5);
        vertex(vc, pose, a.subtract(sideA), 2.0F, vA, argbA);
        vertex(vc, pose, a.add(sideA), 3.0F, vA, argbA);
        vertex(vc, pose, b.add(sideB), 3.0F, vB, argbB);
        vertex(vc, pose, b.subtract(sideB), 2.0F, vB, argbB);
    }

    /**
     * Camera-facing strip through camera-relative {@code points} (shared edges between segments, so no seams), with a
     * width, ARGB colour and v per point; u runs {@code u0}..{@code u0 + 1} across ({@code u0 = 2}: soft sunbeam).
     */
    static void strip(PoseStack.Pose pose, VertexConsumer vc, List<Vec3> points, float[] width, int[] argb, float[] v, int u0) {
        int n = points.size();
        Vec3[] side = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 tangent = points.get(Math.min(n - 1, i + 1)).subtract(points.get(Math.max(0, i - 1)));
            if (tangent.lengthSqr() < 1.0E-8) {
                tangent = new Vec3(0, 1, 0);
            }
            side[i] = facing(tangent, points.get(i)).scale(width[i] * 0.5);
        }
        for (int i = 0; i < n - 1; i++) {
            vertex(vc, pose, points.get(i).subtract(side[i]), u0, v[i], argb[i]);
            vertex(vc, pose, points.get(i).add(side[i]), u0 + 1.0F, v[i], argb[i]);
            vertex(vc, pose, points.get(i + 1).add(side[i + 1]), u0 + 1.0F, v[i + 1], argb[i + 1]);
            vertex(vc, pose, points.get(i + 1).subtract(side[i + 1]), u0, v[i + 1], argb[i + 1]);
        }
    }

    private static Vec3 facing(Vec3 dir, Vec3 at) {
        Vec3 s = dir.cross(at);
        if (s.lengthSqr() < 1.0E-8) {
            s = dir.cross(Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        return s.normalize();
    }

    // ------------------------------------------------------------------ flight

    private static void flight(SunsFuryEntity spear, FxRenderContext ctx) {
        PoseStack.Pose pose = ctx.pose().last();
        float pt = ctx.partialTick();
        Vec3 head = spear.getPosition(pt);
        if (SunsFuryRenderer.tooCloseToShow(spear, ctx.cam())) {
            return;
        }
        // the trail streams from the butt so the spear itself stays visible; the first trail points sit at the
        // thrower's eyes: keep them off their screen
        Vec3 dir = spear.pointing(pt);
        Vec3 butt = head.subtract(dir.scale(0.8));
        List<Vec3> points = new ArrayList<>();
        for (Vec3 p : spear.trail) {
            if (p.distanceToSqr(ctx.cam()) > 4.0 && p.subtract(butt).dot(dir) < -0.05) {
                points.add(p);
            }
        }
        points.add(butt);
        int n = points.size();
        float t = ctx.time(spear.tickCount);
        if (n >= 2) {
            // one continuous strip per layer from the butt (index 0) to the tail: shared edges, no seams
            List<Vec3> rel = new ArrayList<>(n);
            float[] v = new float[n];
            float[] k = new float[n];
            for (int i = 0; i < n; i++) {
                rel.add(ctx.rel(points.get(n - 1 - i)));
                v[i] = i == 0 ? 0.0F : v[i - 1] + (float) rel.get(i).distanceTo(rel.get(i - 1));
                k[i] = 1.0F - (float) i / (n - 1);
            }
            float[] w = new float[n];
            int[] c = new int[n];
            VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
            // the wide glow swells in just behind the spear instead of starting with a hard edge
            for (int i = 0; i < n; i++) {
                float wk = wake(k[i]);
                w[i] = 2.2F * wk + 0.1F;
                c[i] = FxDraw.argb(0.45F * wk * Math.min(1.0F, 1.6F * k[i]), AMBER);
            }
            strip(pose, over, rel, w, c, v, 0);
            VertexConsumer beam = ctx.buffers().getBuffer(SolarRenderTypes.column());
            for (int i = 0; i < n; i++) {
                float wk = wake(k[i]);
                w[i] = 2.6F * wk + 0.1F;
                c[i] = FxDraw.argb(0.5F * wk * Math.min(1.0F, 1.6F * k[i]), GOLD);
            }
            strip(pose, beam, rel, w, c, v, 2);
            for (int i = 0; i < n; i++) {
                w[i] = 1.1F * k[i] + 0.05F;
                c[i] = FxDraw.argb(0.85F * k[i] * k[i], GOLD);
            }
            strip(pose, beam, rel, w, c, v, 0);
            for (int i = 0; i < n; i++) {
                w[i] = 0.36F * k[i] + 0.02F;
                c[i] = FxDraw.argb(k[i] * k[i], WHITE);
            }
            strip(pose, beam, rel, w, c, v, 0);
        }
        // the head of the spear blazes like a small sun
        Vec3 tip = ctx.rel(head.add(dir.scale(0.75)));
        VertexConsumer flare = ctx.buffers().getBuffer(SolarRenderTypes.flare());
        FxDraw.billboard(pose, flare, ctx.camera(), tip, 1.15F + 0.12F * Mth.sin(t * 0.9F), t * 0.04F, FxDraw.argb(0.85F, GOLD));
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), tip, 0.4F, -t * 0.05F, FxDraw.argb(0.9F, PALE));
    }

    /** Trail glow profile along the trail ({@code t} = 1 at the spear, 0 at the tail): swells in, then tapers away. */
    private static float wake(float t) {
        float s = 1.0F - t;
        return t * (float) SolarMath.ease(s / 0.3);
    }

    // ------------------------------------------------------------------ focus

    private static void focus(SunsFuryEntity spear, FxRenderContext ctx) {
        PoseStack.Pose pose = ctx.pose().last();
        float pt = ctx.partialTick();
        double p = spear.focusProgress(pt);
        float e = (float) SolarMath.ease(p);
        float t = ctx.time(spear.tickCount);
        Vec3 target = spear.getPosition(pt);
        Vec3 axis = lightAxis(ctx.level(), pt);
        Vec3 e1 = axis.cross(Math.abs(axis.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 e2 = axis.cross(e1).normalize();
        Vec3 rt = ctx.rel(target);

        // 1. a vast faint cone of gathered sunlight narrowing onto the target (covers: reads by day and by night)
        float coneHeight = 170.0F;
        VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
        FxDraw.ribbon(pose, over, ctx.rel(target.add(axis.scale(coneHeight))), rt, Mth.lerp(e, 64.0F, 10.0F), Mth.lerp(e, 20.0F, 1.6F),
                FxDraw.argb(0.04F + 0.1F * e, AMBER), FxDraw.argb(0.1F + 0.32F * e, GOLD), coneHeight, 0.0F);

        // 2. soft sunbeams spiralling in from a ring high above and converging on the target
        int shafts = 7;
        float ringRadius = Mth.lerp(e, 42.0F, 2.5F);
        float shaftHeight = Mth.lerp(e, 75.0F, 120.0F);
        float spin = t * 0.008F + e * 1.4F;
        Vec3[] starts = new Vec3[shafts];
        float[] flicker = new float[shafts];
        for (int i = 0; i < shafts; i++) {
            double ang = i * Math.PI * 2.0 / shafts + spin;
            starts[i] = ctx.rel(target.add(axis.scale(shaftHeight)).add(e1.scale(Math.cos(ang) * ringRadius))
                    .add(e2.scale(Math.sin(ang) * ringRadius)));
            flicker[i] = 0.82F + 0.18F * Mth.sin(t * 0.5F + i * 1.7F) * Mth.sin(t * 0.23F + i);
        }
        VertexConsumer beam = ctx.buffers().getBuffer(SolarRenderTypes.column());
        for (int i = 0; i < shafts; i++) {
            // soft hazy sunbeams (no core line), wide where they leave the sky, narrowing onto the target
            sunbeam(pose, beam, starts[i], rt, Mth.lerp(e, 11.0F, 4.0F), Mth.lerp(e, 3.2F, 1.0F),
                    FxDraw.argb((0.1F + 0.16F * e) * flicker[i], GOLD), FxDraw.argb((0.32F + 0.48F * e) * flicker[i], WARM),
                    shaftHeight, 0.0F);
        }
        // as the light concentrates, each sunbeam gains a bright filament
        float lines = (float) SolarMath.ease((e - 0.35) / 0.65);
        if (lines > 0.01F) {
            for (int i = 0; i < shafts; i++) {
                FxDraw.ribbon(pose, beam, starts[i], rt, 0.5F, 0.25F, FxDraw.argb(0.15F * lines * flicker[i], PALE),
                        FxDraw.argb(0.55F * lines * flicker[i], WHITE), shaftHeight, 0.0F);
            }
        }

        // 3. the focus beam forming along the axis in the last moments: the light is concentrated
        float focus = (float) SolarMath.ease((p - 0.55) / 0.45);
        if (focus > 0.002F) {
            Vec3 high = ctx.rel(target.add(axis.scale(260.0)));
            float quiver = 0.85F + 0.15F * Mth.sin(t * 2.7F);
            FxDraw.ribbon(pose, over, high, rt, 3.5F * focus, FxDraw.argb(0.3F * focus, GOLD), 260.0F, 0.0F);
            FxDraw.ribbon(pose, beam, high, rt, 1.4F * focus * quiver, FxDraw.argb(0.9F * focus, PALE), 260.0F, 0.0F);
            FxDraw.ribbon(pose, beam, high, rt, 0.45F * focus, FxDraw.argb(focus, WHITE), 260.0F, 0.0F);
        }

        // 4. on the ground: a turning reticle that shrinks and brightens, rings pulled inward, a pool of light
        float ring = (float) SunsFuryEntity.ringRadius(e);
        Vec3 ground = ctx.rel(new Vec3(target.x, target.y + 0.06, target.z));
        VertexConsumer marks = ctx.buffers().getBuffer(SolarRenderTypes.ring());
        groundMark(pose, marks, ground.add(0, 0.02, 0), ring * 1.9F, SolarRenderTypes.POOL, FxDraw.argb(0.2F + 0.65F * e, GOLD));
        groundMark(pose, marks, ground.add(0, 0.03, 0), ring, SolarRenderTypes.RETICLE, FxDraw.argb(0.45F + 0.55F * e, WARM));
        for (int j = 0; j < 2; j++) {
            float q = (t * 0.035F + j * 0.5F) % 1.0F;
            float r = ring * (1.0F + 2.4F * (1.0F - q));
            groundMark(pose, marks, ground.add(0, 0.04 + 0.01 * j, 0), r, SolarRenderTypes.THIN,
                    FxDraw.argb(Mth.sin(q * Mth.PI) * (0.25F + 0.5F * e), PALE));
        }
        VertexConsumer pool = ctx.buffers().getBuffer(SolarRenderTypes.ringOver());
        groundMark(pose, pool, ground.add(0, 0.01, 0), ring * 1.3F, SolarRenderTypes.POOL, FxDraw.argb(0.1F + 0.35F * e, WARM));

        // 5. the spear's own light, swelling
        Vec3 d = spear.pointing(pt);
        Vec3 spearMid = ctx.rel(target.subtract(d.scale(0.45)));
        float pulse = 1.0F + 0.1F * Mth.sin(t * (0.5F + 1.6F * e));
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), spearMid, (0.9F + 2.8F * e) * pulse, t * 0.03F, FxDraw.argb(0.55F + 0.45F * e, PALE));
        VertexConsumer flare = ctx.buffers().getBuffer(SolarRenderTypes.flare());
        FxDraw.billboard(pose, flare, ctx.camera(), ctx.rel(target.add(0, 0.4, 0)), (1.5F + 7.5F * e * e) * pulse, -t * 0.01F,
                FxDraw.argb(0.25F + 0.7F * e * e, GOLD));
    }

    /**
     * Direction the light comes from: straight up, leaning toward the sun while it is above the horizon (at most about
     * 40 degrees) so the cone visibly swings up with the sun as the day time sweeps to noon.
     */
    static Vec3 lightAxis(ClientLevel level, float partialTick) {
        Vec3 up = new Vec3(0, 1, 0);
        if (level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            return up;
        }
        double angle = level.getTimeOfDay(partialTick) * Math.PI * 2.0;
        Vec3 sun = new Vec3(-Math.sin(angle), Math.cos(angle), 0.0);
        if (sun.y <= 0.0) {
            return up;
        }
        Vec3 leaning = sun.add(up.scale(1.3)).normalize();
        double w = Math.min(1.0, sun.y * 4.0);
        return up.add(leaning.subtract(up).scale(w)).normalize();
    }

    /** 0 at night, 1 in full daylight (the vanilla sky brightness curve, rain included). */
    static float daylight(ClientLevel level, float partialTick) {
        if (level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            return 0.0F;
        }
        return Mth.clamp((level.getSkyDarken(partialTick) - 0.2F) / 0.8F, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ strike & fade

    private static void column(SolarRayEntity ray, FxRenderContext ctx) {
        PoseStack.Pose pose = ctx.pose().last();
        float pt = ctx.partialTick();
        double age = ray.age(pt);
        float intensity = (float) SolarMath.columnIntensity(age, Balance.SOLAR_RAY_TICKS, Balance.SOLAR_FADE_TICKS);
        if (intensity <= 0.002F) {
            return;
        }
        float t = (float) age;
        float fade = (float) SolarMath.ease((age - Balance.SOLAR_RAY_TICKS) / Balance.SOLAR_FADE_TICKS);
        float width = 1.0F - 0.88F * fade;
        float burst = 1.0F - (float) SolarMath.ease(age / 10.0);
        float swell = width * (1.0F + 0.55F * burst);
        float breathe = 1.0F + 0.04F * Mth.sin(t * 0.9F) + 0.03F * Mth.sin(t * 2.3F + 1.0F);
        float a = intensity;
        float soft = (float) Math.sqrt(a);

        Vec3 base = ray.getPosition(pt);
        double topY = Math.max(base.y + 320.0, ctx.level().getMaxBuildHeight() + 96.0);
        float height = (float) (topY - base.y);
        Vec3 bottom = ctx.rel(base.add(0, -6.0, 0));
        Vec3 top = ctx.rel(new Vec3(base.x, topY, base.z));

        // the pillar, outer layers: an amber haze and a golden halo that also cover the sky behind them (while fading
        // they keep their colour longer than their width: the pillar thins into a golden thread)
        // by night the faint outer haze would only read as a brown veil over the stars: keep it for the day
        float day = daylight(ctx.level(), pt);
        VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
        FxDraw.ribbon(pose, over, bottom, top, 64.0F * swell * breathe, FxDraw.argb(0.2F * soft * (0.35F + 0.65F * day), AMBER), -6.0F,
                height);
        FxDraw.ribbon(pose, over, bottom, top, 30.0F * swell * breathe, FxDraw.argb(0.45F * soft * (0.6F + 0.4F * day), GOLD), -6.0F,
                height);
        // a bright splash where it meets the ground
        FxDraw.ribbon(pose, over, bottom, ctx.rel(base.add(0, 14.0, 0)), 40.0F * swell, 12.0F * swell,
                FxDraw.argb(0.45F * a, WARM), FxDraw.argb(0.0F, WARM), 0.0F, 20.0F);

        // inner layers, additive: they pile up into a white-hot core
        VertexConsumer beam = ctx.buffers().getBuffer(SolarRenderTypes.column());
        sunbeam(pose, beam, bottom, top, 20.0F * swell, 20.0F * swell, FxDraw.argb(0.5F * a, GOLD), FxDraw.argb(0.5F * a, GOLD),
                -6.0F, height);
        FxDraw.ribbon(pose, beam, bottom, top, 13.0F * swell, FxDraw.argb(0.6F * a, WARM), -6.0F, height);
        FxDraw.ribbon(pose, beam, bottom, top, 6.5F * swell, FxDraw.argb(0.9F * a, PALE), -6.0F, height);
        FxDraw.ribbon(pose, beam, bottom, top, 2.8F * swell, FxDraw.argb(Math.min(1.0F, a + burst), WHITE), -6.0F, height);

        // rings of light pouring down the pillar
        VertexConsumer marks = ctx.buffers().getBuffer(SolarRenderTypes.ring());
        float pour = 1.0F - fade;
        if (pour > 0.01F) {
            for (int j = 0; j < 4; j++) {
                float phase = (t / 18.0F + j / 4.0F) % 1.0F;
                float h = 100.0F * (1.0F - phase) * (1.0F - phase);
                float r = 7.5F * width * (1.0F + 0.6F * phase);
                float ra = Mth.sin(phase * Mth.PI) * 0.5F * a * pour;
                groundMark(pose, marks, ctx.rel(base.add(0, h + 0.5, 0)), r, SolarRenderTypes.THIN, FxDraw.argb(ra, GOLD));
            }
        }

        // where it leaves the sky: a glowing aperture
        float aperture = (float) Math.min(height - 10.0, 190.0);
        Vec3 sky = ctx.rel(base.add(0, aperture, 0));
        groundMark(pose, marks, sky, 46.0F * swell, SolarRenderTypes.POOL, FxDraw.argb(0.55F * a, GOLD));
        groundMark(pose, marks, sky.add(0, -0.5, 0), 26.0F * width, SolarRenderTypes.THIN, FxDraw.argb(0.5F * a, PALE));

        // on the ground: a molten pool of light, the impact starburst and a dome of light
        Vec3 groundC = ctx.rel(base.add(0, 0.08, 0));
        groundMark(pose, marks, groundC, 18.0F * swell, SolarRenderTypes.POOL, FxDraw.argb(0.7F * a, GOLD));
        VertexConsumer pool = ctx.buffers().getBuffer(SolarRenderTypes.ringOver());
        groundMark(pose, pool, groundC.add(0, -0.02, 0), 12.0F * swell, SolarRenderTypes.POOL, FxDraw.argb(0.35F * a, WARM));
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), ctx.rel(base.add(0, 2.0, 0)), 11.0F * swell, t * 0.01F, FxDraw.argb(0.55F * a, WARM));
        VertexConsumer flare = ctx.buffers().getBuffer(SolarRenderTypes.flare());
        FxDraw.billboard(pose, flare, ctx.camera(), ctx.rel(base.add(0, 1.4, 0)), (7.0F + 16.0F * burst) * swell, t * 0.012F,
                FxDraw.argb(Math.min(1.0F, a * (0.75F + burst)), PALE));

        // shockwaves racing outward along the ground
        float[] starts = {0.0F, 5.0F, 12.0F, Balance.SOLAR_RAY_TICKS + 1.0F};
        float[] reach = {46.0F, 34.0F, 24.0F, 18.0F};
        for (int j = 0; j < starts.length; j++) {
            float local = (t - starts[j]) / 24.0F;
            if (local <= 0.0F || local >= 1.0F) {
                continue;
            }
            float r = 1.5F + reach[j] * (float) SolarMath.easeOut(local);
            float alpha = (float) Math.pow(1.0F - local, 1.4) * (j == 0 ? 1.0F : 0.7F);
            groundMark(pose, marks, groundC.add(0, 0.03 * (j + 1), 0), r, SolarRenderTypes.SHOCKWAVE,
                    FxDraw.argb(alpha, j == 0 ? WHITE : WARM));
        }
    }
}
