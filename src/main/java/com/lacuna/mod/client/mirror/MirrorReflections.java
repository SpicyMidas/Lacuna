package com.lacuna.mod.client.mirror;

import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.compat.ShaderPackCompat;
import com.lacuna.mod.client.render.SectionSweep;
import com.lacuna.mod.client.render.StencilSupport;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.util.SafeRun;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Mirror Glass reflections, client state behind {@link MirrorRenderer}.
 * <p>
 * A budgeted {@link SectionSweep} finds the mirror blocks ({@code lacuna:mirror_glass} and the cracked
 * {@code lacuna:labyrinth_exit}) within the reflection range of the camera ({@code mirror.range} in lacuna-client.toml,
 * 32 blocks by default); sections without them cost a palette check. A new sweep starts every {@value #SWEEP_EVERY}
 * ticks, so placed or broken mirrors show up within a fraction of a second and planes a moving camera left behind are
 * dropped. The exposed faces (the neighbour is neither a mirror block nor a solid cube) are grouped by axis-aligned
 * plane: one direction and one block-boundary coordinate per plane, so coplanar mirrors share a plane however far apart
 * they are, while a floor, a ceiling and each wall get their own. While some plane exists, the entities of every kind
 * around the camera (players including you, creatures, dropped items, projectiles, minecarts, boats, experience orbs,
 * armour stands, falling blocks, item frames, paintings, this mod's own entities...) are listed every
 * {@value #ENTITY_REFRESH_EVERY} ticks, nearest first. Nothing is kept, and nothing is drawn, when no mirror is near.
 * Any failure disables the {@value #FEATURE} feature only.
 */
public final class MirrorReflections {
    public static final String FEATURE = "mirror_reflections";
    private static final int MAX_BLOCKS = 8192;
    private static final int MAX_PLANES = 192;
    private static final int MAX_FACES_PER_PLANE = 2048;
    private static final int MAX_ENTITIES = 96;
    private static final int BLOCK_BUDGET = 65_536;
    private static final int SWEEP_EVERY = 4;
    private static final int ENTITY_REFRESH_EVERY = 2;
    /** The offscreen reflection target is freed after this long without any mirror around. */
    private static final int RELEASE_AFTER = 600;

    private static List<Plane> planes = List.of();
    private static List<Entity> entities = List.of();
    private static SectionSweep sweep;
    private static ClientLevel sweptLevel;
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

    /** The single "reflections on" switch: config toggle, feature health and stencil availability. */
    public static boolean isActive() {
        return LacunaClientConfig.get(LacunaClientConfig.MIRROR_REFLECTIONS)
                && !SafeRun.isDisabled(FEATURE)
                && !StencilSupport.failed()
                && !ShaderPackCompat.active();
    }

    /** Reflection range in blocks: mirrors this close to the camera, entities this far in front of a mirror. */
    static int range() {
        return LacunaClientConfig.get(LacunaClientConfig.MIRROR_RANGE);
    }

    /** How many planes may reflect entities in one frame. */
    static int maxPlanes() {
        return LacunaClientConfig.get(LacunaClientConfig.MIRROR_MAX_PLANES);
    }

    static List<Plane> planes() {
        return planes;
    }

    /** Reflectable entities around the camera, the camera entity first, then nearest first. */
    static List<Entity> entities() {
        return entities;
    }

    static boolean isMirror(BlockState state) {
        if (mirror == null) {
            mirror = ModBlocks.MIRROR_GLASS.get();
            crackedMirror = ModBlocks.LABYRINTH_EXIT.get();
        }
        return state.is(mirror) || state.is(crackedMirror);
    }

    /**
     * Entities a mirror can show: anything with a real renderer that is not removed, invisible or a spectator. Parts of
     * multipart creatures are drawn by their parent; renderers that drew nothing in vanilla (markers, area clouds) are
     * skipped so they spend no budget.
     */
    static boolean reflectable(Entity e, EntityRenderDispatcher dispatcher) {
        if (e.isRemoved() || e.isSpectator() || e.isInvisible() || e instanceof PartEntity<?>) {
            return false;
        }
        EntityRenderer<?> renderer = dispatcher.getRenderer(e);
        return renderer != null && !(renderer instanceof NoopRenderer<?>);
    }

    static void reset() {
        planes = List.of();
        entities = List.of();
        sweep = null;
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        sweptLevel = null;
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
        if (level != sweptLevel) {
            // another dimension or world: nothing found in the old one applies
            reset();
            sweptLevel = level;
            sweepStart = ticks - SWEEP_EVERY;
        }
        Vec3 eye = camera.getPosition();
        int range = range();
        if (sweep == null && ticks - sweepStart >= SWEEP_EVERY) {
            sweep = new SectionSweep(level, eye, range, MirrorReflections::isMirror);
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
            entities = gather(mc, level, eye, range);
        }
    }

    private static List<Entity> gather(Minecraft mc, ClientLevel level, Vec3 eye, int range) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Entity viewer = mc.getCameraEntity();
        double maxSq = (double) range * range;
        List<Entity> found = level.getEntities((Entity) null, new AABB(eye, eye).inflate(range),
                e -> reflectable(e, dispatcher) && e.distanceToSqr(eye) <= maxSq);
        found.sort(Comparator.comparingDouble(e -> e == viewer ? -1.0 : e.distanceToSqr(eye)));
        return List.copyOf(found.size() > MAX_ENTITIES ? found.subList(0, MAX_ENTITIES) : found);
    }

    /** Groups the exposed faces of the mirror blocks (nearest first) by plane; planes nearest the camera first. */
    private static List<Plane> buildPlanes(ClientLevel level, List<BlockPos> blocks, Vec3 eye) {
        LongOpenHashSet known = new LongOpenHashSet(blocks.size() * 2);
        for (BlockPos pos : blocks) {
            known.add(pos.asLong());
        }
        Map<Long, PlaneBuilder> byKey = new HashMap<>();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (BlockPos pos : blocks) {
            for (Direction d : Direction.values()) {
                n.setWithOffset(pos, d);
                if (known.contains(n.asLong())) {
                    continue;
                }
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

        /** A thin box around the cells between the given inclusive cell bounds. */
        AABB box(int u0, int u1, int v0, int v1) {
            double a0 = coord - 0.01;
            double a1 = coord + 0.01;
            return switch (axis) {
                case X -> new AABB(a0, v0, u0, a1, v1 + 1, u1 + 1);
                case Y -> new AABB(u0, a0, v0, u1 + 1, a1, v1 + 1);
                case Z -> new AABB(u0, v0, a0, u1 + 1, v1 + 1, a1);
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
