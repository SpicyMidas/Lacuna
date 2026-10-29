package com.lacuna.mod.client.mirror;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.mirror.MirrorCapture.Draw;
import com.lacuna.mod.client.mirror.MirrorReflections.Plane;
import com.lacuna.mod.client.render.StencilSupport;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Faint reflections in Mirror Glass. Runs at {@code AFTER_TRANSLUCENT_BLOCKS}, so the glass itself is already drawn.
 * <p>
 * Planning (CPU): every mirror plane within the reflection range ({@code mirror.range}) that faces the camera and has
 * a face inside the frustum is on screen; the bounds of its visible faces give a pixel rectangle. Planes are ranked by
 * that rectangle's size, then by distance, and walking that ranking each plane collects the entities whose mirror image
 * can show in it (in front of it and within range, the image inside the frustum and projecting onto the visible faces,
 * an unobstructed light path to the glass, mirror blocks counting as opaque), until {@code mirror.maxPlanes} planes
 * reflect something. Then:
 * <ol>
 *   <li>record: each entity needed is rendered once per mirror axis (X, Y or Z) through its own renderer into
 *       {@link MirrorCapture}: at most {@value #MAX_RECORDS} renders a frame whatever the number of planes, the camera
 *       entity first (you show up in first person too), then nearest first. Billboards (experience orbs, thrown items,
 *       flames) are turned towards the mirrored camera, which only depends on the axis. Name tags, shadows and hit
 *       boxes are off;</li>
 *   <li>image: per reflecting plane, inside its pixel rectangle (scissor), a private offscreen target is cleared and the
 *       recorded geometry of the plane's entities is replayed under a true reflection matrix about the plane (swapped
 *       like a real mirror, winding flipped to match; translucent and glint layers last). The offscreen depth buffer
 *       makes the image occlude itself and keeps it independent from the world;</li>
 *   <li>mark: the plane's visible faces, nudged towards the viewer, set the shared stencil bit (depth-tested against
 *       the world, no colour/depth writes), so anything standing in front of the glass keeps its pixels;</li>
 *   <li>composite: inside the stencil, with the depth test off (the image sits "behind" the glass), a screen quad lays
 *       the offscreen image over the glass at about {@value #ALPHA} alpha, tinted cool silver-violet, with a slow
 *       shimmer;</li>
 *   <li>sheen: on every plane on screen, in one batch, a very soft additive gradient on the faces that follows the view
 *       direction (it slides as the viewer moves) and brightens at grazing angles, depth-tested like the mark, so the
 *       glass reads as a mirror even with nothing to reflect.</li>
 * </ol>
 * Fast/Fancy draw into the main target. Under Fabulous the glass lives in the translucent target, which is bound during
 * this stage: the composite goes there (it carries the shared stencil once the post chains were rebuilt), otherwise
 * into the main target, where the glass is laid over it later. Any exception restores the GL state and disables the
 * feature only; an entity type whose renderer throws is left out of the reflections instead.
 */
public final class MirrorRenderer {
    /** Planes on screen considered at most (largest first); the sheen covers them all. */
    private static final int MAX_VIEWS = 96;
    private static final int MAX_SHEEN_FACES = 8192;
    private static final int MAX_SHOWN_PER_PLANE = 24;
    /** Entity renders per frame at most: one per entity and mirror axis, shared by all the planes of that axis. */
    private static final int MAX_RECORDS = 64;
    /** Planes tested for entities per plane allowed to reflect (the rest of the ranking only gets the sheen). */
    private static final int TESTED_PER_IMAGE = 3;
    private static final double FINE_SHEEN_SQ = 16.0 * 16.0;
    private static final float FACE_OFFSET = 0.0025F;
    private static final float ALPHA = 0.35F;
    private static final float TINT_R = 0.83F;
    private static final float TINT_G = 0.81F;
    private static final float TINT_B = 1.0F;
    private static final float SHEEN_ALPHA = 0.07F;
    private static final int GRID = 8;
    private static final int SHEEN_SUB = 2;

    private static final MirrorCapture CAPTURE = new MirrorCapture();
    private static final Set<EntityType<?>> FAILED = new HashSet<>();
    private static TextureTarget image;
    private static boolean drawingImage;

    /** A plane on screen this frame: its faces inside the frustum, their cell bounds and pixel rectangle. */
    private record View(Plane plane, int[] faces, int minU, int maxU, int minV, int maxV, int x0, int y0, int x1, int y1,
                        double area, double distSq) {}

    /** A plane that reflects entities this frame. */
    private record Job(View view, List<Entity> shown) {}

    /** A reflectable entity this frame: its interpolated box, centre and eye. */
    private record Subject(Entity entity, float partial, AABB box, Vec3 center, Vec3 eye) {}

    /** The frame's plan: reflecting planes, and per mirror axis the entities to record (in order) and its first plane. */
    private static final class Frame {
        final List<Job> jobs = new ArrayList<>();
        final List<List<Subject>> records = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final List<Set<Entity>> recorded = List.of(identitySet(), identitySet(), identitySet());
        final Plane[] first = new Plane[3];
        int count;

        private static Set<Entity> identitySet() {
            return Collections.newSetFromMap(new IdentityHashMap<>());
        }
    }

    private MirrorRenderer() {}

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !MirrorReflections.isActive()
                || MirrorReflections.planes().isEmpty()) {
            return;
        }
        SafeRun.run(MirrorReflections.FEATURE, () -> render(event), () -> {
            MirrorReflections.reset();
            release();
        });
    }

    /** No name tags in the glass. */
    static void onNameTag(RenderNameTagEvent event) {
        if (drawingImage) {
            event.setCanRender(TriState.FALSE);
        }
    }

    /** Frees the offscreen target and the recorded geometry (render thread). */
    static void release() {
        drawingImage = false;
        if (image != null) {
            image.destroyBuffers();
            image = null;
        }
        CAPTURE.release();
        FAILED.clear();
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        int mask = StencilSupport.mask();
        if (level == null || mask == 0) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        RenderTarget translucent = mc.levelRenderer.getTranslucentTarget();
        boolean fabulous = Minecraft.useShaderTransparency() && translucent != null;
        RenderTarget stage = fabulous ? translucent : main;
        RenderTarget target = fabulous && translucent.isStencilEnabled() ? translucent : main;
        if (!target.isStencilEnabled()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        int range = MirrorReflections.range();
        Matrix4f clip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        List<View> views = views(cam, frustum, clip, target.width, target.height, range);
        if (views.isEmpty()) {
            return;
        }
        DeltaTracker delta = event.getPartialTick();
        Frame frame = plan(level, views, cam, frustum, delta, range);
        float time = level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
        try {
            if (!frame.jobs.isEmpty()) {
                images(mc, frame, cam, target, mask, time);
            }
            target.bindWrite(false);
            sheen(views, cam, time);
        } finally {
            StencilSupport.end();
            RenderSystem.disableScissor();
            VertexBuffer.unbind();
            GL11.glFrontFace(GL11.GL_CCW);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            stage.bindWrite(true);
        }
    }

    // ------------------------------------------------------------------ planning (CPU only)

    /** The planes on screen, largest on screen first, then nearest. */
    private static List<View> views(Vec3 cam, Frustum frustum, Matrix4f clip, int width, int height, int range) {
        double maxSq = (double) range * range;
        List<View> out = new ArrayList<>();
        Vector4f corner = new Vector4f();
        for (Plane p : MirrorReflections.planes()) {
            if (p.front(cam) <= 0.02) {
                continue;
            }
            double distSq = p.distanceSq(cam);
            if (distSq > maxSq || !frustum.isVisible(p.box(p.minU(), p.maxU(), p.minV(), p.maxV()))) {
                continue;
            }
            View v = view(p, cam, frustum, clip, corner, width, height, distSq);
            if (v != null) {
                out.add(v);
            }
        }
        out.sort(Comparator.comparingDouble((View v) -> -v.area()).thenComparingDouble(View::distSq));
        return out.size() > MAX_VIEWS ? List.copyOf(out.subList(0, MAX_VIEWS)) : out;
    }

    private static View view(Plane p, Vec3 cam, Frustum frustum, Matrix4f clip, Vector4f corner, int width, int height,
                             double distSq) {
        int[] cells = p.cells();
        int[] faces = new int[cells.length];
        int n = 0;
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;
        for (int i = 0; i < cells.length; i += 2) {
            int u = cells[i];
            int v = cells[i + 1];
            if (frustum.isVisible(p.box(u, u, v, v))) {
                faces[n++] = u;
                faces[n++] = v;
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
        }
        if (n == 0) {
            return null;
        }
        // pixel rectangle of the visible faces' bounds; a corner at or behind the camera plane means the whole screen
        double ra = p.coord() - p.a(cam);
        float sx0 = Float.MAX_VALUE;
        float sy0 = Float.MAX_VALUE;
        float sx1 = -Float.MAX_VALUE;
        float sy1 = -Float.MAX_VALUE;
        boolean whole = false;
        for (int c = 0; c < 4; c++) {
            float u = (float) (((c & 1) == 0 ? minU : maxU + 1) - p.u(cam));
            float v = (float) (((c & 2) == 0 ? minV : maxV + 1) - p.v(cam));
            switch (p.axis()) {
                case X -> corner.set((float) ra, v, u, 1.0F);
                case Y -> corner.set(u, (float) ra, v, 1.0F);
                case Z -> corner.set(u, v, (float) ra, 1.0F);
            }
            clip.transform(corner);
            if (corner.w <= 0.05F) {
                whole = true;
                break;
            }
            float x = corner.x / corner.w;
            float y = corner.y / corner.w;
            sx0 = Math.min(sx0, x);
            sx1 = Math.max(sx1, x);
            sy0 = Math.min(sy0, y);
            sy1 = Math.max(sy1, y);
        }
        int x0 = 0;
        int y0 = 0;
        int x1 = width;
        int y1 = height;
        if (!whole) {
            x0 = Mth.clamp(Mth.floor((sx0 + 1.0F) * 0.5F * width) - 2, 0, width);
            x1 = Mth.clamp(Mth.ceil((sx1 + 1.0F) * 0.5F * width) + 2, 0, width);
            y0 = Mth.clamp(Mth.floor((sy0 + 1.0F) * 0.5F * height) - 2, 0, height);
            y1 = Mth.clamp(Mth.ceil((sy1 + 1.0F) * 0.5F * height) + 2, 0, height);
            if (x1 <= x0 || y1 <= y0) {
                return null;
            }
        }
        double area = (double) (x1 - x0) * (y1 - y0) / ((double) width * height);
        return new View(p, n == faces.length ? faces : Arrays.copyOf(faces, n), minU, maxU, minV, maxV, x0, y0, x1, y1,
                area, distSq);
    }

    /** Which planes reflect which entities, within the plane and render budgets. */
    private static Frame plan(ClientLevel level, List<View> views, Vec3 cam, Frustum frustum, DeltaTracker delta, int range) {
        Frame frame = new Frame();
        List<Subject> subjects = subjects(level, delta);
        if (subjects.isEmpty()) {
            return frame;
        }
        int maxPlanes = MirrorReflections.maxPlanes();
        int tested = 0;
        for (View v : views) {
            if (frame.jobs.size() >= maxPlanes || tested++ >= maxPlanes * TESTED_PER_IMAGE) {
                break;
            }
            Plane p = v.plane();
            int axis = p.axis().ordinal();
            Set<Entity> recorded = frame.recorded.get(axis);
            List<Entity> shown = new ArrayList<>();
            for (Subject s : subjects) {
                if (shown.size() >= MAX_SHOWN_PER_PLANE) {
                    break;
                }
                boolean known = recorded.contains(s.entity());
                if (!known && frame.count >= MAX_RECORDS || !reflects(level, v, s, cam, frustum, range)) {
                    continue;
                }
                if (!known) {
                    recorded.add(s.entity());
                    frame.records.get(axis).add(s);
                    frame.count++;
                    if (frame.first[axis] == null) {
                        frame.first[axis] = p;
                    }
                }
                shown.add(s.entity());
            }
            if (!shown.isEmpty()) {
                frame.jobs.add(new Job(v, shown));
            }
        }
        return frame;
    }

    private static List<Subject> subjects(ClientLevel level, DeltaTracker delta) {
        List<Entity> entities = MirrorReflections.entities();
        List<Subject> out = new ArrayList<>(entities.size());
        for (Entity e : entities) {
            if (e.isRemoved() || e.level() != level || e.isInvisible() || e.isSpectator() || FAILED.contains(e.getType())) {
                continue;
            }
            float partial = delta.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(e));
            AABB box = e.getBoundingBox().move(x(e, partial) - e.getX(), y(e, partial) - e.getY(), z(e, partial) - e.getZ());
            out.add(new Subject(e, partial, box, box.getCenter(), e.getEyePosition(partial)));
        }
        return out;
    }

    private static double x(Entity e, float partial) {
        return e.tickCount == 0 ? e.getX() : Mth.lerp(partial, e.xOld, e.getX());
    }

    private static double y(Entity e, float partial) {
        return e.tickCount == 0 ? e.getY() : Mth.lerp(partial, e.yOld, e.getY());
    }

    private static double z(Entity e, float partial) {
        return e.tickCount == 0 ? e.getZ() : Mth.lerp(partial, e.zOld, e.getZ());
    }

    /** Whether the entity's mirror image can show in the plane's visible faces. */
    private static boolean reflects(ClientLevel level, View v, Subject s, Vec3 cam, Frustum frustum, double range) {
        Plane p = v.plane();
        double front = p.front(s.center());
        if (front <= 0.0 || front > range) {
            return false;
        }
        AABB mirrored = p.reflect(s.box());
        if (!frustum.isVisible(mirrored) || !projectsOnto(v, cam, mirrored)) {
            return false;
        }
        return reachesGlass(level, p, cam, s.eye()) || reachesGlass(level, p, cam, s.center());
    }

    /** Whether the image box, seen from the camera through the plane, overlaps the plane's visible faces. */
    private static boolean projectsOnto(View view, Vec3 cam, AABB box) {
        Plane p = view.plane();
        double ca = p.a(cam);
        double cu = p.u(cam);
        double cv = p.v(cam);
        double minU = Double.MAX_VALUE;
        double maxU = -Double.MAX_VALUE;
        double minV = Double.MAX_VALUE;
        double maxV = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 q = new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ);
            double denom = p.a(q) - ca;
            if (Math.abs(denom) < 1.0E-4) {
                return true;
            }
            double t = (p.coord() - ca) / denom;
            if (t <= 0.0 || t > 1.0) {
                return true; // the box crosses the plane: no reliable projection, keep it
            }
            double pu = cu + (p.u(q) - cu) * t;
            double pv = cv + (p.v(q) - cv) * t;
            minU = Math.min(minU, pu);
            maxU = Math.max(maxU, pu);
            minV = Math.min(minV, pv);
            maxV = Math.max(maxV, pv);
        }
        return maxU >= view.minU() - 0.25 && minU <= view.maxU() + 1.25
                && maxV >= view.minV() - 0.25 && minV <= view.maxV() + 1.25;
    }

    /**
     * Whether light from {@code point} reaches the plane where the camera would see its image: nothing may block the
     * segment from the point to the spot on the glass (no glimpses of entities around corners or through mirror walls).
     */
    private static boolean reachesGlass(ClientLevel level, Plane p, Vec3 cam, Vec3 point) {
        Vec3 mirrored = p.reflect(point);
        double ca = p.a(cam);
        double denom = p.a(mirrored) - ca;
        if (Math.abs(denom) < 1.0E-6) {
            return true;
        }
        double t = (p.coord() - ca) / denom;
        if (t <= 0.0 || t >= 1.0) {
            return true;
        }
        Vec3 spot = cam.add(mirrored.subtract(cam).scale(t));
        double nudge = 0.05 * p.sign();
        Vec3 to = switch (p.axis()) {
            case X -> spot.add(nudge, 0.0, 0.0);
            case Y -> spot.add(0.0, nudge, 0.0);
            case Z -> spot.add(0.0, 0.0, nudge);
        };
        return point.distanceToSqr(to) < 0.01 || clearPath(level, point, to);
    }

    /** A block raycast where mirror blocks are opaque and every other block uses its visual shape (glass is clear). */
    private static boolean clearPath(ClientLevel level, Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty());
        return BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> blocks(level, ctx, from, to, pos), ctx -> Boolean.TRUE);
    }

    private static Boolean blocks(ClientLevel level, ClipContext ctx, Vec3 from, Vec3 to, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (MirrorReflections.isMirror(state)) {
            return Boolean.FALSE;
        }
        return level.clipWithInteractionOverride(from, to, pos, ctx.getBlockShape(state, level, pos), state) != null
                ? Boolean.FALSE : null;
    }

    // ------------------------------------------------------------------ the images (offscreen)

    private static TextureTarget image(RenderTarget target) {
        if (image == null) {
            image = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
            image.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (image.width != target.width || image.height != target.height) {
            image.resize(target.width, target.height, Minecraft.ON_OSX);
        }
        return image;
    }

    /** Records the entities, then per reflecting plane: image, stencil mark, composite. */
    private static void images(Minecraft mc, Frame frame, Vec3 cam, RenderTarget target, int mask, float time) {
        TextureTarget rt = image(target);
        // bound while recording too: anything a renderer would draw directly lands here and is cleared below
        rt.bindWrite(true);
        List<Map<Entity, List<Draw>>> recorded = record(mc, frame, cam);
        for (Job job : frame.jobs) {
            View v = job.view();
            RenderSystem.enableScissor(v.x0(), v.y0(), v.x1() - v.x0(), v.y1() - v.y0());
            if (!drawImage(job, recorded.get(v.plane().axis().ordinal()), cam, rt)) {
                continue;
            }
            target.bindWrite(false);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            StencilSupport.clear(mask);

            // mark the visible faces of this plane
            StencilSupport.mark(mask);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            drawFaces(v, cam);

            // inside the marked faces, the image (depth test off: it is behind the glass)
            StencilSupport.inside(mask);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            composite(rt.getColorTextureId(), time);
            StencilSupport.clear(mask);
            StencilSupport.end();
        }
        RenderSystem.disableScissor();
    }

    /** Renders each entity the frame needs once per mirror axis; the draws per axis, by entity. */
    private static List<Map<Entity, List<Draw>>> record(Minecraft mc, Frame frame, Vec3 cam) {
        List<Map<Entity, List<Draw>>> out = List.of(new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Quaternionf orientation = dispatcher.cameraOrientation();
        boolean hitBoxes = dispatcher.shouldRenderHitBoxes();
        CAPTURE.frame();
        dispatcher.setRenderShadow(false);
        dispatcher.setRenderHitBoxes(false);
        drawingImage = true;
        try {
            for (Direction.Axis axis : Direction.Axis.values()) {
                List<Subject> subjects = frame.records.get(axis.ordinal());
                Plane first = frame.first[axis.ordinal()];
                if (subjects.isEmpty() || first == null) {
                    continue;
                }
                dispatcher.overrideCameraOrientation(mirrored(orientation, axis));
                // translucent layers sorted as seen in the first (largest) plane of this axis
                float k = (float) (2.0 * (first.coord() - first.a(cam)));
                VertexSorting sorting = VertexSorting.byDistance(axis == Direction.Axis.X ? k : 0.0F,
                        axis == Direction.Axis.Y ? k : 0.0F, axis == Direction.Axis.Z ? k : 0.0F);
                Map<Entity, List<Draw>> draws = out.get(axis.ordinal());
                for (Subject s : subjects) {
                    Entity e = s.entity();
                    if (FAILED.contains(e.getType())) {
                        continue;
                    }
                    float partial = s.partial();
                    try {
                        draws.put(e, CAPTURE.record(dispatcher, e, x(e, partial) - cam.x, y(e, partial) - cam.y,
                                z(e, partial) - cam.z, Mth.lerp(partial, e.yRotO, e.getYRot()), partial,
                                dispatcher.getPackedLightCoords(e, partial), sorting));
                    } catch (RuntimeException ex) {
                        if (FAILED.add(e.getType())) {
                            Lacuna.LOGGER.warn("Lacuna: {} cannot be drawn in mirrors; left out of reflections",
                                    BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()), ex);
                        }
                    }
                }
            }
        } finally {
            drawingImage = false;
            dispatcher.overrideCameraOrientation(orientation);
            dispatcher.setRenderShadow(true);
            dispatcher.setRenderHitBoxes(hitBoxes);
            VertexBuffer.unbind();
        }
        return out;
    }

    /**
     * The camera orientation for an axis' mirror world. Recorded with it, a camera-facing billboard faces the camera
     * again once reflected: the reflection times this rotation is the camera's rotation times a flip in the sprite's own
     * plane (left-right for walls, upside down for floors and ceilings), as a real mirror would show it.
     */
    private static Quaternionf mirrored(Quaternionf camera, Direction.Axis axis) {
        Matrix3f reflection = new Matrix3f().scaling(axis == Direction.Axis.X ? -1.0F : 1.0F,
                axis == Direction.Axis.Y ? -1.0F : 1.0F, axis == Direction.Axis.Z ? -1.0F : 1.0F);
        Matrix3f flip = axis == Direction.Axis.Y ? new Matrix3f().scaling(1.0F, -1.0F, 1.0F)
                : new Matrix3f().scaling(-1.0F, 1.0F, 1.0F);
        return reflection.mul(new Matrix3f().set(camera)).mul(flip).getNormalizedRotation(new Quaternionf());
    }

    /** Clears the image inside the scissor and replays the plane's entities mirrored; false when there is nothing. */
    private static boolean drawImage(Job job, Map<Entity, List<Draw>> recorded, Vec3 cam, TextureTarget rt) {
        List<Draw> draws = new ArrayList<>();
        for (Entity e : job.shown()) {
            List<Draw> d = recorded.get(e);
            if (d != null) {
                draws.addAll(d);
            }
        }
        if (draws.isEmpty()) {
            return false;
        }
        StencilSupport.end();
        rt.bindWrite(false);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // reflection about the plane, in camera-relative coordinates: a' = 2 (coord - cam_a) - a
        Plane p = job.view().plane();
        float k = (float) (2.0 * (p.coord() - p.a(cam)));
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            switch (p.axis()) {
                case X -> modelView.translate(k, 0.0F, 0.0F).scale(-1.0F, 1.0F, 1.0F);
                case Y -> modelView.translate(0.0F, k, 0.0F).scale(1.0F, -1.0F, 1.0F);
                case Z -> modelView.translate(0.0F, 0.0F, k).scale(1.0F, 1.0F, -1.0F);
            }
            RenderSystem.applyModelViewMatrix();
            // the reflection flips the winding: keep culling the real back faces
            GL11.glFrontFace(GL11.GL_CW);
            for (Draw d : draws) {
                if (!d.late()) {
                    MirrorCapture.replay(d, rt);
                }
            }
            for (Draw d : draws) {
                if (d.late()) {
                    MirrorCapture.replay(d, rt);
                }
            }
        } finally {
            VertexBuffer.unbind();
            GL11.glFrontFace(GL11.GL_CCW);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        return true;
    }

    // ------------------------------------------------------------------ marking, composite and sheen

    private static void drawFaces(View view, Vec3 cam) {
        Plane p = view.plane();
        double ra = p.coord() + p.sign() * FACE_OFFSET - p.a(cam);
        double cu = p.u(cam);
        double cv = p.v(cam);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int[] faces = view.faces();
        for (int i = 0; i < faces.length; i += 2) {
            double u = faces[i] - cu;
            double v = faces[i + 1] - cv;
            vertex(bb, p.axis(), ra, u, v);
            vertex(bb, p.axis(), ra, u + 1.0, v);
            vertex(bb, p.axis(), ra, u + 1.0, v + 1.0);
            vertex(bb, p.axis(), ra, u, v + 1.0);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    /** Adds the camera-relative plane point ({@code ra} along the axis, {@code ru}, {@code rv} in the plane). */
    private static VertexConsumer vertex(BufferBuilder bb, Direction.Axis axis, double ra, double ru, double rv) {
        return switch (axis) {
            case X -> bb.addVertex((float) ra, (float) rv, (float) ru);
            case Y -> bb.addVertex((float) ru, (float) ra, (float) rv);
            case Z -> bb.addVertex((float) ru, (float) rv, (float) ra);
        };
    }

    /** The offscreen image laid over the whole screen (the stencil keeps it to the faces), tinted, shimmering. */
    private static void composite(int texture, float time) {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, texture);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    gridPoint(bb, i, j, time);
                    gridPoint(bb, i + 1, j, time);
                    gridPoint(bb, i + 1, j + 1, time);
                    gridPoint(bb, i, j + 1, time);
                }
            }
            BufferUploader.drawWithShader(bb.buildOrThrow());
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void gridPoint(BufferBuilder bb, int i, int j, float time) {
        float s = (float) i / GRID;
        float t = (float) j / GRID;
        float shimmer = 0.84F + 0.10F * Mth.sin(time * 0.07F + s * 5.1F - t * 3.3F)
                + 0.06F * Mth.sin(time * 0.031F + t * 9.0F + s * 1.7F);
        bb.addVertex(s * 2.0F - 1.0F, t * 2.0F - 1.0F, 0.0F).setUv(s, t).setColor(TINT_R, TINT_G, TINT_B, ALPHA * shimmer);
    }

    /**
     * Soft additive bands on every plane on screen, in one batch: they follow the view direction and brighten at
     * grazing angles. Depth-tested with the faces nudged towards the viewer, like the stencil mark.
     */
    private static void sheen(List<View> views, Vec3 cam, float time) {
        float drift = 0.08F * Mth.sin(time * 0.004F);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int budget = MAX_SHEEN_FACES;
        for (View view : views) {
            Plane p = view.plane();
            double ra = p.coord() + p.sign() * FACE_OFFSET - p.a(cam);
            double cu = p.u(cam);
            double cv = p.v(cam);
            int sub = view.distSq() <= FINE_SHEEN_SQ ? SHEEN_SUB : 1;
            float step = 1.0F / sub;
            int[] faces = view.faces();
            for (int i = 0; i < faces.length && budget > 0; i += 2, budget--) {
                for (int su = 0; su < sub; su++) {
                    for (int sv = 0; sv < sub; sv++) {
                        double u0 = faces[i] + su * step - cu;
                        double v0 = faces[i + 1] + sv * step - cv;
                        sheenPoint(bb, p.axis(), ra, u0, v0, drift);
                        sheenPoint(bb, p.axis(), ra, u0 + step, v0, drift);
                        sheenPoint(bb, p.axis(), ra, u0 + step, v0 + step, drift);
                        sheenPoint(bb, p.axis(), ra, u0, v0 + step, drift);
                    }
                }
            }
        }
        MeshData mesh = bb.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.defaultBlendFunc();
    }

    private static void sheenPoint(BufferBuilder bb, Direction.Axis axis, double ra, double ru, double rv, float drift) {
        double len = Math.sqrt(ra * ra + ru * ru + rv * rv) + 1.0E-6;
        // in-plane part of the view ray (the reflection keeps it): moving changes it, so the bands slide
        float b = (float) ((ru * 0.8 + rv * 0.55) / len) + drift;
        float grazing = 1.0F - (float) (Math.abs(ra) / len);
        float glow = 0.2F + band(b - 0.18F, 0.22F) + 0.55F * band(b + 0.52F, 0.12F) + 0.35F * grazing * grazing;
        vertex(bb, axis, ra, ru, rv).setColor(0.80F, 0.84F, 1.0F, Math.min(1.0F, SHEEN_ALPHA * glow));
    }

    private static float band(float x, float width) {
        return (float) Math.exp(-(x * x) / (width * width));
    }
}
