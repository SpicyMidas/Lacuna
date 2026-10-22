package com.lacuna.mod.client.gear;

import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.client.fx.GroundDecal;
import com.lacuna.mod.gear.EmberWave;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Ember Triangle dust (client): a pulverized body turned into a cloud of ash grains, flakes and glowing ember specks.
 * <p>One {@link EmberWave#DUST_FX} payload arrives while the victim is still alive. Its own renderer draws it once into a
 * {@link Capture} buffer instead of the screen - the real rendered body: model pose and animation of this very moment,
 * body rotation, scale, baby proportions, armour and layers - and the grains are scattered over those surfaces,
 * area-weighted, so the cloud starts as the creature's exact shape (a player-shaped cloud for players, zombies,
 * skeletons...). Without the entity or a drawable body, a humanoid-proportioned silhouette over its bounding box is used.
 * <p>The shape holds for 6-10 ticks, shimmering with heat while its ember specks flicker, then the grains peel off from
 * the outside in: they drift away from the wave centre and rise on hot air through a curl-noise wind, slowing, cooling
 * from orange to grey and fading over two to three seconds, while a few heavier flakes fall and a little puff of ash
 * rolls out from the feet. The dead body itself never shows a death animation: the server removes mobs at once and
 * dead players stay hidden here until the dust has gone.
 */
public final class EmberDust {
    /** Ash palette the grains cool to (tinting pale sprites): charcoal, dark ash, ash grey, pale ash. */
    private static final int[] ASH = {0x2B2623, 0x4A4441, 0x7A7570, 0xA8A39D};
    private static final int[] ASH_WEIGHTS = {25, 30, 30, 15};
    private static final int SPECK_ASH = 0x6E6863;
    private static final int GLOW_ORANGE = 0xFF7A1E;
    private static final int GLOW_DEEP = 0xD9400E;
    /** Grain budget per game tick across all bodies (a wave can pulverize a whole crowd at once). */
    private static final int GRAIN_BUDGET = 4000;
    private static final Map<Integer, Long> HIDDEN = new HashMap<>();

    @Nullable
    private static SpriteSet dustSprites;
    @Nullable
    private static SpriteSet speckSprites;
    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetUsed;

    private EmberDust() {}

    /** Called from FxParticles: registers both sprite sets and keeps them for the hand-built particles below. */
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.EMBER_DUST.get(), sprites -> {
            dustSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> loose(level, x, y, z, dx, dy, dz, sprites, false);
        });
        event.registerSpriteSet(ModParticles.EMBER_SPECK.get(), sprites -> {
            speckSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> loose(level, x, y, z, dx, dy, dz, sprites, true);
        });
    }

    /** Particle density multiplier from the video setting (the grains are added directly to the particle engine). */
    static float particleScale(Minecraft mc) {
        return switch (mc.options.particles().get()) {
            case DECREASED -> 0.5F;
            case MINIMAL -> 0.2F;
            default -> 1.0F;
        };
    }

    private static int budget(ClientLevel level, int want) {
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        int give = Math.max(Math.min(60, want), Math.min(want, GRAIN_BUDGET - budgetUsed));
        budgetUsed += give;
        return give;
    }

    // ------------------------------------------------------------------ the pulverized body

    public static void spawn(FxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || dustSprites == null || speckSprites == null) {
            return;
        }
        boolean shed = p.p2() >= 0.5F;
        Entity entity = level.getEntity(p.entityId());
        Vec3 feet = entity != null ? entity.position() : p.a();
        double far = mc.gameRenderer.getMainCamera().getPosition().distanceTo(feet);
        if (far > 96.0) {
            return;
        }
        float w = Math.max(0.2F, p.p0());
        float h = Math.max(0.2F, p.p1());
        float scale = particleScale(mc);
        int count = (int) (Mth.clamp(240.0F + 200.0F * w * h, 250.0F, 600.0F) * scale * (shed ? 0.3F : 1.0F));
        if (far > 32.0) {
            count = (int) (count * Math.max(0.35, 1.0 - (far - 32.0) / 64.0));
        }
        count = budget(level, count);
        RandomSource r = level.random;
        List<Vec3> points = entity != null ? sampleRendered(entity, count, r) : null;
        if (points == null || points.isEmpty()) {
            points = silhouette(feet, w, h, Float.intBitsToFloat(p.color()), count, r);
        }
        if (points.isEmpty()) {
            return;
        }
        crumble(level, points, p.b(), w, h, shed, r);
        if (!shed) {
            puff(level, feet, w, scale, r);
            if (entity != null) {
                long now = level.getGameTime();
                if (HIDDEN.size() > 32) {
                    HIDDEN.values().removeIf(until -> until < now);
                }
                HIDDEN.put(entity.getId(), now + 80L);
            }
        }
        Vec3[] spots = new Vec3[Math.min(points.size(), shed ? 4 : 7)];
        for (int i = 0; i < spots.length; i++) {
            spots[i] = points.get(r.nextInt(points.size()));
        }
        FxManager.add(new Glow(spots, feet, (float) Math.sqrt(w * h), shed));
    }

    /** Dead pulverized bodies are never drawn (a dead player lies there until respawn; the dust replaces it). */
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (HIDDEN.isEmpty()) {
            return;
        }
        LivingEntity e = event.getEntity();
        Long until = HIDDEN.get(e.getId());
        if (until == null) {
            return;
        }
        if (e.level().getGameTime() > until) {
            HIDDEN.remove(e.getId());
        } else if (e.isDeadOrDying()) {
            event.setCanceled(true);
        }
    }

    /** The grains: hold the shape, then peel off from the outside in and blow away from the wave. */
    private static void crumble(ClientLevel level, List<Vec3> points, Vec3 waveCentre, float w, float h, boolean shed, RandomSource r) {
        double cx = 0.0;
        double cy = 0.0;
        double cz = 0.0;
        for (Vec3 v : points) {
            cx += v.x;
            cy += v.y;
            cz += v.z;
        }
        Vec3 centroid = new Vec3(cx / points.size(), cy / points.size(), cz / points.size());
        double maxD = 1.0E-3;
        for (Vec3 v : points) {
            maxD = Math.max(maxD, v.distanceTo(centroid));
        }
        Vec3 away = new Vec3(centroid.x - waveCentre.x, 0.0, centroid.z - waveCentre.z);
        away = away.lengthSqr() > 1.0E-4 ? away.normalize() : new Vec3(r.nextDouble() - 0.5, 0.0, r.nextDouble() - 0.5).normalize();
        float size = (float) Mth.clamp(Math.sqrt(w * h) / 1.05, 0.75, 2.0);
        for (Vec3 v : points) {
            double d = v.distanceTo(centroid) / maxD;
            Spec s = new Spec();
            float roll = r.nextFloat();
            s.speck = roll < 0.14F;
            boolean flake = !s.speck && roll < 0.21F;
            // outside in: the extremities (head, hands, feet) let go first, the core last
            s.hold = shed ? r.nextInt(4) : 6 + r.nextInt(5) + (int) ((1.0 - d) * 12.0) + r.nextInt(3);
            Vec3 radial = new Vec3(v.x - centroid.x, 0.0, v.z - centroid.z);
            radial = radial.lengthSqr() > 1.0E-4 ? radial.normalize() : Vec3.ZERO;
            double out = 0.035 + 0.05 * r.nextDouble();
            double spread = 0.02 + 0.03 * r.nextDouble();
            s.vx = away.x * out + radial.x * spread + (r.nextDouble() - 0.5) * 0.02;
            s.vy = 0.012 + 0.03 * r.nextDouble();
            s.vz = away.z * out + radial.z * spread + (r.nextDouble() - 0.5) * 0.02;
            if (s.speck) {
                s.heat = 0.85F + 0.15F * r.nextFloat();
                s.size = (0.032F + 0.016F * r.nextFloat()) * size;
                s.life = 22 + r.nextInt(18);
                s.cooling = 0.91F;
                s.buoyancy = 0.0022F;
                s.turbulence = 0.0035F;
                s.alpha = 1.0F;
                s.cool(SPECK_ASH);
            } else if (flake) {
                // heavier flakes: barely warm, they tumble and fall
                s.heat = 0.15F * r.nextFloat();
                s.size = (0.075F + 0.035F * r.nextFloat()) * size;
                s.life = 40 + r.nextInt(22);
                s.fall = 0.010F + 0.006F * r.nextFloat();
                s.buoyancy = 0.0F;
                s.drag = 0.93F;
                s.turbulence = 0.0018F;
                s.spin = 0.12F + 0.1F * r.nextFloat();
                s.physics = true;
                s.vy = 0.01 + 0.02 * r.nextDouble();
                s.cool(ASH[r.nextInt(2)]);
            } else {
                s.heat = 0.08F + 0.3F * r.nextFloat();
                s.size = (0.048F + 0.032F * r.nextFloat()) * size;
                s.life = 34 + r.nextInt(26);
                s.buoyancy = 0.0012F + 0.001F * r.nextFloat();
                s.turbulence = 0.0026F + 0.0016F * r.nextFloat();
                s.spin = 0.02F * r.nextFloat();
                s.cool(ashShade(r));
            }
            if (shed) {
                s.life = (int) (s.life * 0.7F);
            }
            Minecraft.getInstance().particleEngine.add(new Grain(level, v.x, v.y, v.z, s.speck ? speckSprites : dustSprites, s));
        }
    }

    /** A little puff of ash rolling out along the ground from the feet. */
    private static void puff(ClientLevel level, Vec3 feet, float w, float scale, RandomSource r) {
        int n = Math.max(4, (int) (16 * scale));
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double rad = w * (0.25 + 0.3 * r.nextDouble());
            double sp = 0.07 + 0.08 * r.nextDouble();
            ash(level, feet.x + Math.cos(a) * rad, feet.y + 0.05, feet.z + Math.sin(a) * rad, Math.cos(a) * sp,
                    0.015 + 0.03 * r.nextDouble(), Math.sin(a) * sp);
        }
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, feet.x + (r.nextDouble() - 0.5) * w, feet.y + 0.1,
                    feet.z + (r.nextDouble() - 0.5) * w, 0.0, 0.03, 0.0);
        }
    }

    private static int ashShade(RandomSource r) {
        int pick = r.nextInt(100);
        for (int i = 0; i < ASH.length; i++) {
            pick -= ASH_WEIGHTS[i];
            if (pick < 0) {
                return ASH[i];
            }
        }
        return ASH[ASH.length - 1];
    }

    /** A glowing ember spark (wave front, glyph): it arcs up, falls and cools to grey. */
    public static void spark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        if (speckSprites == null) {
            return;
        }
        RandomSource r = level.random;
        Spec s = new Spec();
        s.speck = true;
        s.heat = 1.0F;
        s.life = 12 + r.nextInt(14);
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        s.fall = 0.03F;
        s.buoyancy = 0.0F;
        s.drag = 0.96F;
        s.cooling = 0.9F;
        s.turbulence = 0.0F;
        s.size = 0.035F + 0.02F * r.nextFloat();
        s.alpha = 1.0F;
        s.physics = true;
        s.cool(SPECK_ASH);
        Minecraft.getInstance().particleEngine.add(new Grain(level, x, y, z, speckSprites, s));
    }

    /** A warm ash grain blown along by the wave (or the puff at a body's feet). */
    public static void ash(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        if (dustSprites == null) {
            return;
        }
        RandomSource r = level.random;
        Spec s = new Spec();
        s.heat = 0.2F + 0.3F * r.nextFloat();
        s.life = 16 + r.nextInt(16);
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        s.drag = 0.88F;
        s.buoyancy = 0.0015F;
        s.turbulence = 0.002F;
        s.size = 0.045F + 0.03F * r.nextFloat();
        s.cool(ashShade(r));
        Minecraft.getInstance().particleEngine.add(new Grain(level, x, y, z, dustSprites, s));
    }

    /** /particle lacuna:ember_dust | ember_speck: a loose grain with the given velocity. */
    private static Grain loose(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
                               boolean speck) {
        RandomSource r = level.random;
        Spec s = new Spec();
        s.speck = speck;
        s.heat = speck ? 1.0F : 0.3F;
        s.life = 30 + r.nextInt(20);
        s.vx = dx;
        s.vy = dy;
        s.vz = dz;
        s.size = speck ? 0.04F : 0.06F;
        s.cool(speck ? SPECK_ASH : ashShade(r));
        return new Grain(level, x, y, z, sprites, s);
    }

    // ------------------------------------------------------------------ the body's shape

    /** Points on the entity's rendered surfaces (world space), or null when its renderer cannot be captured. */
    @Nullable
    static List<Vec3> sampleRendered(Entity entity, int count, RandomSource r) {
        try {
            EntityRenderer<? super Entity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            Capture capture = new Capture();
            PoseStack pose = new PoseStack();
            Vec3 offset = renderer.getRenderOffset(entity, 1.0F);
            pose.translate(offset.x, offset.y, offset.z);
            renderer.render(entity, entity.getYRot(), 1.0F, pose, capture, LightTexture.FULL_BRIGHT);
            return scatter(capture.quads, entity.position(), count, r);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * Fallback shape over the bounding box (feet at {@code feet}, facing {@code yawDeg}): humanoid proportions (legs,
     * torso, arms, head; the player model's 32 pixels stretched over the height) for upright creatures, else a
     * rounded body with the head forward.
     */
    static List<Vec3> silhouette(Vec3 feet, float w, float h, float yawDeg, int count, RandomSource r) {
        List<float[]> quads = new ArrayList<>();
        if (h >= w * 1.4F) {
            float u = h / 32.0F;
            box(quads, -4 * u, 0, -2 * u, 0, 12 * u, 2 * u);
            box(quads, 0, 0, -2 * u, 4 * u, 12 * u, 2 * u);
            box(quads, -4 * u, 12 * u, -2 * u, 4 * u, 24 * u, 2 * u);
            box(quads, -8 * u, 12 * u, -2 * u, -4 * u, 24 * u, 2 * u);
            box(quads, 4 * u, 12 * u, -2 * u, 8 * u, 24 * u, 2 * u);
            box(quads, -4 * u, 24 * u, -4 * u, 4 * u, 32 * u, 4 * u);
        } else {
            box(quads, -0.45F * w, 0.1F * h, -0.5F * w, 0.45F * w, 0.8F * h, 0.4F * w);
            box(quads, -0.25F * w, 0.35F * h, 0.35F * w, 0.25F * w, 0.95F * h, 0.75F * w);
        }
        // local x = sideways, z = forward (yaw 0 faces +z)
        double a = Math.toRadians(Float.isFinite(yawDeg) ? yawDeg : 0.0F);
        float fx = (float) -Math.sin(a);
        float fz = (float) Math.cos(a);
        for (float[] q : quads) {
            for (int k = 0; k < 4; k++) {
                float x = q[k * 3];
                float z = q[k * 3 + 2];
                q[k * 3] = x * fz + z * fx;
                q[k * 3 + 2] = x * -fx + z * fz;
            }
        }
        return scatter(quads, feet, count, r);
    }

    /** The six faces of an axis-aligned box as quads (corners in order around each face). */
    private static void box(List<float[]> quads, float x0, float y0, float z0, float x1, float y1, float z1) {
        quads.add(new float[] {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1});
        quads.add(new float[] {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1});
        quads.add(new float[] {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0});
        quads.add(new float[] {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1});
        quads.add(new float[] {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0});
        quads.add(new float[] {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0});
    }

    /** Area-weighted random points on the quads (12 floats each, relative to {@code origin}), with a little depth. */
    static List<Vec3> scatter(List<float[]> quads, Vec3 origin, int count, RandomSource r) {
        int n = quads.size();
        if (n == 0 || count <= 0) {
            return List.of();
        }
        double[] cumulative = new double[n];
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            float[] q = quads.get(i);
            double ax = q[3] - q[0];
            double ay = q[4] - q[1];
            double az = q[5] - q[2];
            double bx = q[9] - q[0];
            double by = q[10] - q[1];
            double bz = q[11] - q[2];
            double cx = ay * bz - az * by;
            double cy = az * bx - ax * bz;
            double cz = ax * by - ay * bx;
            total += Math.sqrt(cx * cx + cy * cy + cz * cz);
            cumulative[i] = total;
        }
        if (total < 1.0E-6) {
            return List.of();
        }
        List<Vec3> out = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            int i = Arrays.binarySearch(cumulative, r.nextDouble() * total);
            float[] q = quads.get(Math.min(i < 0 ? -i - 1 : i, n - 1));
            double s = r.nextDouble();
            double t = r.nextDouble();
            double x = q[0] + (q[3] - q[0]) * s + (q[9] - q[0]) * t;
            double y = q[1] + (q[4] - q[1]) * s + (q[10] - q[1]) * t;
            double z = q[2] + (q[5] - q[2]) * s + (q[11] - q[2]) * t;
            out.add(new Vec3(origin.x + x + (r.nextDouble() - 0.5) * 0.04, origin.y + y + (r.nextDouble() - 0.5) * 0.04,
                    origin.z + z + (r.nextDouble() - 0.5) * 0.04));
        }
        return out;
    }

    /**
     * A buffer source that records the quads an entity renderer draws instead of drawing them. Name tags, lines and
     * strips (leashes, hitboxes), enchantment glint and anything textured from an atlas (held items and blocks, trims)
     * are not the body and are skipped.
     */
    private static final class Capture implements MultiBufferSource {
        final List<float[]> quads = new ArrayList<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            String desc = type.toString();
            String name = desc.startsWith("RenderType[") ? desc.substring("RenderType[".length()) : desc;
            if (type.mode() != VertexFormat.Mode.QUADS || name.startsWith("text") || name.contains("glint")
                    || desc.contains("textures/atlas/")) {
                return new QuadSink(null);
            }
            // a fresh sink per request: layered consumers (foil) may write one vertex to two sinks
            return new QuadSink(quads);
        }
    }

    /** Collects every 4 vertices into one quad (positions only). */
    private static final class QuadSink implements VertexConsumer {
        @Nullable
        private final List<float[]> out;
        private final float[] current = new float[12];
        private int corner;

        QuadSink(@Nullable List<float[]> out) {
            this.out = out;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (out != null) {
                current[corner * 3] = x;
                current[corner * 3 + 1] = y;
                current[corner * 3 + 2] = z;
                if (++corner == 4) {
                    corner = 0;
                    out.add(current.clone());
                }
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    // ------------------------------------------------------------------ wind

    private static final double[] PHASE = {0.0, 1.7, 3.1, 4.4, 2.3, 5.2};

    /**
     * Divergence-free turbulence: the curl of a smooth periodic vector potential, two octaves, written into
     * {@code out}. Neighbouring grains share the same swirls, so the dust moves like smoke instead of jittering.
     */
    static void curl(double x, double y, double z, double t, double[] out) {
        out[0] = 0.0;
        out[1] = 0.0;
        out[2] = 0.0;
        octave(x, y, z, t, 1.3F, 1.0F, out);
        octave(x, y, z, t * 1.7, 3.1F, 0.45F, out);
    }

    private static void octave(double x, double y, double z, double t, float f, float amp, double[] out) {
        // psi = (sin(f y + t) cos(.8 f z), sin(f z + t) cos(.8 f x), sin(f x + t) cos(.8 f y)), with phase offsets
        float ax = (float) (f * y + PHASE[0] + t);
        float bx = (float) (0.8F * f * z + PHASE[1]);
        float ay = (float) (f * z + PHASE[2] + t);
        float by = (float) (0.8F * f * x + PHASE[3]);
        float az = (float) (f * x + PHASE[4] + t);
        float bz = (float) (0.8F * f * y + PHASE[5]);
        float dPzDy = -Mth.sin(az) * Mth.sin(bz) * 0.8F;
        float dPyDz = Mth.cos(ay) * Mth.cos(by);
        float dPxDz = -Mth.sin(ax) * Mth.sin(bx) * 0.8F;
        float dPzDx = Mth.cos(az) * Mth.cos(bz);
        float dPyDx = -Mth.sin(ay) * Mth.sin(by) * 0.8F;
        float dPxDy = Mth.cos(ax) * Mth.cos(bx);
        // (each derivative carries a factor f, divided out again so both octaves stay comparable)
        out[0] += (dPzDy - dPyDz) * amp;
        out[1] += (dPxDz - dPzDx) * amp;
        out[2] += (dPyDx - dPxDy) * amp;
    }

    // ------------------------------------------------------------------ the grain

    /** Behaviour of one grain; a plain bag of numbers filled in by the spawners above. */
    static final class Spec {
        boolean speck;
        boolean physics;
        int hold;
        int life = 40;
        double vx;
        double vy;
        double vz;
        float heat = 0.2F;
        float size = 0.06F;
        float alpha = 0.95F;
        float coolR = 0.48F;
        float coolG = 0.46F;
        float coolB = 0.44F;
        float buoyancy = 0.0016F;
        float fall;
        float drag = 0.95F;
        float cooling = 0.93F;
        float turbulence = 0.003F;
        float spin;

        void cool(int rgb) {
            coolR = ((rgb >> 16) & 0xFF) / 255.0F;
            coolG = ((rgb >> 8) & 0xFF) / 255.0F;
            coolB = (rgb & 0xFF) / 255.0F;
        }
    }

    /**
     * One ash grain, flake or ember speck. For {@code hold} ticks it stays where it was on the body (shimmering, the
     * specks flickering), then it flies: curl-noise wind, hot-air buoyancy (stronger while hot), drag, optional fall,
     * cooling from orange to its ash colour and fading in the second half of its flight. Hot grains glow (block light).
     */
    static final class Grain extends TextureSheetParticle {
        private static final double[] WIND = new double[3];
        private final Spec spec;
        private final double baseX;
        private final double baseY;
        private final double baseZ;
        private final float phase;
        private final float spin;
        private float heat;
        private float flicker = 1.0F;

        Grain(ClientLevel level, double x, double y, double z, SpriteSet sprites, Spec spec) {
            super(level, x, y, z);
            this.spec = spec;
            this.baseX = x;
            this.baseY = y;
            this.baseZ = z;
            this.xd = 0.0;
            this.yd = 0.0;
            this.zd = 0.0;
            this.lifetime = spec.hold + Math.max(1, spec.life);
            this.heat = spec.heat;
            this.hasPhysics = spec.physics;
            this.quadSize = spec.size;
            this.alpha = spec.alpha;
            this.phase = this.random.nextFloat() * 100.0F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.spin = spec.spin * (this.random.nextBoolean() ? 1.0F : -1.0F);
            if (spec.physics) {
                this.setSize(0.06F, 0.06F);
            }
            this.pickSprite(sprites);
            colour();
        }

        @Override
        public void tick() {
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            this.oRoll = this.roll;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            if (this.age <= spec.hold) {
                // still part of the body: a faint heat shimmer runs up it, the embers flicker
                float t = this.age + phase;
                double s = 0.008 + 0.012 * heat;
                this.setPos(baseX + s * Mth.sin(t * 0.9F + (float) baseY * 7.0F), baseY + s * 0.5 * Mth.sin(t * 1.3F),
                        baseZ + s * Mth.cos(t * 0.8F + (float) baseY * 6.0F));
                heat = Math.max(0.0F, heat - 0.004F);
                flicker = spec.speck ? 0.55F + 0.45F * this.random.nextFloat() : 0.92F + 0.08F * this.random.nextFloat();
                colour();
                return;
            }
            if (this.age == spec.hold + 1) {
                this.xd = spec.vx;
                this.yd = spec.vy;
                this.zd = spec.vz;
            }
            float since = this.age - spec.hold;
            if (spec.turbulence > 0.0F) {
                curl(this.x, this.y, this.z, (this.age + phase) * 0.045, WIND);
                double amp = spec.turbulence * Math.min(1.0, since / 10.0);
                this.xd += WIND[0] * amp;
                this.yd += WIND[1] * amp;
                this.zd += WIND[2] * amp;
            }
            this.yd += spec.buoyancy * (0.35F + heat) - spec.fall;
            this.xd *= spec.drag;
            this.yd *= spec.drag;
            this.zd *= spec.drag;
            this.move(this.xd, this.yd, this.zd);
            if (this.onGround) {
                this.xd *= 0.6;
                this.zd *= 0.6;
            }
            heat *= spec.cooling;
            flicker = spec.speck ? 0.75F + 0.25F * this.random.nextFloat() : 1.0F;
            this.roll += spin;
            colour();
            float tail = since / Math.max(1, spec.life);
            this.alpha = spec.alpha * (tail < 0.45F ? 1.0F : Math.max(0.0F, 1.0F - (tail - 0.45F) / 0.55F));
        }

        /** Hot: yellow-white to orange to deep red-orange; cold: the grain's ash colour. */
        private void colour() {
            float g = Mth.clamp(heat * flicker, 0.0F, 1.0F);
            float mix = Mth.clamp(g * 1.6F, 0.0F, 1.0F);
            this.rCol = Mth.lerp(mix, spec.coolR, 1.0F);
            this.gCol = Mth.lerp(mix, spec.coolG, 0.42F + 0.4F * g);
            this.bCol = Mth.lerp(mix, spec.coolB, 0.1F + 0.35F * g * g);
        }

        @Override
        public float getQuadSize(float partialTick) {
            float since = this.age + partialTick - spec.hold;
            if (since <= 0.0F) {
                return this.quadSize;
            }
            float tail = Mth.clamp(since / Math.max(1, spec.life), 0.0F, 1.0F);
            return this.quadSize * (spec.speck ? 1.0F - 0.5F * tail : 1.0F - 0.25F * tail);
        }

        @Override
        protected int getLightColor(float partialTick) {
            int base = super.getLightColor(partialTick);
            int glow = (int) (Mth.clamp(heat * flicker * (spec.speck ? 1.6F : 1.0F), 0.0F, 1.0F) * 240.0F);
            return (base & 0xFFFF0000) | Math.max(base & 0xFFFF, glow);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------ the heat glow over the body

    /** Soft orange glows over the crumbling shape and a hot patch on the ground under it, dying with the heat. */
    private static final class Glow implements ClientFx {
        private final Vec3[] spots;
        private final Vec3 feet;
        private final float size;
        private final boolean shed;
        private final int life;
        /** The ground under the body (the hot patch lies on it). */
        private final GroundDecal decal = new GroundDecal();
        private int age;

        Glow(Vec3[] spots, Vec3 feet, float bodySize, boolean shed) {
            this.spots = spots;
            this.feet = feet;
            this.size = 0.3F + 0.3F * bodySize;
            this.shed = shed;
            this.life = shed ? 10 : 20;
        }

        @Override
        public boolean tick() {
            return ++age < life;
        }

        @Override
        public void render(FxRenderContext ctx) {
            float time = ctx.time(age);
            float fade = 1.0F - Mth.clamp(time / life, 0.0F, 1.0F);
            fade *= fade;
            if (fade <= 0.0F) {
                return;
            }
            PoseStack.Pose pose = ctx.pose().last();
            VertexConsumer vc = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.GLOW));
            for (int i = 0; i < spots.length; i++) {
                float flick = 0.75F + 0.25F * Mth.sin(time * 1.7F + i * 2.1F);
                FxDraw.billboard(pose, vc, ctx.camera(), ctx.rel(spots[i]), size * (0.85F + 0.15F * flick), 0.0F,
                        FxDraw.argb((shed ? 0.16F : 0.26F) * fade * flick, GLOW_ORANGE));
            }
            if (!shed) {
                decal.at(ctx.level(), feet, 0.5 + size).square(ctx, vc, feet.x, feet.z, 0.5F + size, 0.0F, FxDraw.argb(0.4F * fade, GLOW_DEEP));
            }
        }
    }
}
