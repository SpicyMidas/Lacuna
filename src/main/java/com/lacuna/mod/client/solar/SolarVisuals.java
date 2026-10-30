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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The light of Sun's Fury, drawn after particles (so it also works under Fabulous graphics) for every spear / column
 * whose entity renderer ran this frame (vanilla culling decides what is visible). Everything is GPU shader light
 * ({@link SolarRenderTypes}), always additive: the pillar and the focus sunbeams are real 3D volumes ({@link SolarTubes};
 * camera-facing ribbons without its shader), the trail a volumetric ribbon, flat procedural marks rings and pools.
 * <ul>
 *   <li>flight: a tapering golden comet trail and a starburst on the spear head;</li>
 *   <li>focus: while the sun is up, a wide cone of sunlight, soft sunbeams and finally a thin focus beam that all leave
 *       the sun's disc exactly where it is drawn ({@link SunLight}), glide with it through the time sweep in its
 *       colour (orange at sunrise, warm white high up) and converge on the spear (a magnifying glass gathering
 *       sunlight); always a turning target reticle that shrinks and brightens, pull-in rings, a ground light pool and
 *       the spear's own swelling glow, in white light while the sun is below the horizon;</li>
 *   <li>strike: a sky-to-ground pillar of nested light volumes (amber haze, golden halo, sunbeam body, striated body,
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
            if (!QUEUE.isEmpty()) {
                SunLight.update(ctx.level(), ctx.partialTick());
            }
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

    /**
     * A straight shaft of light from {@code a} to {@code b} (camera-relative), {@code widthA} / {@code widthB} across:
     * a 3D volume ({@link SolarTubes}) or, without the tube shader, the camera-facing ribbon into {@code fallback}
     * ({@code sunbeam}: the soft sunbeam profile).
     */
    static void tube(PoseStack.Pose pose, VertexConsumer fallback, Vec3 a, Vec3 b, float widthA, float widthB, int argbA, int argbB,
                      float vA, float vB, boolean sunbeam) {
        tube(pose, fallback, a, b, widthA, widthB, argbA, argbB, vA, vB, sunbeam, 0.0F, 0.0F, 0.0F);
    }

    /**
     * {@link #tube} with the 3D extras ({@link SolarTubes}): a bright body {@code cover} (0..1) under the light, rotating
     * light {@code streaks} and rising {@code embers} (the flat fallback has none of them).
     */
    static void tube(PoseStack.Pose pose, VertexConsumer fallback, Vec3 a, Vec3 b, float widthA, float widthB, int argbA, int argbB,
                     float vA, float vB, boolean sunbeam, float cover, float streaks, float embers) {
        if (SolarTubes.straight(pose, a, b, widthA * 0.5F, widthB * 0.5F, argbA, argbB, vA, vB,
                sunbeam ? SolarTubes.SUNBEAM : SolarTubes.PILLAR, 0, cover, streaks, embers)) {
            return;
        }
        if (sunbeam) {
            sunbeam(pose, fallback, a, b, widthA, widthB, argbA, argbB, vA, vB);
        } else {
            FxDraw.ribbon(pose, fallback, a, b, widthA, widthB, argbA, argbB, vA, vB);
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
            // each layer runs from the butt (index 0) to the tail in one piece: no seams
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
            if (SolarTubes.room(3 * n)) {
                // a comet of light as 3D volumes along a smooth curve through the trail: a wide soft glow, the golden
                // body with its white-hot core, and a thin white thread with a bright body of its own
                for (int i = 0; i < n; i++) {
                    float wk = wake(k[i]);
                    w[i] = 2.6F * wk + 0.1F;
                    c[i] = FxDraw.argb(0.62F * wk * Math.min(1.0F, 1.6F * k[i]), SunLight.mix(AMBER, GOLD, k[i]));
                }
                trail(pose, rel, w, c, v, SolarTubes.SUNBEAM, 0.0F);
                for (int i = 0; i < n; i++) {
                    w[i] = 1.1F * k[i] + 0.05F;
                    c[i] = FxDraw.argb(0.85F * k[i] * k[i], GOLD);
                }
                trail(pose, rel, w, c, v, SolarTubes.PILLAR, 0.0F);
                for (int i = 0; i < n; i++) {
                    w[i] = 0.36F * k[i] + 0.02F;
                    c[i] = FxDraw.argb(0.8F * k[i] * k[i], WHITE);
                }
                trail(pose, rel, w, c, v, SolarTubes.PILLAR, 0.35F);
            } else {
                // without the tube shader: one continuous camera-facing strip per layer
                VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
                // the wide glow swells in just behind the spear instead of starting with a hard edge
                for (int i = 0; i < n; i++) {
                    float wk = wake(k[i]);
                    w[i] = 2.2F * wk + 0.1F;
                    c[i] = FxDraw.argb(0.3F * wk * Math.min(1.0F, 1.6F * k[i]), AMBER);
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
        }
        // the head of the spear blazes like a small sun
        Vec3 tip = ctx.rel(head.add(dir.scale(0.75)));
        VertexConsumer flare = ctx.buffers().getBuffer(SolarRenderTypes.flare());
        FxDraw.billboard(pose, flare, ctx.camera(), tip, 1.15F + 0.12F * Mth.sin(t * 0.9F), t * 0.04F, FxDraw.argb(0.85F, GOLD));
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), tip, 0.4F, -t * 0.05F, FxDraw.argb(0.9F, PALE));
    }

    /**
     * One layer of the flight trail as 3D light ({@link SolarTubes}): a quadratic B-spline through the camera-relative
     * {@code points} (straight halves at both ends, curved pieces from midpoint to midpoint around every inner point),
     * so the pieces meet with one tangent and the light flows through the bends without seams. Width, colour and v are
     * those of the points, averaged at the midpoints; the body {@code cover} follows the layer's alpha.
     */
    private static void trail(PoseStack.Pose pose, List<Vec3> points, float[] width, int[] argb, float[] v, int profile, float cover) {
        int n = points.size();
        for (int i = 0; i < n; i++) {
            // piece i runs from the start of point i's span to its end: P0 -> M01, M(i-1,i) -> M(i,i+1), M(n-2,n-1) -> Pn-1
            Vec3 p = points.get(i);
            Vec3 from = i == 0 ? p : p.add(points.get(i - 1)).scale(0.5);
            Vec3 to = i == n - 1 ? p : p.add(points.get(i + 1)).scale(0.5);
            if (from.distanceToSqr(to) < 1.0E-6) {
                continue;
            }
            Vec3 knot = i == 0 || i == n - 1 ? from.add(to).scale(0.5) : p;
            float wFrom = i == 0 ? width[0] : 0.5F * (width[i - 1] + width[i]);
            float wTo = i == n - 1 ? width[i] : 0.5F * (width[i] + width[i + 1]);
            int cFrom = i == 0 ? argb[0] : mean(argb[i - 1], argb[i]);
            int cTo = i == n - 1 ? argb[i] : mean(argb[i], argb[i + 1]);
            float vFrom = i == 0 ? v[0] : 0.5F * (v[i - 1] + v[i]);
            float vTo = i == n - 1 ? v[i] : 0.5F * (v[i] + v[i + 1]);
            float alpha = Math.max(cFrom >>> 24, cTo >>> 24) / 255.0F;
            SolarTubes.curve(pose, from.x, from.y, from.z, knot.x, knot.y, knot.z, to.x, to.y, to.z, wFrom * 0.5F, wTo * 0.5F, cFrom, cTo,
                    vFrom, vTo, profile, 0, cover * alpha, 0.0F, 0.0F);
        }
    }

    /** The mean of two ARGB colours, channel by channel (alpha included). */
    private static int mean(int a, int b) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            out |= ((((a >>> shift) & 0xFF) + ((b >>> shift) & 0xFF)) / 2) << shift;
        }
        return out;
    }

    /** Trail glow profile along the trail ({@code t} = 1 at the spear, 0 at the tail): swells in, then tapers away. */
    private static float wake(float t) {
        float s = 1.0F - t;
        return t * (float) SolarMath.ease(s / 0.3);
    }

    // ------------------------------------------------------------------ focus

    private static final int SHAFTS = 7;
    /** Pieces of each sunlight shaft (closer together near the target, where the camera usually is). */
    private static final int SEGMENTS = 12;
    private static final double[] PX = new double[SEGMENTS + 1];
    private static final double[] PY = new double[SEGMENTS + 1];
    private static final double[] PZ = new double[SEGMENTS + 1];
    private static final double[] SX = new double[SEGMENTS + 1];
    private static final double[] SY = new double[SEGMENTS + 1];
    private static final double[] SZ = new double[SEGMENTS + 1];
    private static final float[] SV = new float[SEGMENTS + 1];
    private static final int[] SC = new int[SEGMENTS + 1];

    private static void focus(SunsFuryEntity spear, FxRenderContext ctx) {
        PoseStack.Pose pose = ctx.pose().last();
        float pt = ctx.partialTick();
        double p = spear.focusProgress(pt);
        float e = (float) SolarMath.ease(p);
        float t = ctx.time(spear.tickCount);
        Vec3 target = spear.getPosition(pt);
        Vec3 rt = ctx.rel(target);

        // 1-3. the gathered sunlight, from the sun's disc, only while the sun is above the horizon
        if (SunLight.up > 0.003F) {
            sunlight(pose, ctx, rt, p, e, t, SunLight.up);
        }

        // 4. where it is going to hit: a turning reticle that shrinks and brightens, rings pulled inward and a pool of
        // light on the ground, in the sun's colour by day and in white light by night (like the Divine Magnifying Glass)
        float ring = (float) SunsFuryEntity.ringRadius(e);
        int gold = SunLight.lit(GOLD, 0.0F);
        int warm = SunLight.lit(WARM, 0.15F);
        int pale = SunLight.lit(PALE, 0.35F);
        // (all of them lie on the terrain: SolarGround)
        com.lacuna.mod.client.fx.GroundDecal ground = SolarGround.at(ctx, spear, target, ring * 3.4F);
        VertexConsumer marks = ctx.buffers().getBuffer(SolarRenderTypes.ring());
        SolarGround.mark(ctx, ground, marks, target, ring * 1.9F, SolarRenderTypes.POOL, 0.006F, FxDraw.argb(0.2F + 0.65F * e, gold));
        SolarGround.mark(ctx, ground, marks, target, ring, SolarRenderTypes.RETICLE, 0.012F, FxDraw.argb(0.45F + 0.55F * e, warm));
        for (int j = 0; j < 2; j++) {
            float q = (t * 0.035F + j * 0.5F) % 1.0F;
            float r = ring * (1.0F + 2.4F * (1.0F - q));
            SolarGround.mark(ctx, ground, marks, target, r, SolarRenderTypes.THIN, 0.018F + 0.006F * j,
                    FxDraw.argb(Mth.sin(q * Mth.PI) * (0.25F + 0.5F * e), pale));
        }
        VertexConsumer pool = ctx.buffers().getBuffer(SolarRenderTypes.ringOver());
        SolarGround.mark(ctx, ground, pool, target, ring * 1.3F, SolarRenderTypes.POOL, 0.0F, FxDraw.argb(0.1F + 0.35F * e, warm));

        // 5. the spear's own light, swelling
        Vec3 d = spear.pointing(pt);
        Vec3 spearMid = ctx.rel(target.subtract(d.scale(0.45)));
        float pulse = 1.0F + 0.1F * Mth.sin(t * (0.5F + 1.6F * e));
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), spearMid, (0.9F + 2.8F * e) * pulse, t * 0.03F, FxDraw.argb(0.55F + 0.45F * e, pale));
        VertexConsumer flare = ctx.buffers().getBuffer(SolarRenderTypes.flare());
        FxDraw.billboard(pose, flare, ctx.camera(), ctx.rel(target.add(0, 0.4, 0)), (1.5F + 7.5F * e * e) * pulse, -t * 0.01F,
                FxDraw.argb(0.25F + 0.7F * e * e, gold));
    }

    /**
     * The sunlight of the focus, leaving the sun exactly where vanilla draws it this frame: the sky is drawn at infinity
     * with the same camera rotation, so a point {@code far} blocks from the camera along {@link SunLight}'s direction
     * lands on the sun's disc. Like light through a magnifying glass, the sunbeams leave the disc, swing out through a
     * wide ring around the sun-to-target axis and converge on the target; as the light concentrates the ring shrinks
     * until they gather into one beam. All of it glides with the sun through the time sweep in the sun's colour
     * (orange at sunrise, warm white high up); {@code sun} fades it out as the sun sinks below the horizon.
     */
    private static void sunlight(PoseStack.Pose pose, FxRenderContext ctx, Vec3 rt, double p, float e, float t, float sun) {
        double far = Math.min(320.0, 0.75 * Minecraft.getInstance().gameRenderer.getDepthFar());
        double sx = SunLight.dirX;
        double sy = SunLight.dirY;
        // the sun's centre, and the midpoint used as the control point of the straight sun-to-target layers
        double cx = sx * far;
        double cy = sy * far;
        double mx = (cx + rt.x) * 0.5;
        double my = (cy + rt.y) * 0.5;
        double mz = rt.z * 0.5;
        int amber = SunLight.shade(AMBER, 0.0F);
        int gold = SunLight.shade(GOLD, 0.0F);
        int warm = SunLight.shade(WARM, 0.1F);
        int pale = SunLight.shade(PALE, 0.3F);
        int white = SunLight.shade(WHITE, 0.5F);

        // 1. a vast faint cone of gathered sunlight from the sun narrowing onto the target
        VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.skyColumnOver());
        shaft(pose, over, rt, mx, my, mz, cx, cy, 0.0, Mth.lerp(e, 20.0F, 1.6F), (float) (far * Mth.lerp(e, 0.3F, 0.06F)),
                (0.06F + 0.2F * e) * sun, (0.025F + 0.06F * e) * sun, gold, amber, 0);

        // 2. soft sunbeams, turning slowly; as the light concentrates they narrow, brighten and gain a bright filament
        // (the ring lies across the sun's direction, spanned by (0, 0, 1) and dir x (0, 0, 1) = (sy, -sx, 0))
        double ring = Mth.lerp(e, 64.0F, 3.0F);
        double lens = Math.min(Mth.lerp(e, 80.0F, 120.0F), 0.45 * far);
        double disc = SunLight.DISC * Mth.lerp(e, 0.8F, 0.2F);
        float spin = t * 0.008F + e * 1.4F;
        float sunWidth = (float) (far * SunLight.DISC * Mth.lerp(e, 0.7F, 0.35F));
        float lines = (float) SolarMath.ease((e - 0.35) / 0.65);
        VertexConsumer beam = ctx.buffers().getBuffer(SolarRenderTypes.skyColumn());
        for (int i = 0; i < SHAFTS; i++) {
            double ang = i * Math.PI * 2.0 / SHAFTS + spin;
            double cos = Math.cos(ang);
            double sin = Math.sin(ang);
            // where it leaves the sun's disc
            double ax = (sx + sin * disc * sy) * far;
            double ay = (sy - sin * disc * sx) * far;
            double az = cos * disc * far;
            // the ring it swings out through (kept above the ground while the sun is low)
            double kx = rt.x + sx * lens + sin * ring * sy;
            double ky = Math.max(rt.y + sy * lens - sin * ring * sx, rt.y + 4.0 + 0.2 * ring);
            double kz = rt.z + cos * ring;
            float flicker = (0.82F + 0.18F * Mth.sin(t * 0.5F + i * 1.7F) * Mth.sin(t * 0.23F + i)) * sun;
            shaft(pose, beam, rt, kx, ky, kz, ax, ay, az, Mth.lerp(e, 3.2F, 1.0F), sunWidth, (0.32F + 0.48F * e) * flicker,
                    (0.1F + 0.16F * e) * flicker, warm, gold, 2);
            if (lines > 0.01F) {
                shaft(pose, beam, rt, kx, ky, kz, ax, ay, az, 0.25F, (float) (far * 0.004), 0.55F * lines * flicker,
                        0.15F * lines * flicker, white, pale, 0);
            }
        }

        // 3. the focus beam forming straight from the sun's centre in the last moments: the light is concentrated
        float focus = (float) SolarMath.ease((p - 0.55) / 0.45);
        if (focus > 0.002F) {
            float quiver = 0.85F + 0.15F * Mth.sin(t * 2.7F);
            float a = focus * sun;
            shaft(pose, over, rt, mx, my, mz, cx, cy, 0.0, 3.5F * focus, (float) (far * 0.02) * focus, 0.2F * a, 0.2F * a, gold, gold, 0);
            shaft(pose, beam, rt, mx, my, mz, cx, cy, 0.0, 1.4F * focus * quiver, 1.4F * focus * quiver, 0.9F * a, 0.9F * a, pale, pale, 0);
            shaft(pose, beam, rt, mx, my, mz, cx, cy, 0.0, 0.45F * focus, 0.45F * focus, a, a, white, white, 0, 0.6F * a);
        }
    }

    /**
     * Camera-facing ribbon along the quadratic Bezier curve from the camera-relative target {@code t} through control
     * point {@code k} to {@code a} (on the sun), in {@link #SEGMENTS} pieces sharing their edges (closer together near
     * the target), with width, alpha and colour interpolated from the target end ({@code ...T}) to the sun end
     * ({@code ...S}); it fades out where it passes within a few blocks of the camera, so a shaft falling right past the
     * eyes never fills the screen. u runs {@code u0}..{@code u0 + 1} across ({@code u0 = 2}: soft sunbeam), v = blocks
     * along from the target. No allocation.
     */
    private static void shaft(PoseStack.Pose pose, VertexConsumer vc, Vec3 t, double kx, double ky, double kz, double ax, double ay,
                              double az, float widthT, float widthS, float alphaT, float alphaS, int rgbT, int rgbS, int u0) {
        shaft(pose, vc, t, kx, ky, kz, ax, ay, az, widthT, widthS, alphaT, alphaS, rgbT, rgbS, u0, 0.0F);
    }

    /** {@link #shaft} with a bright body {@code cover} (0..1) under the light when drawn in 3D. */
    private static void shaft(PoseStack.Pose pose, VertexConsumer vc, Vec3 t, double kx, double ky, double kz, double ax, double ay,
                              double az, float widthT, float widthS, float alphaT, float alphaS, int rgbT, int rgbS, int u0, float cover) {
        if (Math.max(alphaT, alphaS) <= 0.002F) {
            return;
        }
        // as a 3D volume when the tube shader is there (sunlight from the sky: not dimmed with distance)
        double toK = Math.sqrt((kx - t.x) * (kx - t.x) + (ky - t.y) * (ky - t.y) + (kz - t.z) * (kz - t.z));
        double toA = Math.sqrt((ax - kx) * (ax - kx) + (ay - ky) * (ay - ky) + (az - kz) * (az - kz));
        double chord = Math.sqrt((ax - t.x) * (ax - t.x) + (ay - t.y) * (ay - t.y) + (az - t.z) * (az - t.z));
        if (SolarTubes.curve(pose, t.x, t.y, t.z, kx, ky, kz, ax, ay, az, widthT * 0.5F, widthS * 0.5F, FxDraw.argb(alphaT, rgbT),
                FxDraw.argb(alphaS, rgbS), 0.0F, (float) (0.5 * (chord + toK + toA)),
                u0 == 2 ? SolarTubes.SUNBEAM : SolarTubes.PILLAR, SolarTubes.SKY | SolarTubes.NEAR_FADE, cover, 0.0F, 0.0F)) {
            return;
        }
        double along = 0.0;
        for (int i = 0; i <= SEGMENTS; i++) {
            double q = (double) i / SEGMENTS;
            q *= q;
            double r = 1.0 - q;
            double x = r * r * t.x + 2.0 * q * r * kx + q * q * ax;
            double y = r * r * t.y + 2.0 * q * r * ky + q * q * ay;
            double z = r * r * t.z + 2.0 * q * r * kz + q * q * az;
            if (i > 0) {
                double ex = x - PX[i - 1];
                double ey = y - PY[i - 1];
                double ez = z - PZ[i - 1];
                along += Math.sqrt(ex * ex + ey * ey + ez * ez);
            }
            PX[i] = x;
            PY[i] = y;
            PZ[i] = z;
            // across the ribbon: perpendicular to the curve and to the view ray to this point
            double dx = r * (kx - t.x) + q * (ax - kx);
            double dy = r * (ky - t.y) + q * (ay - ky);
            double dz = r * (kz - t.z) + q * (az - kz);
            double cx = dy * z - dz * y;
            double cy = dz * x - dx * z;
            double cz = dx * y - dy * x;
            double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (len < 1.0E-6) {
                boolean vertical = Math.abs(dy) > 0.99 * Math.sqrt(dx * dx + dy * dy + dz * dz);
                cx = vertical ? 0.0 : -dz;
                cy = vertical ? dz : 0.0;
                cz = vertical ? -dy : dx;
                len = Math.sqrt(cx * cx + cy * cy + cz * cz);
                if (len < 1.0E-9) {
                    return;
                }
            }
            double half = Mth.lerp(q, widthT, widthS) * 0.5 / len;
            SX[i] = cx * half;
            SY[i] = cy * half;
            SZ[i] = cz * half;
            double distance = Math.sqrt(x * x + y * y + z * z);
            float near = (float) SolarMath.ease((distance - 1.5) / 6.0);
            SC[i] = FxDraw.argb(Mth.lerp((float) q, alphaT, alphaS) * near, SunLight.mix(rgbT, rgbS, (float) q));
            SV[i] = (float) along;
        }
        float u1 = u0 + 1.0F;
        for (int i = 0; i < SEGMENTS; i++) {
            int j = i + 1;
            vc.addVertex(pose, (float) (PX[i] - SX[i]), (float) (PY[i] - SY[i]), (float) (PZ[i] - SZ[i])).setUv(u0, SV[i]).setColor(SC[i]);
            vc.addVertex(pose, (float) (PX[i] + SX[i]), (float) (PY[i] + SY[i]), (float) (PZ[i] + SZ[i])).setUv(u1, SV[i]).setColor(SC[i]);
            vc.addVertex(pose, (float) (PX[j] + SX[j]), (float) (PY[j] + SY[j]), (float) (PZ[j] + SZ[j])).setUv(u1, SV[j]).setColor(SC[j]);
            vc.addVertex(pose, (float) (PX[j] - SX[j]), (float) (PY[j] - SY[j]), (float) (PZ[j] - SZ[j])).setUv(u0, SV[j]).setColor(SC[j]);
        }
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

        // the pillar, outer layers: a faint amber haze and a golden halo, additive like all the light (while fading
        // they keep their colour longer than their width: the pillar thins into a golden thread)
        float day = daylight(ctx.level(), pt);
        VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
        // (nested 3D shells, SolarTubes: seen from any side, from below or from inside, they keep their thickness)
        // additive light reads stronger against the night sky than on a bright day sky (where too much of it only
        // bleaches into a wide white slab): the outer layers ease off by day
        float glare = 1.0F - 0.45F * day;
        int haze = FxDraw.argb(0.08F * soft * glare, AMBER);
        int halo = FxDraw.argb(0.16F * soft * glare, GOLD);
        float hazeWidth = 64.0F * swell * breathe;
        float haloWidth = 30.0F * swell * breathe;
        tube(pose, over, bottom, top, hazeWidth, hazeWidth, haze, haze, -6.0F, height, false);
        // embers rising through the halo
        tube(pose, over, bottom, top, haloWidth, haloWidth, halo, halo, -6.0F, height, false, 0.0F, 0.0F, 4.0F);
        // a bright splash where it meets the ground: a cone of light widening into the ground
        tube(pose, over, bottom, ctx.rel(base.add(0, 14.0, 0)), 40.0F * swell, 12.0F * swell,
                FxDraw.argb(0.22F * a * glare, WARM), FxDraw.argb(0.0F, WARM), 0.0F, 20.0F, false);

        // inner layers: they pile up into a white-hot core. Each lays a bright body of its own colour under its light
        // (cover), golden outside, white-gold towards the core, so the pillar reads as thick and blinding over a bright
        // sky as over dark terrain (light that only adds bleaches into the sky and thins out over the ground); three
        // light streaks wind up the body, turning
        VertexConsumer beam = ctx.buffers().getBuffer(SolarRenderTypes.column());
        int gold = FxDraw.argb(0.32F * a * glare, GOLD);
        int warm = FxDraw.argb(0.42F * a * glare, WARM);
        int pale = FxDraw.argb(0.7F * a * (1.0F - 0.25F * day), PALE);
        int white = FxDraw.argb(Math.min(1.0F, a + burst), WHITE);
        tube(pose, beam, bottom, top, 20.0F * swell, 20.0F * swell, gold, gold, -6.0F, height, true, 0.3F * a, 2.2F, 0.0F);
        tube(pose, beam, bottom, top, 13.0F * swell, 13.0F * swell, warm, warm, -6.0F, height, false, 0.5F * a, 0.0F, 0.0F);
        tube(pose, beam, bottom, top, 6.5F * swell, 6.5F * swell, pale, pale, -6.0F, height, false, 0.7F * a, 0.0F, 0.0F);
        tube(pose, beam, bottom, top, 3.2F * swell, 3.2F * swell, white, white, -6.0F, height, false, 0.95F * a, 0.0F, 0.0F);

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

        // on the ground (lying on the terrain: SolarGround): a molten pool of light, the impact starburst and a dome of light
        com.lacuna.mod.client.fx.GroundDecal groundC = SolarGround.at(ctx, ray, base,
                t < Balance.SOLAR_RAY_TICKS + 26.0F ? 48.0 : 18.0 * swell);
        SolarGround.mark(ctx, groundC, marks, base, 18.0F * swell, SolarRenderTypes.POOL, 0.01F, FxDraw.argb(0.7F * a, GOLD));
        VertexConsumer pool = ctx.buffers().getBuffer(SolarRenderTypes.ringOver());
        SolarGround.mark(ctx, groundC, pool, base, 12.0F * swell, SolarRenderTypes.POOL, 0.0F, FxDraw.argb(0.35F * a, WARM));
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
            SolarGround.mark(ctx, groundC, marks, base, r, SolarRenderTypes.SHOCKWAVE, 0.01F + 0.008F * (j + 1),
                    FxDraw.argb(alpha, j == 0 ? WHITE : WARM));
        }
    }
}
