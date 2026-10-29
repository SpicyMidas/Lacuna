package com.lacuna.mod.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * A ring-by-ring surface ({@code rows} rings of {@code sides} vertices) for the {@code tide_vortex} shader, allocated
 * once per effect and updated in place each frame: camera-relative position, normal and colour per vertex, one v per
 * ring; u runs 0..1 round the ring (the seam gets u = 1, so the shader's ring-periodic noise closes). Also emits the
 * effects' loose quads (droplets, mist puffs) in the same batch. Without the shader every vertex is shaded on the CPU
 * ({@link TideWater#fallback}) and the uv points at the middle of the beam mask, like the wave's fill.
 */
final class TideMesh {
    static final int CORE = 0;
    static final int SHEATH = 1;
    static final int CANOPY = 2;
    static final int POOL = 3;
    static final int SKIRT = 4;
    static final int FLAT = 5;
    static final int PUFF = 6;
    static final int JET = 7;
    static final int FALL = 8;
    /** Vertices under this alpha (0..255) are treated as empty: quads made only of them are skipped. */
    private static final int EMPTY = 3;

    final int rows;
    final int sides;
    final int mode;
    final float[] cos;
    final float[] sin;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] nx;
    private final float[] ny;
    private final float[] nz;
    private final int[] argb;
    private final float[] v;

    TideMesh(int rows, int sides, int mode) {
        this.rows = rows;
        this.sides = sides;
        this.mode = mode;
        this.cos = new float[sides];
        this.sin = new float[sides];
        for (int i = 0; i < sides; i++) {
            cos[i] = Mth.cos(Mth.TWO_PI * i / sides);
            sin[i] = Mth.sin(Mth.TWO_PI * i / sides);
        }
        int n = rows * sides;
        this.x = new float[n];
        this.y = new float[n];
        this.z = new float[n];
        this.nx = new float[n];
        this.ny = new float[n];
        this.nz = new float[n];
        this.argb = new int[n];
        this.v = new float[rows];
    }

    void set(int row, int i, double px, double py, double pz, double qx, double qy, double qz, int colour) {
        int k = row * sides + i;
        x[k] = (float) px;
        y[k] = (float) py;
        z[k] = (float) pz;
        nx[k] = (float) qx;
        ny[k] = (float) qy;
        nz[k] = (float) qz;
        argb[k] = colour;
    }

    void v(int row, float value) {
        v[row] = value;
    }

    /** The surface between rings {@code from} .. {@code to}. */
    void emit(Emitter e, int from, int to) {
        float base = mode * 2.0F;
        for (int row = from; row < to; row++) {
            for (int i = 0; i < sides; i++) {
                int a = row * sides + i;
                int b = row * sides + (i + 1) % sides;
                int c = b + sides;
                int d = a + sides;
                if (Math.max(Math.max(argb[a] >>> 24, argb[b] >>> 24), Math.max(argb[c] >>> 24, argb[d] >>> 24)) < EMPTY) {
                    continue;
                }
                float u0 = base + (float) i / sides;
                float u1 = base + (float) (i + 1) / sides;
                e.vertex(x[a], y[a], z[a], u0, v[row], argb[a], nx[a], ny[a], nz[a], mode);
                e.vertex(x[b], y[b], z[b], u1, v[row], argb[b], nx[b], ny[b], nz[b], mode);
                e.vertex(x[c], y[c], z[c], u1, v[row + 1], argb[c], nx[c], ny[c], nz[c], mode);
                e.vertex(x[d], y[d], z[d], u0, v[row + 1], argb[d], nx[d], ny[d], nz[d], mode);
            }
        }
    }

    /**
     * Vertex writer for one frame: applies the pose without allocating, picks shader or fallback uv and colour, and
     * knows the camera's axes for camera-facing quads.
     */
    static final class Emitter {
        private VertexConsumer vc;
        private boolean shaded;
        private float m00, m01, m02, m10, m11, m12, m20, m21, m22, m30, m31, m32;
        private float n00, n01, n02, n10, n11, n12, n20, n21, n22;
        private float lx, ly, lz, ux, uy, uz;

        Emitter begin(VertexConsumer consumer, PoseStack.Pose pose, boolean shader, org.joml.Vector3f left, org.joml.Vector3f up) {
            this.vc = consumer;
            this.shaded = shader;
            Matrix4f m = pose.pose();
            m00 = m.m00();
            m01 = m.m01();
            m02 = m.m02();
            m10 = m.m10();
            m11 = m.m11();
            m12 = m.m12();
            m20 = m.m20();
            m21 = m.m21();
            m22 = m.m22();
            m30 = m.m30();
            m31 = m.m31();
            m32 = m.m32();
            Matrix3f n = pose.normal();
            n00 = n.m00();
            n01 = n.m01();
            n02 = n.m02();
            n10 = n.m10();
            n11 = n.m11();
            n12 = n.m12();
            n20 = n.m20();
            n21 = n.m21();
            n22 = n.m22();
            lx = left.x();
            ly = left.y();
            lz = left.z();
            ux = up.x();
            uy = up.y();
            uz = up.z();
            return this;
        }

        void vertex(float px, float py, float pz, float u, float vv, int colour, float qx, float qy, float qz, int mode) {
            float wx = m00 * px + m10 * py + m20 * pz + m30;
            float wy = m01 * px + m11 * py + m21 * pz + m31;
            float wz = m02 * px + m12 * py + m22 * pz + m32;
            float ox = n00 * qx + n10 * qy + n20 * qz;
            float oy = n01 * qx + n11 * qy + n21 * qz;
            float oz = n02 * qx + n12 * qy + n22 * qz;
            if (!shaded) {
                float len = Mth.sqrt(wx * wx + wy * wy + wz * wz);
                float edge = len < 1.0E-4F ? 0.0F : 1.0F - Math.min(1.0F, Math.abs((wx * ox + wy * oy + wz * oz) / len));
                colour = TideWater.fallback(colour, mode, edge);
                u = 0.5F;
                vv = 0.5F;
            }
            vc.addVertex(wx, wy, wz).setUv(u, vv).setColor(colour).setNormal(ox, oy, oz);
        }

        /** Camera-facing square of half-size {@code s} at camera-relative (px, py, pz); u,v 0..1 over it (+ offsets). */
        void square(float px, float py, float pz, float s, int colour, int mode, float vOffset) {
            float ax = (lx + ux) * s;
            float ay = (ly + uy) * s;
            float az = (lz + uz) * s;
            float bx = (lx - ux) * s;
            float by = (ly - uy) * s;
            float bz = (lz - uz) * s;
            float u = mode * 2.0F;
            // the normal faces the camera (unused by FLAT / PUFF, kept sane for the format)
            float qx = -px;
            float qy = -py;
            float qz = -pz;
            float len = Mth.sqrt(qx * qx + qy * qy + qz * qz);
            if (len > 1.0E-4F) {
                qx /= len;
                qy /= len;
                qz /= len;
            }
            vertex(px + ax, py + ay, pz + az, u, vOffset, colour, qx, qy, qz, mode);
            vertex(px + bx, py + by, pz + bz, u, vOffset + 1.0F, colour, qx, qy, qz, mode);
            vertex(px - ax, py - ay, pz - az, u + 1.0F, vOffset + 1.0F, colour, qx, qy, qz, mode);
            vertex(px - bx, py - by, pz - bz, u + 1.0F, vOffset, colour, qx, qy, qz, mode);
        }
    }
}
