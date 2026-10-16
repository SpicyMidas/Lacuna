package com.lacuna.mod.client.mirror;

import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.render.SectionSweep;
import com.lacuna.mod.client.render.StencilSupport;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Mirror Glass reflections, client state behind {@link MirrorRenderer}.
 * <p>
 * Every {@value #SWEEP_EVERY} ticks a budgeted {@link SectionSweep} finds the mirror blocks ({@code lacuna:mirror_glass}
 * and the cracked {@code lacuna:labyrinth_exit}) around the camera; sections without them cost a palette check. Their
 * exposed faces (the neighbour is neither a mirror block nor a solid cube) are grouped by axis-aligned plane: one
 * direction and one block-boundary coordinate per plane. The living entities near the camera are refreshed every few
 * ticks while some plane exists. Nothing is kept, and nothing is drawn, when no mirror is near. Any failure disables
 * the {@value #FEATURE} feature only.
 */
public final class MirrorReflections {
    public static final String FEATURE = "mirror_reflections";
    /** Mirror blocks farther than this from the camera are ignored. */
    static final int SCAN_RANGE = 16;
    /** Creatures farther than this from the camera are not reflected. */
    static final double ENTITY_RANGE = 12.0;
    private static final int MAX_BLOCKS = 1024;
    private static final int MAX_PLANES = 48;
    private static final int MAX_FACES_PER_PLANE = 512;
    private static final int MAX_ENTITIES = 16;
    private static final int BLOCK_BUDGET = 16_384;
    private static final int SWEEP_EVERY = 10;
    private static final int ENTITY_REFRESH_EVERY = 3;
    /** The offscreen reflection target is freed after this long without any mirror around. */
    private static final int RELEASE_AFTER = 600;

    private static List<Plane> planes = List.of();
    private static List<LivingEntity> entities = List.of();
    private static SectionSweep sweep;
    private static int ticks;
    private static int sweepStart = -SWEEP_EVERY;
    private static int lastMirrorTick;
    private static Block mirror;
    private static Block crackedMirror;

    private MirrorReflections() {}

    public static void register(IEventBus forgeBus) {
        forgeBus.addListener(MirrorReflections::onClientTick);
        forgeBus.addListener(MirrorReflections::onLogout);
        forgeBus.addListener(MirrorRenderer::onRenderLevel);
        forgeBus.addListener(MirrorRenderer::onNameTag);
    }

    /** Config toggle, feature health and stencil availability. */
    public static boolean isActive() {
        return LacunaClientConfig.get(LacunaClientConfig.MIRROR_REFLECTIONS)
                && !SafeRun.isDisabled(FEATURE)
                && !StencilSupport.failed();
    }

    static List<Plane> planes() {
        return planes;
    }

    static List<LivingEntity> entities() {
        return entities;
    }

    static boolean isMirror(BlockState state) {
        if (mirror == null) {
            mirror = ModBlocks.MIRROR_GLASS.get();
            crackedMirror = ModBlocks.LABYRINTH_EXIT.get();
        }
        return state.is(mirror) || state.is(crackedMirror);
    }

    static void reset() {
        planes = List.of();
        entities = List.of();
        sweep = null;
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        MirrorRenderer.release();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ticks++;
        if (isActive()) {
            SafeRun.run(FEATURE, MirrorReflections::tick, MirrorReflections::reset);
        } else if (!planes.isEmpty() || !entities.isEmpty() || sweep != null) {
            reset();
        }
        if (planes.isEmpty() && ticks - lastMirrorTick > RELEASE_AFTER) {
            MirrorRenderer.release();
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (level == null || mc.player == null || !camera.isInitialized()) {
            reset();
            return;
        }
        Vec3 eye = camera.getPosition();
        if (sweep == null && ticks - sweepStart >= SWEEP_EVERY) {
            sweep = new SectionSweep(level, eye, SCAN_RANGE, MirrorReflections::isMirror);
            sweepStart = ticks;
        }
        if (sweep != null) {
            sweep.run(level, BLOCK_BUDGET);
            if (sweep.done()) {
                List<BlockPos> blocks = sweep.result(MAX_BLOCKS);
                planes = blocks.isEmpty() ? List.of() : buildPlanes(level, blocks, sweep.center());
                sweep = null;
            }
        }
        if (planes.isEmpty()) {
            entities = List.of();
            return;
        }
        lastMirrorTick = ticks;
        if (!StencilSupport.ensure(mc)) {
            planes = List.of();
            return;
        }
        if (ticks % ENTITY_REFRESH_EVERY == 0) {
            List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(eye, eye).inflate(ENTITY_RANGE),
                    e -> !e.isRemoved() && !e.isSpectator() && !e.isInvisible()
                            && e.distanceToSqr(eye) <= ENTITY_RANGE * ENTITY_RANGE));
            found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
            entities = List.copyOf(found.size() > MAX_ENTITIES ? found.subList(0, MAX_ENTITIES) : found);
        }
    }

    /** Groups the exposed faces of the mirror blocks (nearest first) by plane; planes nearest the camera first. */
    private static List<Plane> buildPlanes(ClientLevel level, List<BlockPos> blocks, Vec3 eye) {
        Map<Long, PlaneBuilder> byKey = new HashMap<>();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (BlockPos pos : blocks) {
            for (Direction d : Direction.values()) {
                n.setWithOffset(pos, d);
                BlockState neighbour = level.getBlockState(n);
                if (isMirror(neighbour) || neighbour.isSolidRender(level, n)) {
                    continue;
                }
                Direction.Axis axis = d.getAxis();
                int sign = d.getAxisDirection().getStep();
                int coord = axis.choose(pos.getX(), pos.getY(), pos.getZ()) + (sign > 0 ? 1 : 0);
                long key = ((long) d.ordinal() << 32) | (coord & 0xFFFFFFFFL);
                byKey.computeIfAbsent(key, k -> new PlaneBuilder(axis, sign, coord))
                        .add(Plane.cellU(axis, pos), Plane.cellV(axis, pos));
            }
        }
        List<Plane> out = new ArrayList<>(byKey.size());
        for (PlaneBuilder b : byKey.values()) {
            out.add(b.build());
        }
        out.sort(Comparator.comparingDouble(p -> p.distanceSq(eye)));
        return List.copyOf(out.size() > MAX_PLANES ? out.subList(0, MAX_PLANES) : out);
    }

    private static final class PlaneBuilder {
        private final Direction.Axis axis;
        private final int sign;
        private final int coord;
        private int[] cells = new int[32];
        private int size;
        private int minU = Integer.MAX_VALUE;
        private int maxU = Integer.MIN_VALUE;
        private int minV = Integer.MAX_VALUE;
        private int maxV = Integer.MIN_VALUE;

        PlaneBuilder(Direction.Axis axis, int sign, int coord) {
            this.axis = axis;
            this.sign = sign;
            this.coord = coord;
        }

        void add(int u, int v) {
            if (size / 2 >= MAX_FACES_PER_PLANE) {
                return;
            }
            if (size + 2 > cells.length) {
                cells = Arrays.copyOf(cells, cells.length * 2);
            }
            cells[size++] = u;
            cells[size++] = v;
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        Plane build() {
            return new Plane(axis, sign, coord, Arrays.copyOf(cells, size), minU, maxU, minV, maxV);
        }
    }

    /**
     * One mirror plane: the faces looking along {@code sign} on {@code axis}, all lying on the block boundary
     * {@code coord}. Faces are unit cells in the plane's own (u, v) coordinates (X planes: u = z, v = y; Y planes:
     * u = x, v = z; Z planes: u = x, v = y), stored as pairs of cell origins; the bounds are inclusive cell origins.
     */
    record Plane(Direction.Axis axis, int sign, int coord, int[] cells, int minU, int maxU, int minV, int maxV) {
        static int cellU(Direction.Axis axis, BlockPos p) {
            return axis == Direction.Axis.X ? p.getZ() : p.getX();
        }

        static int cellV(Direction.Axis axis, BlockPos p) {
            return axis == Direction.Axis.Y ? p.getZ() : p.getY();
        }

        double a(Vec3 p) {
            return axis.choose(p.x, p.y, p.z);
        }

        double u(Vec3 p) {
            return axis == Direction.Axis.X ? p.z : p.x;
        }

        double v(Vec3 p) {
            return axis == Direction.Axis.Y ? p.z : p.y;
        }

        /** Signed distance in front of the plane: positive on the side its faces look at. */
        double front(Vec3 p) {
            return (a(p) - coord) * sign;
        }

        /** The mirror image of a point. */
        Vec3 reflect(Vec3 p) {
            return switch (axis) {
                case X -> new Vec3(2.0 * coord - p.x, p.y, p.z);
                case Y -> new Vec3(p.x, 2.0 * coord - p.y, p.z);
                case Z -> new Vec3(p.x, p.y, 2.0 * coord - p.z);
            };
        }

        /** The mirror image of a box. */
        AABB reflect(AABB b) {
            return switch (axis) {
                case X -> new AABB(2.0 * coord - b.maxX, b.minY, b.minZ, 2.0 * coord - b.minX, b.maxY, b.maxZ);
                case Y -> new AABB(b.minX, 2.0 * coord - b.maxY, b.minZ, b.maxX, 2.0 * coord - b.minY, b.maxZ);
                case Z -> new AABB(b.minX, b.minY, 2.0 * coord - b.maxZ, b.maxX, b.maxY, 2.0 * coord - b.minZ);
            };
        }

        /** Squared distance from {@code p} to the plane's bounding rectangle. */
        double distanceSq(Vec3 p) {
            double da = a(p) - coord;
            double du = outside(u(p), minU, maxU + 1);
            double dv = outside(v(p), minV, maxV + 1);
            return da * da + du * du + dv * dv;
        }

        private static double outside(double c, double min, double max) {
            return c < min ? min - c : c > max ? c - max : 0.0;
        }
    }
}
