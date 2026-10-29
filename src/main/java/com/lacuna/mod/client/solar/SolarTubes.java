package com.lacuna.mod.client.solar;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.compat.ShaderPackCompat;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The shafts of light of Sun's Fury as real 3D volumes: the strike pillar's nested shells (haze, halo, sunbeam body,
 * warm and pale layers, white-hot core, the splash cone at its foot) and the focus' converging sunbeams and focus beam.
 * <p>
 * One closed tube mesh (16 sides, 48 rings, both end caps) is built once into a static GPU buffer and reused for every
 * shaft: the {@code lacuna:solar_tube} vertex shader bends and scales it onto the shaft's axis (a quadratic Bezier
 * curve, straight for the pillar) and radii, and the fragment shader integrates the light along each view ray through
 * the round volume against the scene depth ({@link SolarDepth}; at most one depth copy per frame, none under
 * Fabulous). Only back faces are drawn, so every pixel of the tube's footprint is shaded exactly once whether the eye
 * is outside the shaft or inside it (looking straight up the pillar from within). Additive, no depth test, no depth
 * writes, after the level ({@link SolarVolume#drawLate}).
 * <p>
 * Shafts are queued during the after-particles pass ({@link #straight}, {@link #curve}; fixed arrays, no allocation)
 * and drawn in the late pass. When the shader did not load (or the queue is full) the callers fall back to their
 * camera-facing ribbons.
 */
final class SolarTubes {
    /** Profiles ({@code TubeMode.x}): the pillar's core / body / halo, or the soft hazy sunbeam. */
    static final int PILLAR = 0;
    static final int SUNBEAM = 1;
    /** Flags ({@code TubeMode.y}): sunlight from the sky's sun disc (no distance dimming). */
    static final int SKY = 2;
    /** Flag: fades out where it passes within a few blocks of the eye (a shaft right past the eyes never fills the screen). */
    static final int NEAR_FADE = 4;

    private static final int SIDES = 16;
    private static final int RINGS = 48;
    private static final int MAX = 96;
    private static final int STRIDE = 23;
    private static final float[] DATA = new float[MAX * STRIDE];
    private static int count;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static VertexBuffer mesh;
    private static final Vector3f SCRATCH = new Vector3f();

    private SolarTubes() {}

    static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id("solar_tube"), DefaultVertexFormat.POSITION),
                    s -> shader = s);
        } catch (IOException | RuntimeException e) {
            shader = null;
            Lacuna.LOGGER.warn("Lacuna solar_tube shader unavailable, the shafts of light stay flat ribbons: {}", e.toString());
        }
    }

    static boolean pending() {
        return count > 0;
    }

    static void clear() {
        count = 0;
    }

    /** A straight shaft from {@code a} to {@code b} (pose space), radius {@code ra} at {@code a} and {@code rb} at {@code b}. */
    static boolean straight(PoseStack.Pose pose, Vec3 a, Vec3 b, float ra, float rb, int argbA, int argbB, float va, float vb, int profile,
                            int flags) {
        return curve(pose, a.x, a.y, a.z, (a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5, b.x, b.y, b.z, ra, rb, argbA, argbB, va,
                vb, profile, flags);
    }

    /**
     * A shaft along the quadratic Bezier curve {@code a -> k -> b} (pose space), radius {@code ra} at {@code a} and
     * {@code rb} at {@code b}, colour and intensity (ARGB) from {@code argbA} to {@code argbB}, v (blocks along, for
     * the motion of the light) from {@code va} to {@code vb}. False when it cannot be drawn in 3D (draw the ribbon).
     */
    static boolean curve(PoseStack.Pose pose, double ax, double ay, double az, double kx, double ky, double kz, double bx, double by,
                         double bz, float ra, float rb, int argbA, int argbB, float va, float vb, int profile, int flags) {
        if (!ShaderPackCompat.usable(shader) || count >= MAX) {
            return false;
        }
        if (Math.max(argbA >>> 24, argbB >>> 24) == 0 || Math.max(ra, rb) <= 1.0E-3F) {
            return true;
        }
        int o = count * STRIDE;
        Matrix4f m = pose.pose();
        put(m, o, ax, ay, az);
        put(m, o + 3, kx, ky, kz);
        put(m, o + 6, bx, by, bz);
        DATA[o + 9] = Math.max(ra, 1.0E-3F);
        DATA[o + 10] = Math.max(rb, 1.0E-3F);
        colour(o + 11, argbA);
        colour(o + 15, argbB);
        DATA[o + 19] = va;
        DATA[o + 20] = vb;
        DATA[o + 21] = profile;
        DATA[o + 22] = flags;
        count++;
        return true;
    }

    private static void put(Matrix4f m, int o, double x, double y, double z) {
        m.transformPosition((float) x, (float) y, (float) z, SCRATCH);
        DATA[o] = SCRATCH.x;
        DATA[o + 1] = SCRATCH.y;
        DATA[o + 2] = SCRATCH.z;
    }

    private static void colour(int o, int argb) {
        DATA[o] = ((argb >> 16) & 0xFF) / 255.0F;
        DATA[o + 1] = ((argb >> 8) & 0xFF) / 255.0F;
        DATA[o + 2] = (argb & 0xFF) / 255.0F;
        DATA[o + 3] = (argb >>> 24) / 255.0F;
    }

    /** Late pass (depth prepared, model-view of the frame restored): draws the queued shafts and empties the queue. */
    static void draw(Matrix4f modelView) {
        ShaderInstance s = shader;
        if (count == 0 || s == null) {
            count = 0;
            return;
        }
        VertexBuffer tube = mesh();
        SolarDepth.bind(s);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_FRONT);
        tube.bind();
        try {
            for (int i = 0; i < count; i++) {
                int o = i * STRIDE;
                s.safeGetUniform("TubeA").set(DATA[o], DATA[o + 1], DATA[o + 2]);
                s.safeGetUniform("TubeK").set(DATA[o + 3], DATA[o + 4], DATA[o + 5]);
                s.safeGetUniform("TubeB").set(DATA[o + 6], DATA[o + 7], DATA[o + 8]);
                s.safeGetUniform("TubeRadius").set(DATA[o + 9], DATA[o + 10]);
                s.safeGetUniform("TubeColorA").set(DATA[o + 11], DATA[o + 12], DATA[o + 13], DATA[o + 14]);
                s.safeGetUniform("TubeColorB").set(DATA[o + 15], DATA[o + 16], DATA[o + 17], DATA[o + 18]);
                s.safeGetUniform("TubeV").set(DATA[o + 19], DATA[o + 20]);
                s.safeGetUniform("TubeMode").set(DATA[o + 21], DATA[o + 22]);
                tube.drawWithShader(modelView, projection, s);
            }
        } finally {
            count = 0;
            VertexBuffer.unbind();
            GL11.glCullFace(GL11.GL_BACK);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    /**
     * The unit tube, built once: x, y on the unit circle (0, 0 at a cap's centre), z 0..1 along; outward faces wind
     * counter-clockwise (side quads: around the axis then along it; caps facing out of each end).
     */
    private static VertexBuffer mesh() {
        if (mesh != null && !mesh.isInvalid()) {
            return mesh;
        }
        float[] cos = new float[SIDES + 1];
        float[] sin = new float[SIDES + 1];
        for (int i = 0; i <= SIDES; i++) {
            double a = Math.PI * 2.0 * (i % SIDES) / SIDES;
            cos[i] = (float) Math.cos(a);
            sin[i] = (float) Math.sin(a);
        }
        try (ByteBufferBuilder bytes = new ByteBufferBuilder((SIDES * RINGS * 6 + SIDES * 6) * 12)) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            for (int j = 0; j < RINGS; j++) {
                float z0 = (float) j / RINGS;
                float z1 = (float) (j + 1) / RINGS;
                for (int i = 0; i < SIDES; i++) {
                    b.addVertex(cos[i], sin[i], z0);
                    b.addVertex(cos[i + 1], sin[i + 1], z0);
                    b.addVertex(cos[i + 1], sin[i + 1], z1);
                    b.addVertex(cos[i], sin[i], z0);
                    b.addVertex(cos[i + 1], sin[i + 1], z1);
                    b.addVertex(cos[i], sin[i], z1);
                }
            }
            for (int i = 0; i < SIDES; i++) {
                b.addVertex(0.0F, 0.0F, 1.0F);
                b.addVertex(cos[i], sin[i], 1.0F);
                b.addVertex(cos[i + 1], sin[i + 1], 1.0F);
                b.addVertex(0.0F, 0.0F, 0.0F);
                b.addVertex(cos[i + 1], sin[i + 1], 0.0F);
                b.addVertex(cos[i], sin[i], 0.0F);
            }
            MeshData data = b.buildOrThrow();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(data);
            VertexBuffer.unbind();
            mesh = vbo;
        }
        return mesh;
    }
}
