package com.lacuna.mod.client.solar;

import com.lacuna.mod.client.fx.FxRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The scorch trail of the Divine Magnifying Glass: where its beam touches a block face it leaves small dark grey
 * marks, snapped to the block's 1/16 pixel grid, lying on that face (any orientation, slabs and stairs included: the
 * marks sit on the surface the beam really hit) and fading out within {@link #LIFE_TICKS} ticks (~0.8 s). As the beam
 * sweeps a face the pixels between its last and current spot are scorched too, so it draws a continuous pixel trail.
 * Client only and computed by every client for every beam it sees ({@link MagnifierBeams#tick}); one fixed pool of
 * {@link #MAX} marks (oldest reused first, a spot the beam rests on is refreshed instead of duplicated), drawn as
 * depth-tested quads just off the surface after particles ({@link SolarRenderTypes#scorch()}).
 */
final class MagnifierScorch {
    private static final int MAX = 512;
    private static final int LIFE_TICKS = 16;
    private static final float PIXEL = 1.0F / 16.0F;
    /** Lift off the surface (with the render type's polygon offset: no z-fighting). */
    private static final float LIFT = 0.0025F;
    /** Most pixels added per beam per tick (a fast sweep is sampled, never unbounded). */
    private static final int MAX_STEPS = 40;
    private static final int GREY = 0x2E2B29;
    private static final Direction[] FACES = Direction.values();

    // the pool: pixel centre on the face (world), face, birth tick, darkness
    private static final double[] X = new double[MAX];
    private static final double[] Y = new double[MAX];
    private static final double[] Z = new double[MAX];
    private static final byte[] FACE = new byte[MAX];
    private static final long[] BORN = new long[MAX];
    private static final float[] DARK = new float[MAX];
    private static int next;
    private static int used;
    @Nullable
    private static ClientLevel level;

    /** The spot each beam scorched last tick (for the trail between ticks). */
    private static final Map<Player, Spot> LAST = new WeakHashMap<>();

    private record Spot(Vec3 at, Direction face, long tick) {}

    private MagnifierScorch() {}

    static void clear() {
        used = 0;
        next = 0;
        LAST.clear();
        level = null;
    }

    /** Client tick: {@code player}'s beam touches {@code hit} (the face its drawn ray meets); null when it touches none. */
    static void burn(ClientLevel lvl, Player player, @Nullable BlockHitResult hit, RandomSource random) {
        if (lvl != level) {
            clear();
            level = lvl;
        }
        long now = lvl.getGameTime();
        if (hit == null) {
            LAST.remove(player);
            return;
        }
        Direction face = hit.getDirection();
        Vec3 at = hit.getLocation();
        Spot last = LAST.get(player);
        LAST.put(player, new Spot(at, face, now));
        // the trail from the last spot when the beam slid along the same face plane
        if (last != null && last.face() == face && now - last.tick() <= 2
                && Math.abs(plane(last.at(), face) - plane(at, face)) < 1.0E-3 && last.at().distanceToSqr(at) < 4.0 * 4.0) {
            double length = last.at().distanceTo(at);
            int steps = Math.min(MAX_STEPS, (int) Math.ceil(length / (PIXEL * 0.75)));
            for (int i = 1; i < steps; i++) {
                Vec3 p = last.at().lerp(at, (double) i / steps);
                add(lvl, p, face, now, 0.75F + 0.25F * random.nextFloat());
            }
        }
        // the burning spot: its pixel and a ragged few around it
        add(lvl, at, face, now, 1.0F);
        int extra = 2 + random.nextInt(3);
        for (int i = 0; i < extra; i++) {
            Vec3 p = offset(at, face, (random.nextInt(5) - 2) * PIXEL, (random.nextInt(5) - 2) * PIXEL);
            add(lvl, p, face, now, 0.55F + 0.35F * random.nextFloat());
        }
    }

    /** The coordinate of {@code p} along the face's axis (the surface plane). */
    private static double plane(Vec3 p, Direction face) {
        return p.get(face.getAxis());
    }

    /** {@code p} moved by {@code a}, {@code b} within the face plane. */
    private static Vec3 offset(Vec3 p, Direction face, double a, double b) {
        return switch (face.getAxis()) {
            case X -> p.add(0.0, b, a);
            case Y -> p.add(a, 0.0, b);
            case Z -> p.add(a, b, 0.0);
        };
    }

    /** Snaps {@code p} to the centre of its 1/16 pixel on the face, checks the face is really there, and stores it. */
    private static void add(ClientLevel lvl, Vec3 p, Direction face, long now, float dark) {
        double x = snap(p.x);
        double y = snap(p.y);
        double z = snap(p.z);
        switch (face.getAxis()) {
            case X -> x = p.x;
            case Y -> y = p.y;
            case Z -> z = p.z;
        }
        // something solid right behind the surface at this pixel (no marks floating over a gap or an edge)
        double ex = x - face.getStepX() * 0.01;
        double ey = y - face.getStepY() * 0.01;
        double ez = z - face.getStepZ() * 0.01;
        BlockPos behind = BlockPos.containing(ex, ey, ez);
        BlockState state = lvl.getBlockState(behind);
        if (state.isAir()) {
            return;
        }
        VoxelShape shape = state.getCollisionShape(lvl, behind);
        if (shape.isEmpty() || !shape.bounds().move(behind).inflate(1.0E-3).contains(ex, ey, ez)) {
            return;
        }
        // a spot the beam rests on is refreshed, not duplicated
        int f = face.ordinal();
        for (int k = 0, i = next - 1; k < Math.min(used, 96); k++, i--) {
            int j = Math.floorMod(i, MAX);
            if (FACE[j] == f && X[j] == x && Y[j] == y && Z[j] == z) {
                BORN[j] = now;
                DARK[j] = Math.max(DARK[j], dark);
                return;
            }
        }
        X[next] = x;
        Y[next] = y;
        Z[next] = z;
        FACE[next] = (byte) f;
        BORN[next] = now;
        DARK[next] = dark;
        next = (next + 1) % MAX;
        used = Math.min(MAX, used + 1);
    }

    private static double snap(double v) {
        return (Math.floor(v * 16.0) + 0.5) / 16.0;
    }

    /** After particles: the live marks, darkest when fresh, fading out over their short life. */
    static void render(FxRenderContext ctx) {
        if (used == 0 || ctx.level() != level) {
            return;
        }
        long now = ctx.level().getGameTime();
        float pt = ctx.partialTick();
        PoseStack.Pose pose = ctx.pose().last();
        VertexConsumer vc = null;
        Vec3 cam = ctx.cam();
        float h = PIXEL * 0.5F;
        for (int i = 0; i < used; i++) {
            float age = (now - BORN[i]) + pt;
            if (age >= LIFE_TICKS || age < -1.0F) {
                continue;
            }
            float life = age / LIFE_TICKS;
            float alpha = 0.82F * DARK[i] * (1.0F - smooth((life - 0.3F) / 0.7F));
            if (alpha <= 0.01F) {
                continue;
            }
            if (vc == null) {
                vc = ctx.buffers().getBuffer(SolarRenderTypes.scorch());
            }
            Direction face = FACES[FACE[i]];
            float cx = (float) (X[i] - cam.x + face.getStepX() * LIFT);
            float cy = (float) (Y[i] - cam.y + face.getStepY() * LIFT);
            float cz = (float) (Z[i] - cam.z + face.getStepZ() * LIFT);
            int argb = ((int) (alpha * 255.0F) << 24) | GREY;
            switch (face.getAxis()) {
                case X -> {
                    vertex(vc, pose, cx, cy - h, cz - h, argb);
                    vertex(vc, pose, cx, cy + h, cz - h, argb);
                    vertex(vc, pose, cx, cy + h, cz + h, argb);
                    vertex(vc, pose, cx, cy - h, cz + h, argb);
                }
                case Y -> {
                    vertex(vc, pose, cx - h, cy, cz - h, argb);
                    vertex(vc, pose, cx - h, cy, cz + h, argb);
                    vertex(vc, pose, cx + h, cy, cz + h, argb);
                    vertex(vc, pose, cx + h, cy, cz - h, argb);
                }
                case Z -> {
                    vertex(vc, pose, cx - h, cy - h, cz, argb);
                    vertex(vc, pose, cx + h, cy - h, cz, argb);
                    vertex(vc, pose, cx + h, cy + h, cz, argb);
                    vertex(vc, pose, cx - h, cy + h, cz, argb);
                }
            }
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int argb) {
        vc.addVertex(pose, x, y, z).setColor(argb);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
