package com.lacuna.mod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Geometry helpers for {@code POSITION_TEX_COLOR} quad render types ({@link FxRenderTypes}). Every position is
 * camera-relative (the camera sits at the origin of the pose stack used during {@link FxManager} rendering).
 */
public final class FxDraw {
    private FxDraw() {}

    public static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, float alphaMul) {
        int a = (int) (((argb >>> 24) & 0xFF) * Mth.clamp(alphaMul, 0.0F, 1.0F));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float vv, int argb) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setUv(u, vv).setColor(argb);
    }

    /**
     * Camera-facing ribbon from {@code a} to {@code b} with full width {@code width}; u runs 0..1 across, v from
     * {@code vA} to {@code vB} along the ribbon (for the beam shader pass the distance in blocks).
     */
    public static void ribbon(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, float widthA, float widthB,
                              int argbA, int argbB, float vA, float vB) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 sideA = side(dir, a).scale(widthA * 0.5);
        Vec3 sideB = side(dir, b).scale(widthB * 0.5);
        v(vc, pose, a.x - sideA.x, a.y - sideA.y, a.z - sideA.z, 0.0F, vA, argbA);
        v(vc, pose, a.x + sideA.x, a.y + sideA.y, a.z + sideA.z, 1.0F, vA, argbA);
        v(vc, pose, b.x + sideB.x, b.y + sideB.y, b.z + sideB.z, 1.0F, vB, argbB);
        v(vc, pose, b.x - sideB.x, b.y - sideB.y, b.z - sideB.z, 0.0F, vB, argbB);
    }

    public static void ribbon(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, float width, int argb, float vA, float vB) {
        ribbon(pose, vc, a, b, width, width, argb, argb, vA, vB);
    }

    /** Unit vector perpendicular to {@code dir} and to the view direction at {@code at} (camera at origin). */
    private static Vec3 side(Vec3 dir, Vec3 at) {
        Vec3 s = dir.cross(at);
        if (s.lengthSqr() < 1.0E-8) {
            s = dir.cross(Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        return s.normalize();
    }

    /** Camera-facing square of half-size {@code size} at {@code p}, rotated by {@code roll} radians; uv 0..1. */
    public static void billboard(PoseStack.Pose pose, VertexConsumer vc, Camera camera, Vec3 p, float size, float roll, int argb) {
        Quaternionf q = camera.rotation();
        float[][] corners = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}};
        float cos = Mth.cos(roll);
        float sin = Mth.sin(roll);
        for (float[] c : corners) {
            Vector3f vec = new Vector3f(c[0] * cos - c[1] * sin, c[0] * sin + c[1] * cos, 0.0F);
            vec.rotate(q).mul(size);
            v(vc, pose, p.x + vec.x(), p.y + vec.y(), p.z + vec.z(), c[2], c[3], argb);
        }
    }

    /** Flat quad centred at {@code c} spanning ±{@code u} and ±{@code w} (half-axes); uv 0..1. */
    public static void quad(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, Vec3 u, Vec3 w, int argb) {
        v(vc, pose, c.x - u.x - w.x, c.y - u.y - w.y, c.z - u.z - w.z, 0.0F, 0.0F, argb);
        v(vc, pose, c.x - u.x + w.x, c.y - u.y + w.y, c.z - u.z + w.z, 0.0F, 1.0F, argb);
        v(vc, pose, c.x + u.x + w.x, c.y + u.y + w.y, c.z + u.z + w.z, 1.0F, 1.0F, argb);
        v(vc, pose, c.x + u.x - w.x, c.y + u.y - w.y, c.z + u.z - w.z, 1.0F, 0.0F, argb);
    }

    /** Horizontal square of half-size {@code half} at {@code c} (for rings and ground discs). */
    public static void ground(PoseStack.Pose pose, VertexConsumer vc, Vec3 c, float half, int argb) {
        quad(pose, vc, c, new Vec3(half, 0, 0), new Vec3(0, 0, half), argb);
    }

    /**
     * Open tube (no caps) of {@code sides} faces from {@code a} to {@code b}; u wraps around (0..1 per face so masks
     * like {@link FxRenderTypes#BEAM} read as a soft edge), v from {@code vA} to {@code vB}.
     */
    public static void tube(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, float radiusA, float radiusB, int sides,
                            int argbA, int argbB, float vA, float vB) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 axis = dir.normalize();
        Vec3 e1 = axis.cross(Math.abs(axis.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 e2 = axis.cross(e1).normalize();
        for (int i = 0; i < sides; i++) {
            double t0 = Math.PI * 2 * i / sides;
            double t1 = Math.PI * 2 * (i + 1) / sides;
            Vec3 o0 = e1.scale(Math.cos(t0)).add(e2.scale(Math.sin(t0)));
            Vec3 o1 = e1.scale(Math.cos(t1)).add(e2.scale(Math.sin(t1)));
            Vec3 a0 = a.add(o0.scale(radiusA));
            Vec3 a1 = a.add(o1.scale(radiusA));
            Vec3 b0 = b.add(o0.scale(radiusB));
            Vec3 b1 = b.add(o1.scale(radiusB));
            v(vc, pose, a0.x, a0.y, a0.z, 0.0F, vA, argbA);
            v(vc, pose, a1.x, a1.y, a1.z, 1.0F, vA, argbA);
            v(vc, pose, b1.x, b1.y, b1.z, 1.0F, vB, argbB);
            v(vc, pose, b0.x, b0.y, b0.z, 0.0F, vB, argbB);
        }
    }
}
