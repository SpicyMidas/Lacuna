package com.lacuna.mod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Terrain-projected ground marks: a glyph, ring, reticle, pool or scorch that lies on the real surface of the blocks
 * under it instead of one flat quad at a single height (which sinks into steps, slabs, snow and slopes or floats over
 * them).
 * <p>
 * One instance per effect holds a cached <b>height grid</b> around a reference point: for every half-block cell (one
 * block for marks wider than {@value #FINE_REACH} blocks) the top of the visible block shape there - snow layers,
 * slabs, carpets, paths, farmland, each half of a stair, the surface of water or lava - picked as the surface nearest
 * the reference height within a few blocks (open above it, so ceilings and buried blocks never count; plants, rails,
 * torches and thin posts are looked through). The grid is merged into rectangles of equal height and the steps between
 * them into short vertical "skirts", so flat ground costs one quad and a mark wraps over a step without a gap. It is
 * rebuilt only when the effect moves away, grows past it or every {@value #REFRESH} ticks (picking up broken and placed
 * blocks); drawing allocates nothing.
 * <p>
 * Every mark is the parallelogram {@code c + s * U + t * W} ({@code s, t} in -1..1) clipped to each rectangle, with
 * uv mapped from {@code s, t} (any rotation, any uv window such as the mode bands of {@code solar_ring}), lifted
 * {@link #EPS} plus the caller's {@code lift} above the surface. Cells much higher or lower than the ground under the
 * centre (cliff edges, a wall top, a pit) fade out. Plain vertices into the caller's consumer: works with every
 * {@link FxRenderTypes} type and their Fabulous depth twins. Render thread only.
 */
public final class GroundDecal {
    /** Lift above the surface against z-fighting, in blocks. */
    public static final float EPS = 0.015F;
    /** Search window around the reference height: surfaces up to this far above / below are found. */
    private static final double UP = 4.0;
    private static final double DOWN = 4.5;
    /** Free space needed above a surface (blocks): a buried top or one right under a ceiling is no surface. */
    private static final double CLEARANCE = 0.2;
    /** Surfaces above the reference height are found a little less eagerly than those below. */
    private static final double ABOVE_COST = 1.25;
    /** Height difference from the ground under the centre at which cells start / finish fading out. */
    private static final float FADE_START = 1.5F;
    private static final float FADE_END = 3.5F;
    /** Steps up to this tall get a skirt (taller ones are walls: the mark stops at their foot). */
    private static final float SKIRT_MAX = 1.05F;
    /** Skirts stand this far off the block face. */
    private static final double SKIRT_OUT = 0.012;
    /** The reference height may drift this much before the grid is rebuilt. */
    private static final double MOVE_Y = 0.3;
    /** Marks up to this reach use half-block cells, wider ones whole blocks. */
    private static final double FINE_REACH = 12.0;
    private static final double MAX_REACH = 64.0;
    /** Extra blocks built around the reach, so a mark that follows a walking entity does not rebuild every tick. */
    private static final double MARGIN = 1.0;
    /** Ticks between refreshes of a small grid, and of a large one ({@link #LARGE_CELLS} cells or more). */
    private static final int REFRESH = 10;
    private static final int REFRESH_LARGE = 30;
    private static final int LARGE_CELLS = 4096;

    // ------------------------------------------------------------------ block shapes (per state, cached)

    /** Shape tops/bottoms per state: [0..3] tops of the four half cells (x + 2z), [4..7] their bottoms, [8], [9] whole block. */
    private static final Map<BlockState, float[]> SHAPES = new IdentityHashMap<>();
    private static final float[] NONE = new float[10];

    static {
        Arrays.fill(NONE, Float.NaN);
    }

    // ------------------------------------------------------------------ scratch (render thread)

    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final double[] CEIL = new double[4];
    private static final double[] BEST = new double[4];
    private static final double[] COST = new double[4];
    private static final double[] AX = new double[12];
    private static final double[] AZ = new double[12];
    private static final double[] AS = new double[12];
    private static final double[] AT = new double[12];
    private static final double[] BX = new double[12];
    private static final double[] BZ = new double[12];
    private static final double[] BS = new double[12];
    private static final double[] BT = new double[12];

    // ------------------------------------------------------------------ shared instances (keyed by entity etc.)

    private static final Map<Object, GroundDecal> SHARED = new HashMap<>();

    // ------------------------------------------------------------------ state

    private boolean fluids = true;
    private int levelTag;
    private double refX;
    private double refY;
    private double refZ;
    /** Largest reach asked for since the last build (the next refresh builds for it). */
    private double want;
    private long lastUse;

    private boolean built;
    private long builtAt;
    private double builtY;
    private double builtReach;
    private double cell = 0.5;
    /** World position of the grid's corner, cells per side. */
    private double gx;
    private double gz;
    private int nx;
    private int nz;
    /** Height of the ground under the reference point (cliff fading is measured from it). */
    private double base;
    private float[] h = new float[0];
    private boolean[] used = new boolean[0];

    private int rects;
    private float[] rx0 = new float[0];
    private float[] rz0 = new float[0];
    private float[] rx1 = new float[0];
    private float[] rz1 = new float[0];
    private float[] rh = new float[0];
    private float[] rf = new float[0];

    private int skirts;
    private float[] sx0 = new float[0];
    private float[] sz0 = new float[0];
    private float[] sx1 = new float[0];
    private float[] sz1 = new float[0];
    private float[] sLo = new float[0];
    private float[] sHi = new float[0];
    private float[] sf = new float[0];

    public GroundDecal() {}

    /**
     * The decal kept for {@code key} (an entity, a player...) by effects that have no object of their own to hold one;
     * entries unused for a few seconds are dropped.
     */
    public static GroundDecal shared(Object key) {
        long now = Util.getMillis();
        GroundDecal d = SHARED.get(key);
        if (d == null) {
            if (SHARED.size() >= 16) {
                SHARED.values().removeIf(old -> now - old.lastUse > 4000L);
            }
            d = new GroundDecal();
            SHARED.put(key, d);
        }
        d.lastUse = now;
        return d;
    }

    /** Whether the surface of water and lava counts (default true: a mark at the water line lies on the water). */
    public GroundDecal fluids(boolean on) {
        if (on != fluids) {
            fluids = on;
            built = false;
        }
        return this;
    }

    /**
     * Aims the decal at world {@code (x, y, z)}, {@code y} being the height of the ground the effect stands on, for
     * marks reaching up to {@code reach} blocks from it. Cheap when nothing changed; returns this.
     */
    public GroundDecal at(@Nullable ClientLevel level, double x, double y, double z, double reach) {
        if (level == null) {
            return this;
        }
        reach = Mth.clamp(reach, 0.5, MAX_REACH);
        want = Math.max(want, reach);
        int tag = System.identityHashCode(level);
        long now = level.getGameTime();
        boolean moved = Math.abs(y - builtY) > MOVE_Y || !covers(x, z, reach, reach);
        int refresh = nx * nz >= LARGE_CELLS ? REFRESH_LARGE : REFRESH;
        refX = x;
        refY = y;
        refZ = z;
        if (!built || tag != levelTag || moved || now - builtAt >= refresh || now < builtAt) {
            levelTag = tag;
            build(level, want);
        }
        return this;
    }

    /** {@link #at(ClientLevel, double, double, double, double)} at a world position. */
    public GroundDecal at(@Nullable ClientLevel level, Vec3 ground, double reach) {
        return at(level, ground.x, ground.y, ground.z, reach);
    }

    /** Height of the ground under the reference point (its own height when no surface was found there). */
    public double ground() {
        return built ? base : refY;
    }

    /** Surface height at world {@code (x, z)} (no lift), or {@code fallback} where there is none or outside the grid. */
    public double heightAt(double x, double z, double fallback) {
        if (!built) {
            return fallback;
        }
        int i = Mth.floor((x - gx) / cell);
        int j = Mth.floor((z - gz) / cell);
        if (i < 0 || j < 0 || i >= nx || j >= nz) {
            return fallback;
        }
        float v = h[j * nx + i];
        return Float.isNaN(v) ? fallback : v;
    }

    // ------------------------------------------------------------------ drawing

    /** Axis-aligned square mark of half-size {@code half} centred at world {@code (cx, cz)}; uv 0..1 like {@link FxDraw#ground}. */
    public void square(FxRenderContext ctx, VertexConsumer vc, double cx, double cz, float half, float lift, int argb) {
        draw(ctx.pose().last(), vc, ctx.cam(), cx, cz, half, 0.0, 0.0, half, 0.0F, 0.0F, 1.0F, 1.0F, lift, argb);
    }

    /** Axis-aligned square mark with its uv window {@code u0..u1 x v0..v1}. */
    public void square(FxRenderContext ctx, VertexConsumer vc, double cx, double cz, float half, float u0, float v0, float u1,
                       float v1, float lift, int argb) {
        draw(ctx.pose().last(), vc, ctx.cam(), cx, cz, half, 0.0, 0.0, half, u0, v0, u1, v1, lift, argb);
    }

    /**
     * Mark spanning {@code c ± U ± W} (horizontal half-axes {@code U = (ux, uz)} along texture u, {@code W = (wx, wz)}
     * along v, like {@link FxDraw#quad}); uv 0..1.
     */
    public void quad(FxRenderContext ctx, VertexConsumer vc, double cx, double cz, double ux, double uz, double wx, double wz,
                     float lift, int argb) {
        draw(ctx.pose().last(), vc, ctx.cam(), cx, cz, ux, uz, wx, wz, 0.0F, 0.0F, 1.0F, 1.0F, lift, argb);
    }

    /**
     * The general mark: the parallelogram {@code c + s * U + t * W} at world {@code (cx, cz)}, mapped to
     * {@code u = u0..u1} along s and {@code v = v0..v1} along t, clipped to the terrain cells under it and lifted
     * {@link #EPS} + {@code lift} above them. {@code cam} is the camera position (vertices are camera-relative).
     */
    public void draw(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, double cx, double cz, double ux, double uz, double wx,
                     double wz, float u0, float v0, float u1, float v1, float lift, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        double det = ux * wz - uz * wx;
        if (Math.abs(det) < 1.0E-9) {
            return;
        }
        double ex = Math.abs(ux) + Math.abs(wx);
        double ez = Math.abs(uz) + Math.abs(wz);
        if (!ensure(cx, cz, ex, ez)) {
            return;
        }
        // s = (dx * wz - dz * wx) / det, t = (dz * ux - dx * uz) / det, with d relative to the centre
        double isx = wz / det;
        double isz = -wx / det;
        double itx = -uz / det;
        double itz = ux / det;
        double ccx = cx - gx;
        double ccz = cz - gz;
        double fx0 = ccx - ex;
        double fx1 = ccx + ex;
        double fz0 = ccz - ez;
        double fz1 = ccz + ez;
        float um = (u0 + u1) * 0.5F;
        float du = (u1 - u0) * 0.5F;
        float vm = (v0 + v1) * 0.5F;
        float dv = (v1 - v0) * 0.5F;
        double ox = gx - cam.x;
        double oz = gz - cam.z;
        double oy = EPS + lift - cam.y;

        for (int r = 0; r < rects; r++) {
            double x0 = rx0[r];
            double x1 = rx1[r];
            double z0 = rz0[r];
            double z1 = rz1[r];
            if (x1 <= fx0 || x0 >= fx1 || z1 <= fz0 || z0 >= fz1) {
                continue;
            }
            int c = rf[r] >= 0.999F ? argb : FxDraw.withAlpha(argb, rf[r]);
            if ((c >>> 24) == 0) {
                continue;
            }
            put(AX, AZ, AS, AT, 0, x0, z0, ccx, ccz, isx, isz, itx, itz);
            put(AX, AZ, AS, AT, 1, x0, z1, ccx, ccz, isx, isz, itx, itz);
            put(AX, AZ, AS, AT, 2, x1, z1, ccx, ccz, isx, isz, itx, itz);
            put(AX, AZ, AS, AT, 3, x1, z0, ccx, ccz, isx, isz, itx, itz);
            int m = 4;
            if (!inside(AS, AT, 4)) {
                m = clip(m, AX, AZ, AS, AT, BX, BZ, BS, BT, true, 1.0);
                m = clip(m, BX, BZ, BS, BT, AX, AZ, AS, AT, true, -1.0);
                m = clip(m, AX, AZ, AS, AT, BX, BZ, BS, BT, false, 1.0);
                m = clip(m, BX, BZ, BS, BT, AX, AZ, AS, AT, false, -1.0);
                if (m < 3) {
                    continue;
                }
            }
            float y = (float) (rh[r] + oy);
            for (int k = 1; k + 1 < m; k += 2) {
                int k3 = Math.min(k + 2, m - 1);
                vertex(vc, pose, 0, ox, y, oz, um, du, vm, dv, c);
                vertex(vc, pose, k, ox, y, oz, um, du, vm, dv, c);
                vertex(vc, pose, k + 1, ox, y, oz, um, du, vm, dv, c);
                vertex(vc, pose, k3, ox, y, oz, um, du, vm, dv, c);
            }
        }

        // skirts: the mark streaks down the faces of the steps it crosses (a projected decal), closing the gap
        for (int k = 0; k < skirts; k++) {
            double ax = sx0[k];
            double az = sz0[k];
            double bx = sx1[k];
            double bz = sz1[k];
            if (Math.max(ax, bx) < fx0 || Math.min(ax, bx) > fx1 || Math.max(az, bz) < fz0 || Math.min(az, bz) > fz1) {
                continue;
            }
            int c = sf[k] >= 0.999F ? argb : FxDraw.withAlpha(argb, sf[k]);
            if ((c >>> 24) == 0) {
                continue;
            }
            double sa = (ax - ccx) * isx + (az - ccz) * isz;
            double ta = (ax - ccx) * itx + (az - ccz) * itz;
            double sb = (bx - ccx) * isx + (bz - ccz) * isz;
            double tb = (bx - ccx) * itx + (bz - ccz) * itz;
            double l0 = 0.0;
            double l1 = 1.0;
            for (int p = 0; p < 4 && l0 < l1; p++) {
                double sign = (p & 1) == 0 ? 1.0 : -1.0;
                double va = p < 2 ? sa : ta;
                double vb = p < 2 ? sb : tb;
                double num = 1.0 - sign * va;
                double den = sign * (vb - va);
                if (Math.abs(den) < 1.0E-12) {
                    if (num < 0.0) {
                        l1 = -1.0;
                    }
                } else if (den > 0.0) {
                    l1 = Math.min(l1, num / den);
                } else {
                    l0 = Math.max(l0, num / den);
                }
            }
            if (l0 >= l1) {
                continue;
            }
            float xa = (float) (ax + (bx - ax) * l0 + ox);
            float za = (float) (az + (bz - az) * l0 + oz);
            float xb = (float) (ax + (bx - ax) * l1 + ox);
            float zb = (float) (az + (bz - az) * l1 + oz);
            float ua = um + du * (float) Mth.clamp(sa + (sb - sa) * l0, -1.0, 1.0);
            float va = vm + dv * (float) Mth.clamp(ta + (tb - ta) * l0, -1.0, 1.0);
            float ub = um + du * (float) Mth.clamp(sa + (sb - sa) * l1, -1.0, 1.0);
            float vb = vm + dv * (float) Mth.clamp(ta + (tb - ta) * l1, -1.0, 1.0);
            float lo = (float) (sLo[k] + oy);
            float hi = (float) (sHi[k] + oy);
            vc.addVertex(pose, xa, lo, za).setUv(ua, va).setColor(c);
            vc.addVertex(pose, xa, hi, za).setUv(ua, va).setColor(c);
            vc.addVertex(pose, xb, hi, zb).setUv(ub, vb).setColor(c);
            vc.addVertex(pose, xb, lo, zb).setUv(ub, vb).setColor(c);
        }
    }

    private static void put(double[] xs, double[] zs, double[] ss, double[] ts, int i, double x, double z, double ccx, double ccz,
                            double isx, double isz, double itx, double itz) {
        xs[i] = x;
        zs[i] = z;
        ss[i] = (x - ccx) * isx + (z - ccz) * isz;
        ts[i] = (x - ccx) * itx + (z - ccz) * itz;
    }

    private static boolean inside(double[] ss, double[] ts, int n) {
        for (int i = 0; i < n; i++) {
            if (ss[i] < -1.0 || ss[i] > 1.0 || ts[i] < -1.0 || ts[i] > 1.0) {
                return false;
            }
        }
        return true;
    }

    /** Sutherland-Hodgman against {@code sign * (s or t) <= 1}; returns the output vertex count. */
    private static int clip(int n, double[] ix, double[] iz, double[] is, double[] it, double[] ox, double[] oz, double[] os,
                            double[] ot, boolean alongS, double sign) {
        int m = 0;
        for (int k = 0; k < n; k++) {
            int k2 = k + 1 == n ? 0 : k + 1;
            double a = sign * (alongS ? is[k] : it[k]) - 1.0;
            double b = sign * (alongS ? is[k2] : it[k2]) - 1.0;
            if (a <= 0.0) {
                ox[m] = ix[k];
                oz[m] = iz[k];
                os[m] = is[k];
                ot[m] = it[k];
                m++;
            }
            if ((a <= 0.0) != (b <= 0.0)) {
                double f = a / (a - b);
                ox[m] = ix[k] + (ix[k2] - ix[k]) * f;
                oz[m] = iz[k] + (iz[k2] - iz[k]) * f;
                os[m] = is[k] + (is[k2] - is[k]) * f;
                ot[m] = it[k] + (it[k2] - it[k]) * f;
                m++;
            }
        }
        return m;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int i, double ox, float y, double oz, float um, float du, float vm,
                               float dv, int argb) {
        float s = (float) Mth.clamp(AS[i], -1.0, 1.0);
        float t = (float) Mth.clamp(AT[i], -1.0, 1.0);
        vc.addVertex(pose, (float) (AX[i] + ox), y, (float) (AZ[i] + oz)).setUv(um + du * s, vm + dv * t).setColor(argb);
    }

    // ------------------------------------------------------------------ the height grid

    private boolean covers(double cx, double cz, double ex, double ez) {
        return built && cx - ex >= gx && cx + ex <= gx + nx * cell && cz - ez >= gz && cz + ez <= gz + nz * cell;
    }

    /** Makes sure the grid covers a mark of extents {@code ex, ez} at {@code (cx, cz)}, growing it if it can. */
    private boolean ensure(double cx, double cz, double ex, double ez) {
        double need = Math.max(Math.abs(cx - refX) + ex, Math.abs(cz - refZ) + ez);
        want = Math.max(want, Math.min(MAX_REACH, need));
        if (covers(cx, cz, ex, ez) || (built && builtReach >= MAX_REACH)) {
            return built;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || System.identityHashCode(level) != levelTag) {
            return built;
        }
        build(level, Math.max(want, Math.min(MAX_REACH, need * 1.25)));
        return built;
    }

    private void build(ClientLevel level, double reach) {
        reach = Mth.clamp(reach, 0.5, MAX_REACH);
        cell = reach > FINE_REACH ? 1.0 : 0.5;
        boolean fine = cell < 1.0;
        double ext = reach + MARGIN;
        int i0 = Mth.floor((refX - ext) / cell);
        int i1 = Mth.ceil((refX + ext) / cell);
        int j0 = Mth.floor((refZ - ext) / cell);
        int j1 = Mth.ceil((refZ + ext) / cell);
        nx = i1 - i0;
        nz = j1 - j0;
        gx = i0 * cell;
        gz = j0 * cell;
        int cells = nx * nz;
        if (h.length < cells) {
            h = new float[cells];
            used = new boolean[cells];
        }
        Arrays.fill(h, 0, cells, Float.NaN);
        int yTop = Mth.floor(refY + UP);
        int yBot = Mth.floor(refY - DOWN);
        int per = fine ? 2 : 1;
        int bx0 = Math.floorDiv(i0, per);
        int bx1 = Math.floorDiv(i1 - 1, per);
        int bz0 = Math.floorDiv(j0, per);
        int bz1 = Math.floorDiv(j1 - 1, per);
        for (int bz = bz0; bz <= bz1; bz++) {
            for (int bx = bx0; bx <= bx1; bx++) {
                scan(level, bx, bz, yTop, yBot, fine);
                for (int q = 0; q < (fine ? 4 : 1); q++) {
                    int ci = bx * per + (q & 1) - i0;
                    int cj = bz * per + (q >> 1) - j0;
                    if (ci >= 0 && cj >= 0 && ci < nx && cj < nz) {
                        h[cj * nx + ci] = (float) BEST[q];
                    }
                }
            }
        }
        // the ground under the reference point (or the nearest found around it)
        base = refY;
        int ci = Mth.clamp(Mth.floor((refX - gx) / cell), 0, nx - 1);
        int cj = Mth.clamp(Mth.floor((refZ - gz) / cell), 0, nz - 1);
        double bestD = Double.MAX_VALUE;
        for (int dj = -2; dj <= 2; dj++) {
            for (int di = -2; di <= 2; di++) {
                int a = ci + di;
                int b = cj + dj;
                if (a < 0 || b < 0 || a >= nx || b >= nz || Float.isNaN(h[b * nx + a])) {
                    continue;
                }
                double d = di * di + dj * dj;
                if (d < bestD) {
                    bestD = d;
                    base = h[b * nx + a];
                }
            }
        }
        mesh();
        skirts();
        built = true;
        builtAt = level.getGameTime();
        builtY = refY;
        builtReach = reach;
        want = 0.0;
    }

    /** Finds the surface of each half cell (or the whole block) of column {@code (bx, bz)} into {@link #BEST} (NaN: none). */
    private void scan(ClientLevel level, int bx, int bz, int yTop, int yBot, boolean fine) {
        int cells = fine ? 4 : 1;
        for (int q = 0; q < cells; q++) {
            CEIL[q] = Double.POSITIVE_INFINITY;
            BEST[q] = Double.NaN;
            COST[q] = Double.POSITIVE_INFINITY;
        }
        Fluid above = Fluids.EMPTY;
        for (int y = yTop + 1; y >= yBot; y--) {
            BlockState state = level.getBlockState(POS.set(bx, y, bz));
            float[] shape = shapeOf(state);
            double fluidTop = Double.NaN;
            if (fluids) {
                FluidState fluid = state.getFluidState();
                // only the top of a body of fluid is a surface
                if (!fluid.isEmpty() && !fluid.getType().isSame(above)) {
                    fluidTop = y + fluid.getOwnHeight();
                }
                above = fluid.getType();
            }
            boolean window = y <= yTop;
            boolean done = y + 0.5 < refY;
            for (int q = 0; q < cells; q++) {
                if (window && !Double.isNaN(fluidTop)) {
                    consider(q, fluidTop);
                }
                float top = shape[fine ? q : 8];
                if (!Float.isNaN(top)) {
                    if (window) {
                        consider(q, y + top);
                    }
                    CEIL[q] = y + shape[fine ? 4 + q : 9];
                }
                // nothing further down can be nearer than what this cell already has
                if (COST[q] > refY - (y + 0.5)) {
                    done = false;
                }
            }
            if (done) {
                break;
            }
        }
    }

    private void consider(int q, double top) {
        if (CEIL[q] - top < CLEARANCE || top > refY + UP || top < refY - DOWN) {
            return;
        }
        double cost = top > refY ? (top - refY) * ABOVE_COST : refY - top;
        if (cost < COST[q]) {
            COST[q] = cost;
            BEST[q] = top;
        }
    }

    private static float[] shapeOf(BlockState state) {
        float[] s = SHAPES.get(state);
        if (s == null) {
            s = computeShape(state);
            SHAPES.put(state, s);
        }
        return s;
    }

    /**
     * Top and bottom of the state's visible shape over each half cell and the whole block (NaN where it covers less
     * than 40%: posts, panes, ladders, flower pots are looked through), or {@link #NONE} for air, plants, rails,
     * torches and other blocks without collision (snow layers always count).
     */
    private static float[] computeShape(BlockState state) {
        if (state.isAir()) {
            return NONE;
        }
        VoxelShape shape;
        try {
            VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (!(state.getBlock() instanceof SnowLayerBlock) && !state.isSolid() && collision.isEmpty()) {
                return NONE;
            }
            shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) {
                shape = collision;
            }
        } catch (RuntimeException e) {
            shape = Shapes.block();
        }
        if (shape.isEmpty()) {
            return NONE;
        }
        double[] cover = new double[5];
        double[] top = new double[5];
        double[] topAny = new double[5];
        double[] bottom = new double[5];
        Arrays.fill(top, Double.NEGATIVE_INFINITY);
        Arrays.fill(topAny, Double.NEGATIVE_INFINITY);
        Arrays.fill(bottom, Double.POSITIVE_INFINITY);
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            for (int q = 0; q < 5; q++) {
                double qx0 = q == 4 ? 0.0 : (q & 1) * 0.5;
                double qz0 = q == 4 ? 0.0 : (q >> 1) * 0.5;
                double size = q == 4 ? 1.0 : 0.5;
                double ox = Math.min(x1, qx0 + size) - Math.max(x0, qx0);
                double oz = Math.min(z1, qz0 + size) - Math.max(z0, qz0);
                if (ox <= 1.0E-6 || oz <= 1.0E-6) {
                    continue;
                }
                double area = ox * oz / (size * size);
                cover[q] += area;
                bottom[q] = Math.min(bottom[q], y0);
                topAny[q] = Math.max(topAny[q], y1);
                if (area >= 0.15) {
                    top[q] = Math.max(top[q], y1);
                }
            }
        });
        float[] out = new float[10];
        Arrays.fill(out, Float.NaN);
        for (int q = 0; q < 5; q++) {
            if (cover[q] >= 0.4) {
                int ti = q == 4 ? 8 : q;
                int bi = q == 4 ? 9 : 4 + q;
                out[ti] = (float) (top[q] > Double.NEGATIVE_INFINITY ? top[q] : topAny[q]);
                out[bi] = (float) bottom[q];
            }
        }
        return out;
    }

    private float fade(float height) {
        float d = (float) Math.abs(height - base);
        if (d <= FADE_START) {
            return 1.0F;
        }
        if (d >= FADE_END) {
            return 0.0F;
        }
        float t = (d - FADE_START) / (FADE_END - FADE_START);
        return 1.0F - t * t * (3.0F - 2.0F * t);
    }

    /** Merges the grid into rectangles of equal height (greedy). */
    private void mesh() {
        rects = 0;
        int cells = nx * nz;
        Arrays.fill(used, 0, cells, false);
        float c = (float) cell;
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                float v = h[k];
                if (used[k] || Float.isNaN(v)) {
                    continue;
                }
                int i2 = i + 1;
                while (i2 < nx && !used[j * nx + i2] && h[j * nx + i2] == v) {
                    i2++;
                }
                int j2 = j + 1;
                grow:
                while (j2 < nz) {
                    for (int a = i; a < i2; a++) {
                        int kk = j2 * nx + a;
                        if (used[kk] || h[kk] != v) {
                            break grow;
                        }
                    }
                    j2++;
                }
                for (int b = j; b < j2; b++) {
                    Arrays.fill(used, b * nx + i, b * nx + i2, true);
                }
                float f = fade(v);
                if (f <= 0.004F) {
                    continue;
                }
                if (rects == rh.length) {
                    int len = Math.max(16, rects * 2);
                    rx0 = Arrays.copyOf(rx0, len);
                    rz0 = Arrays.copyOf(rz0, len);
                    rx1 = Arrays.copyOf(rx1, len);
                    rz1 = Arrays.copyOf(rz1, len);
                    rh = Arrays.copyOf(rh, len);
                    rf = Arrays.copyOf(rf, len);
                }
                rx0[rects] = i * c;
                rz0[rects] = j * c;
                rx1[rects] = i2 * c;
                rz1[rects] = j2 * c;
                rh[rects] = v;
                rf[rects] = f;
                rects++;
            }
        }
    }

    /** Vertical strips on the steps between neighbouring cells (merged along each cell boundary). */
    private void skirts() {
        skirts = 0;
        float c = (float) cell;
        for (int pass = 0; pass < 2; pass++) {
            boolean alongZ = pass == 0; // boundaries between x neighbours run along z
            int lines = alongZ ? nx - 1 : nz - 1;
            int length = alongZ ? nz : nx;
            for (int l = 0; l < lines; l++) {
                int start = -1;
                float rLo = 0.0F;
                float rHi = 0.0F;
                int rSide = 0;
                for (int s = 0; s <= length; s++) {
                    boolean has = false;
                    float lo = 0.0F;
                    float hi = 0.0F;
                    int side = 0;
                    if (s < length) {
                        float a = alongZ ? h[s * nx + l] : h[l * nx + s];
                        float b = alongZ ? h[s * nx + l + 1] : h[(l + 1) * nx + s];
                        float d = b - a;
                        if (!Float.isNaN(d) && Math.abs(d) > 1.0E-4F && Math.abs(d) <= SKIRT_MAX) {
                            has = true;
                            lo = Math.min(a, b);
                            hi = Math.max(a, b);
                            side = d > 0.0F ? 1 : -1;
                        }
                    }
                    if (start >= 0 && (!has || lo != rLo || hi != rHi || side != rSide)) {
                        // the higher cell's face looks towards the lower one: stand just off it on that side
                        float at = (float) ((l + 1) * c - rSide * SKIRT_OUT);
                        float f = Math.min(fade(rLo), fade(rHi));
                        if (f > 0.004F) {
                            if (alongZ) {
                                addSkirt(at, start * c, at, s * c, rLo, rHi, f);
                            } else {
                                addSkirt(start * c, at, s * c, at, rLo, rHi, f);
                            }
                        }
                        start = -1;
                    }
                    if (has && start < 0) {
                        start = s;
                        rLo = lo;
                        rHi = hi;
                        rSide = side;
                    }
                }
            }
        }
    }

    private void addSkirt(float x0, float z0, float x1, float z1, float lo, float hi, float f) {
        if (skirts == sf.length) {
            int len = Math.max(16, skirts * 2);
            sx0 = Arrays.copyOf(sx0, len);
            sz0 = Arrays.copyOf(sz0, len);
            sx1 = Arrays.copyOf(sx1, len);
            sz1 = Arrays.copyOf(sz1, len);
            sLo = Arrays.copyOf(sLo, len);
            sHi = Arrays.copyOf(sHi, len);
            sf = Arrays.copyOf(sf, len);
        }
        sx0[skirts] = x0;
        sz0[skirts] = z0;
        sx1[skirts] = x1;
        sz1[skirts] = z1;
        sLo[skirts] = lo;
        sHi[skirts] = hi;
        sf[skirts] = f;
        skirts++;
    }
}
