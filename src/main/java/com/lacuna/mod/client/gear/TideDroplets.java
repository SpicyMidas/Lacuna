package com.lacuna.mod.client.gear;

import com.lacuna.mod.client.fx.FxDraw;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Pixel droplets of the tide's water (crisp camera-facing squares drawn in the vortex batch): a fixed pool of
 * ballistic drops in world space (fixed arrays, no allocation), interpolated per frame. They fall under gravity with
 * a little drag and vanish into the water at {@code floor} or when their life runs out.
 */
final class TideDroplets {
    private final int cap;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] px;
    private final float[] py;
    private final float[] pz;
    private final float[] vx;
    private final float[] vy;
    private final float[] vz;
    private final float[] size;
    private final int[] life;
    private final int[] age;
    private final int[] slot;
    private int count;
    private final double ox;
    private final double oy;
    private final double oz;
    private final double floor;

    /** Positions are stored relative to {@code origin} (float precision); drops die below {@code floorY}. */
    TideDroplets(int cap, Vec3 origin, double floorY) {
        this.cap = cap;
        this.x = new float[cap];
        this.y = new float[cap];
        this.z = new float[cap];
        this.px = new float[cap];
        this.py = new float[cap];
        this.pz = new float[cap];
        this.vx = new float[cap];
        this.vy = new float[cap];
        this.vz = new float[cap];
        this.size = new float[cap];
        this.life = new int[cap];
        this.age = new int[cap];
        this.slot = new int[cap];
        this.ox = origin.x;
        this.oy = origin.y;
        this.oz = origin.z;
        this.floor = floorY - origin.y;
    }

    /** Adds a drop at world (wx, wy, wz) moving at (dx, dy, dz) blocks per tick; dropped silently when full. */
    void add(double wx, double wy, double wz, double dx, double dy, double dz, float half, int ticks, int paletteSlot) {
        if (count >= cap) {
            return;
        }
        int k = count++;
        x[k] = px[k] = (float) (wx - ox);
        y[k] = py[k] = (float) (wy - oy);
        z[k] = pz[k] = (float) (wz - oz);
        vx[k] = (float) dx;
        vy[k] = (float) dy;
        vz[k] = (float) dz;
        size[k] = half;
        life[k] = ticks;
        age[k] = 0;
        slot[k] = paletteSlot;
    }

    /** A random spray slot: mostly light water, some pale, a few foam-white. */
    static int spraySlot(RandomSource r) {
        int n = r.nextInt(10);
        return n < 5 ? TideWater.LIGHT : n < 8 ? TideWater.PALE : TideWater.FOAM;
    }

    void tick() {
        for (int k = 0; k < count; ) {
            px[k] = x[k];
            py[k] = y[k];
            pz[k] = z[k];
            vy[k] -= 0.045F;
            vx[k] *= 0.975F;
            vy[k] *= 0.98F;
            vz[k] *= 0.975F;
            x[k] += vx[k];
            y[k] += vy[k];
            z[k] += vz[k];
            if (++age[k] >= life[k] || (vy[k] < 0.0F && y[k] < floor)) {
                remove(k);
            } else {
                k++;
            }
        }
    }

    private void remove(int k) {
        int last = --count;
        x[k] = x[last];
        y[k] = y[last];
        z[k] = z[last];
        px[k] = px[last];
        py[k] = py[last];
        pz[k] = pz[last];
        vx[k] = vx[last];
        vy[k] = vy[last];
        vz[k] = vz[last];
        size[k] = size[last];
        life[k] = life[last];
        age[k] = age[last];
        slot[k] = slot[last];
    }

    int count() {
        return count;
    }

    /** Draws every drop; {@code cam} is the camera position, {@code partial} the partial tick, fading by {@code fade}. */
    void render(TideMesh.Emitter e, Vec3 cam, float partial, TideWater water, float fade) {
        float bx = (float) (ox - cam.x);
        float by = (float) (oy - cam.y);
        float bz = (float) (oz - cam.z);
        for (int k = 0; k < count; k++) {
            float left = life[k] - age[k] - partial;
            float a = 0.92F * fade * Mth.clamp(left / 4.0F, 0.0F, 1.0F);
            if (a <= 0.02F) {
                continue;
            }
            float qx = Mth.lerp(partial, px[k], x[k]) + bx;
            float qy = Mth.lerp(partial, py[k], y[k]) + by;
            float qz = Mth.lerp(partial, pz[k], z[k]) + bz;
            e.square(qx, qy, qz, size[k], FxDraw.argb(a, water.get(slot[k])), TideMesh.FLAT, 0.0F);
        }
    }
}
