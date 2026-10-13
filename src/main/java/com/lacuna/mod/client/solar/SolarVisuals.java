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
 * whose entity renderer ran this frame (vanilla culling decides what is visible):
 * <ul>
 *   <li>flight: a tapering golden shader trail and a flare on the spear;</li>
 *   <li>focus: a wide faint cone of sunlight tilted toward the sun that narrows onto the spear, rays converging from a
 *       shrinking ring high in the sky (a magnifying glass focusing sunlight), a target ring and ground glow that
 *       shrink and brighten, and the spear's own swelling glow;</li>
 *   <li>strike: a sky-to-ground pillar of layered column-shader ribbons (white core, golden body, wide halo), light
 *       spiralling up around it, an impact flare and expanding shockwave rings; fade: everything thins out.</li>
 * </ul>
 * A few hundred quads at most.
 */
public final class SolarVisuals {
    private static final List<Entity> QUEUE = new ArrayList<>();

    private static final int AMBER = 0xFF9A2A;
    private static final int GOLD = 0xFFC24A;
    private static final int WARM = 0xFFD98A;
    private static final int PALE = 0xFFEFC4;
    private static final int WHITE = 0xFFFFF4;

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

    // ------------------------------------------------------------------ flight

    private static void flight(SunsFuryEntity spear, FxRenderContext ctx) {
        PoseStack.Pose pose = ctx.pose().last();
        Vec3 head = spear.getPosition(ctx.partialTick());
        List<Vec3> points = new ArrayList<>(spear.trail);
        points.add(head);
        VertexConsumer beam = ctx.buffers().getBuffer(SolarRenderTypes.column());
        int n = points.size();
        float along = 0.0F;
        for (int i = n - 1; i > 0; i--) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i - 1);
            float ta = (float) i / (n - 1);
            float tb = (float) (i - 1) / (n - 1);
            float len = (float) a.distanceTo(b);
            FxDraw.ribbon(pose, beam, ctx.rel(a), ctx.rel(b), 0.9F * ta + 0.05F, 0.9F * tb + 0.05F,
                    FxDraw.argb(0.85F * ta, GOLD), FxDraw.argb(0.85F * tb, GOLD), along, along + len);
            FxDraw.ribbon(pose, beam, ctx.rel(a), ctx.rel(b), 0.3F * ta + 0.02F, 0.3F * tb + 0.02F,
                    FxDraw.argb(ta, WHITE), FxDraw.argb(tb, WHITE), along, along + len);
            along += len;
        }
        float t = ctx.time(spear.tickCount);
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), ctx.rel(head), 0.6F + 0.08F * Mth.sin(t * 0.9F), t * 0.05F, FxDraw.argb(0.9F, PALE));
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
        // 1. a wide faint cone of sunlight narrowing onto the target (golden, covering: it reads by day and by night)
        float coneHeight = 150.0F;
        Vec3 top = target.add(axis.scale(coneHeight));
        VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
        FxDraw.ribbon(pose, over, ctx.rel(top), ctx.rel(target), Mth.lerp(e, 46.0F, 6.0F), Mth.lerp(e, 16.0F, 1.0F),
                FxDraw.argb(0.05F + 0.12F * e, AMBER), FxDraw.argb(0.12F + 0.38F * e, GOLD), coneHeight, 0.0F);

        // 2. rays converging from a shrinking ring high above, like light gathered by a lens
        int rays = 8;
        float ringHigh = Mth.lerp(e, 44.0F, 2.0F);
        float rayHeight = Mth.lerp(e, 60.0F, 90.0F);
        Vec3[] starts = new Vec3[rays];
        float[] flickers = new float[rays];
        for (int i = 0; i < rays; i++) {
            double ang = i * Math.PI * 2.0 / rays + t * 0.012;
            starts[i] = target.add(axis.scale(rayHeight)).add(e1.scale(Math.cos(ang) * ringHigh)).add(e2.scale(Math.sin(ang) * ringHigh));
            flickers[i] = 0.85F + 0.15F * Mth.sin(t * 0.7F + i * 1.7F);
            FxDraw.ribbon(pose, over, ctx.rel(starts[i]), ctx.rel(target), 0.7F + 0.7F * e, 0.35F + 0.4F * e,
                    FxDraw.argb((0.1F + 0.3F * e) * flickers[i], GOLD), FxDraw.argb((0.35F + 0.5F * e) * flickers[i], WARM), rayHeight, 0.0F);
        }
        VertexConsumer column = ctx.buffers().getBuffer(SolarRenderTypes.column());
        for (int i = 0; i < rays; i++) {
            FxDraw.ribbon(pose, column, ctx.rel(starts[i]), ctx.rel(target), 0.25F + 0.2F * e, 0.12F + 0.15F * e,
                    FxDraw.argb((0.15F + 0.35F * e) * flickers[i], PALE), FxDraw.argb((0.4F + 0.6F * e) * flickers[i], WHITE), rayHeight, 0.0F);
        }

        // 3. the spear's own light, swelling
        Vec3 d = spear.pointing(pt);
        Vec3 spearMid = target.subtract(d.scale(0.45));
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        float pulse = 1.0F + 0.08F * Mth.sin(t * (0.6F + 1.4F * e));
        FxDraw.billboard(pose, glow, ctx.camera(), ctx.rel(spearMid), (0.9F + 3.2F * e) * pulse, t * 0.03F,
                FxDraw.argb(0.55F + 0.45F * e, PALE));
        FxDraw.billboard(pose, glow, ctx.camera(), ctx.rel(target.add(0, 0.3, 0)), (2.0F + 6.0F * e * e) * pulse, -t * 0.02F,
                FxDraw.argb(0.15F + 0.5F * e * e, GOLD));

        // 4. the target ring on the ground: shrinks and brightens; a second ring keeps pulling inward
        double ground = target.y + 0.06;
        float ring = (float) SunsFuryEntity.ringRadius(e);
        VertexConsumer rings = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
        Vec3 c = ctx.rel(new Vec3(target.x, ground, target.z));
        FxDraw.ground(pose, rings, c, ring, FxDraw.argb(0.35F + 0.65F * e, WARM));
        float q = (t * 0.045F) % 1.0F;
        FxDraw.ground(pose, rings, c.add(0, 0.01, 0), ring * (1.0F + 1.6F * (1.0F - q)), FxDraw.argb(q * (0.25F + 0.55F * e), PALE));
        VertexConsumer disc = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        FxDraw.ground(pose, disc, c.add(0, 0.02, 0), ring * 1.6F, FxDraw.argb(0.12F + 0.55F * e, GOLD));
    }

    /**
     * Direction the light comes from: straight up, leaning toward the sun while it is above the horizon (at most about
     * 45 degrees) so the cone visibly swings up with the sun as the day time sweeps to noon.
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
        Vec3 leaning = sun.add(up.scale(1.1)).normalize();
        double w = Math.min(1.0, sun.y * 4.0);
        return up.add(leaning.subtract(up).scale(w)).normalize();
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
        float width = 1.0F - 0.85F * fade;
        float burst = 1.0F - (float) SolarMath.ease(age / 8.0);
        float swell = width * (1.0F + 0.6F * burst);
        float flicker = 0.93F + 0.07F * Mth.sin(t * 0.8F) * Mth.sin(t * 1.9F + 0.4F);
        float a = intensity * flicker;

        Vec3 base = ray.getPosition(pt);
        double topY = Math.max(base.y + 320.0, ctx.level().getMaxBuildHeight() + 96.0);
        float height = (float) (topY - base.y);
        Vec3 bottom = ctx.rel(base.add(0, -9.0, 0));
        Vec3 top = ctx.rel(new Vec3(base.x, topY, base.z));

        // the pillar, outer layers: a wide amber halo and a golden body that cover the sky behind them
        // (while fading they keep their colour longer than they keep their width: the pillar thins into a golden thread)
        float gold = (float) Math.sqrt(a);
        VertexConsumer over = ctx.buffers().getBuffer(SolarRenderTypes.columnOver());
        FxDraw.ribbon(pose, over, bottom, top, 30.0F * swell, FxDraw.argb(0.32F * gold, AMBER), -9.0F, height);
        FxDraw.ribbon(pose, over, bottom, top, 13.0F * swell, FxDraw.argb(0.55F * gold, GOLD), -9.0F, height);
        // a bright splash where it meets the ground
        FxDraw.ribbon(pose, over, bottom, ctx.rel(base.add(0, 8.0, 0)), 22.0F * swell, 6.0F * swell,
                FxDraw.argb(0.55F * a, WARM), FxDraw.argb(0.0F, WARM), 0.0F, 17.0F);
        spirals(pose, over, ctx, base, t, width, a);

        // inner layers, additive: they pile up into a white-hot core
        VertexConsumer column = ctx.buffers().getBuffer(SolarRenderTypes.column());
        FxDraw.ribbon(pose, column, bottom, top, 6.0F * swell, FxDraw.argb(0.5F * a, WARM), -9.0F, height);
        FxDraw.ribbon(pose, column, bottom, top, 2.2F * swell, FxDraw.argb(Math.min(1.0F, 0.95F * a + burst), WHITE), -9.0F, height);

        // impact flare and ground glow
        Vec3 groundC = ctx.rel(base.add(0, 0.08, 0));
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), ctx.rel(base.add(0, 1.5, 0)), (6.0F + 9.0F * burst) * swell, t * 0.02F,
                FxDraw.argb(Math.min(1.0F, a * (0.7F + burst)), PALE));
        VertexConsumer disc = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
        FxDraw.ground(pose, disc, groundC, 16.0F * swell, FxDraw.argb(0.45F * a, GOLD));

        // shockwave rings racing outward
        VertexConsumer rings = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
        float[] starts = {0.0F, 6.0F, 13.0F, Balance.SOLAR_RAY_TICKS + 1.0F};
        for (int j = 0; j < starts.length; j++) {
            float local = (t - starts[j]) / 22.0F;
            if (local <= 0.0F || local >= 1.0F) {
                continue;
            }
            float r = 2.0F + 40.0F * (float) SolarMath.easeOut(local);
            float alpha = (float) Math.pow(1.0F - local, 1.5) * (j == 0 ? 1.0F : 0.6F);
            FxDraw.ground(pose, rings, groundC.add(0, 0.02 * (j + 1), 0), r, FxDraw.argb(alpha, j == 0 ? WHITE : WARM));
        }
    }

    /** Three golden threads of light spiralling up around the pillar. */
    private static void spirals(PoseStack.Pose pose, VertexConsumer vc, FxRenderContext ctx, Vec3 base, float t, float width, float a) {
        int strands = 3;
        int segments = 22;
        float step = 2.8F;
        float top = segments * step;
        float radius = 3.8F * width;
        for (int k = 0; k < strands; k++) {
            Vec3 prev = null;
            float prevH = 0;
            for (int s = 0; s <= segments; s++) {
                float h = s * step;
                double ang = k * Math.PI * 2.0 / strands + h * 0.16 - t * 0.22;
                float r = radius * (1.0F + 0.12F * Mth.sin(h * 0.3F + t * 0.5F));
                Vec3 point = ctx.rel(base.add(Math.cos(ang) * r, h, Math.sin(ang) * r));
                if (prev != null) {
                    float fa = (1.0F - prevH / top) * 0.45F * a;
                    float fb = (1.0F - h / top) * 0.45F * a;
                    FxDraw.ribbon(pose, vc, prev, point, 1.3F * width, 1.3F * width, FxDraw.argb(fa, GOLD), FxDraw.argb(fb, GOLD), prevH, h);
                }
                prev = point;
                prevH = h;
            }
        }
    }
}
