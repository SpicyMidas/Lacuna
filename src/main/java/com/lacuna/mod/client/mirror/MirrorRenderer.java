package com.lacuna.mod.client.mirror;

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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * Faint reflections in Mirror Glass. Runs at {@code AFTER_TRANSLUCENT_BLOCKS}, so the glass itself is already drawn.
 * For each of the (at most {@value #MAX_PLANES}) mirror planes nearest the camera that face it:
 * <ol>
 *   <li>image: the creatures in front of the plane whose mirror image can show in it (frustum, projection onto the
 *       plane's faces, an unobstructed light path to the glass) are drawn with their own renderers, armour and held
 *       items included, into a private offscreen target, under a true reflection matrix about the plane (the image is
 *       left-right swapped like a real mirror; winding flipped to match, name tags, shadows and hit boxes off). The
 *       offscreen depth buffer makes the image occlude itself properly and keeps it independent from the world;</li>
 *   <li>mark: the plane's visible faces, nudged towards the viewer, set the shared stencil bit (depth-tested against
 *       the world, no colour/depth writes), so anything standing in front of the glass keeps its pixels;</li>
 *   <li>composite: inside the stencil, with the depth test off (the image sits "behind" the glass), a screen quad lays
 *       the offscreen image over the glass at about {@value #ALPHA} alpha, tinted cool silver-violet, with a slow
 *       shimmer;</li>
 *   <li>sheen: a very soft additive gradient on the faces that follows the view direction (it slides as the viewer
 *       moves) and brightens at grazing angles, so the glass reads as a mirror even with nothing to reflect.</li>
 * </ol>
 * Fast/Fancy draw into the main target. Under Fabulous the glass lives in the translucent target, which is bound during
 * this stage: the composite goes there (it carries the shared stencil once the post chains were rebuilt), otherwise
 * into the main target, where the glass is laid over it later. Render types that would switch to another target in the
 * image pass are dropped. Any exception restores the GL state and disables the feature only.
 */
public final class MirrorRenderer {
    private static final int MAX_PLANES = 3;
    private static final int MAX_REFLECTED = 8;
    private static final double PLANE_RANGE = MirrorReflections.SCAN_RANGE;
    private static final float FACE_OFFSET = 0.0025F;
    private static final float ALPHA = 0.35F;
    private static final float TINT_R = 0.83F;
    private static final float TINT_G = 0.81F;
    private static final float TINT_B = 1.0F;
    private static final float SHEEN_ALPHA = 0.07F;
    private static final int GRID = 8;
    private static final int SHEEN_SUB = 2;
    private static final int OUT_MAIN = 0;
    private static final int OUT_FABULOUS_TARGET = 1;
    private static final int OUT_ELSEWHERE = 2;
    private static final String[] FABULOUS_TARGETS = {
            "item_entity_target", "translucent_target", "particles_target", "weather_target", "clouds_target"};

    private static final Map<RenderType, Integer> OUTPUTS = new IdentityHashMap<>();
    private static TextureTarget image;
    private static boolean drawingImage;

    private record Job(Plane plane, int[] faces, List<LivingEntity> shown) {}

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

    /** Frees the offscreen target (render thread). */
    static void release() {
        drawingImage = false;
        if (image != null) {
            image.destroyBuffers();
            image = null;
        }
        OUTPUTS.clear();
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
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<Job> jobs = plan(level, cam, event.getFrustum(), partial);
        if (jobs.isEmpty()) {
            return;
        }
        float time = level.getGameTime() + partial;
        // vanilla flushes its pending batches (block outline lines) right after this stage: do it now, into the stage
        // target, so the image pass below starts from an empty buffer source
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        buffers.endBatch();
        try {
            for (Job job : jobs) {
                boolean hasImage = !job.shown().isEmpty() && drawImage(mc, buffers, job, cam, partial, main, Minecraft.useShaderTransparency());
                target.bindWrite(true);
                StencilSupport.begin(mask);

                // mark the visible faces of this plane
                StencilSupport.mark(mask);
                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.depthMask(false);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.disableCull();
                RenderSystem.disableBlend();
                drawFaces(job, cam);

                // inside the marked faces: the image, then the sheen (depth test off: the image is behind the glass)
                StencilSupport.inside(mask);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.disableDepthTest();
                RenderSystem.enableBlend();
                if (hasImage) {
                    composite(image.getColorTextureId(), time);
                }
                sheen(job, cam, time);
                StencilSupport.clear(mask);
            }
        } finally {
            StencilSupport.end();
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

    /** The nearest planes facing the camera with a face on screen, and the creatures each one reflects. */
    private static List<Job> plan(ClientLevel level, Vec3 cam, Frustum frustum, float partial) {
        List<Plane> facing = new ArrayList<>();
        double maxSq = PLANE_RANGE * PLANE_RANGE;
        for (Plane p : MirrorReflections.planes()) {
            if (p.front(cam) > 0.02 && p.distanceSq(cam) <= maxSq) {
                facing.add(p);
            }
        }
        if (facing.isEmpty()) {
            return List.of();
        }
        facing.sort(Comparator.comparingDouble(p -> p.distanceSq(cam)));
        List<Job> jobs = new ArrayList<>(MAX_PLANES);
        int budget = MAX_REFLECTED;
        for (Plane p : facing) {
            int[] faces = visibleFaces(p, frustum);
            if (faces.length == 0) {
                continue;
            }
            List<LivingEntity> shown = budget > 0 ? reflected(level, p, cam, frustum, partial, budget) : List.of();
            budget -= shown.size();
            jobs.add(new Job(p, faces, shown));
            if (jobs.size() >= MAX_PLANES) {
                break;
            }
        }
        return jobs;
    }

    private static int[] visibleFaces(Plane p, Frustum frustum) {
        int[] cells = p.cells();
        int[] out = new int[cells.length];
        int n = 0;
        for (int i = 0; i < cells.length; i += 2) {
            int u = cells[i];
            int v = cells[i + 1];
            if (frustum.isVisible(faceBox(p, u, v))) {
                out[n++] = u;
                out[n++] = v;
            }
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    private static AABB faceBox(Plane p, int u, int v) {
        double a0 = p.coord() - 0.01;
        double a1 = p.coord() + 0.01;
        return switch (p.axis()) {
            case X -> new AABB(a0, v, u, a1, v + 1, u + 1);
            case Y -> new AABB(u, a0, v, u + 1, a1, v + 1);
            case Z -> new AABB(u, v, a0, u + 1, v + 1, a1);
        };
    }

    private static List<LivingEntity> reflected(ClientLevel level, Plane p, Vec3 cam, Frustum frustum, float partial, int budget) {
        List<LivingEntity> out = new ArrayList<>(Math.min(budget, 4));
        for (LivingEntity e : MirrorReflections.entities()) {
            if (out.size() >= budget) {
                break;
            }
            if (e.isRemoved() || e.level() != level || e.isInvisible() || e.isSpectator()) {
                continue;
            }
            AABB box = e.getBoundingBox().move(Mth.lerp(partial, e.xOld, e.getX()) - e.getX(),
                    Mth.lerp(partial, e.yOld, e.getY()) - e.getY(), Mth.lerp(partial, e.zOld, e.getZ()) - e.getZ());
            Vec3 center = box.getCenter();
            double front = p.front(center);
            if (front <= 0.0 || front > MirrorReflections.ENTITY_RANGE) {
                continue;
            }
            AABB mirrored = p.reflect(box);
            if (!frustum.isVisible(mirrored) || !projectsOnto(p, cam, mirrored)) {
                continue;
            }
            if (!reachesGlass(level, p, cam, e.getEyePosition(partial)) && !reachesGlass(level, p, cam, center)) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** Whether the image box, seen from the camera through the plane, overlaps the plane's faces. */
    private static boolean projectsOnto(Plane p, Vec3 cam, AABB box) {
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
        return maxU >= p.minU() - 0.25 && minU <= p.maxU() + 1.25 && maxV >= p.minV() - 0.25 && minV <= p.maxV() + 1.25;
    }

    /**
     * Whether light from {@code point} reaches the plane where the camera would see its image: nothing may block the
     * segment from the point to the spot on the glass (no glimpses of creatures around corners).
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
        if (point.distanceToSqr(to) < 0.01) {
            return true;
        }
        return level.clip(new ClipContext(point, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ the image (offscreen)

    private static TextureTarget image(RenderTarget main) {
        if (image == null) {
            image = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            image.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (image.width != main.width || image.height != main.height) {
            image.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        return image;
    }

    /** Draws the plane's creatures, mirrored, into the offscreen image; true when at least one was drawn. */
    private static boolean drawImage(Minecraft mc, MultiBufferSource.BufferSource source, Job job, Vec3 cam, float partial,
                                     RenderTarget main, boolean shaderTransparency) {
        TextureTarget rt = image(main);
        StencilSupport.end();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        rt.clear(Minecraft.ON_OSX);
        rt.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // reflection about the plane, in camera-relative coordinates: a' = 2 (coord - cam_a) - a
        Plane p = job.plane();
        PoseStack pose = new PoseStack();
        float k = (float) (2.0 * (p.coord() - p.a(cam)));
        switch (p.axis()) {
            case X -> {
                pose.translate(k, 0.0F, 0.0F);
                pose.scale(-1.0F, 1.0F, 1.0F);
            }
            case Y -> {
                pose.translate(0.0F, k, 0.0F);
                pose.scale(1.0F, -1.0F, 1.0F);
            }
            case Z -> {
                pose.translate(0.0F, 0.0F, k);
                pose.scale(1.0F, 1.0F, -1.0F);
            }
        }
        MultiBufferSource filtered = type -> stays(type, shaderTransparency) ? source.getBuffer(type) : Discard.INSTANCE;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        boolean hitBoxes = dispatcher.shouldRenderHitBoxes();
        int drawn = 0;
        boolean flushed = false;
        dispatcher.setRenderShadow(false);
        dispatcher.setRenderHitBoxes(false);
        drawingImage = true;
        // the reflection flips the winding: keep culling the real back faces
        GL11.glFrontFace(GL11.GL_CW);
        try {
            for (LivingEntity e : job.shown()) {
                double x = Mth.lerp(partial, e.xOld, e.getX()) - cam.x;
                double y = Mth.lerp(partial, e.yOld, e.getY()) - cam.y;
                double z = Mth.lerp(partial, e.zOld, e.getZ()) - cam.z;
                float yRot = Mth.lerp(partial, e.yRotO, e.getYRot());
                dispatcher.render(e, x, y, z, yRot, partial, pose, filtered, dispatcher.getPackedLightCoords(e, partial));
                drawn++;
            }
            source.endBatch();
            flushed = true;
        } finally {
            if (!flushed) {
                // a renderer failed mid-batch: whatever it left goes into the image, never into the world view
                try {
                    source.endBatch();
                } catch (RuntimeException ignored) {
                    // the feature is being disabled anyway
                }
            }
            GL11.glFrontFace(GL11.GL_CCW);
            drawingImage = false;
            dispatcher.setRenderShadow(true);
            dispatcher.setRenderHitBoxes(hitBoxes);
        }
        return drawn > 0;
    }

    /** Render types that keep drawing into the bound target (the image) instead of switching to another one. */
    private static boolean stays(RenderType type, boolean shaderTransparency) {
        Integer out = OUTPUTS.get(type);
        if (out == null) {
            if (OUTPUTS.size() > 1024) {
                OUTPUTS.clear();
            }
            out = output(type);
            OUTPUTS.put(type, out);
        }
        return out == OUT_MAIN || out == OUT_FABULOUS_TARGET && !shaderTransparency;
    }

    private static int output(RenderType type) {
        if (type.isOutline()) {
            return OUT_ELSEWHERE;
        }
        String s = type.toString();
        if (s.contains("outline_target")) {
            return OUT_ELSEWHERE;
        }
        for (String t : FABULOUS_TARGETS) {
            if (s.contains(t)) {
                return OUT_FABULOUS_TARGET;
            }
        }
        return OUT_MAIN;
    }

    // ------------------------------------------------------------------ marking, composite and sheen

    private static void drawFaces(Job job, Vec3 cam) {
        Plane p = job.plane();
        double ra = p.coord() + p.sign() * FACE_OFFSET - p.a(cam);
        double cu = p.u(cam);
        double cv = p.v(cam);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int[] faces = job.faces();
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

    /** Soft additive bands that follow the view direction, brighter at grazing angles. */
    private static void sheen(Job job, Vec3 cam, float time) {
        Plane p = job.plane();
        double ra = p.coord() + p.sign() * FACE_OFFSET - p.a(cam);
        double cu = p.u(cam);
        double cv = p.v(cam);
        float drift = 0.08F * Mth.sin(time * 0.004F);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int[] faces = job.faces();
        float step = 1.0F / SHEEN_SUB;
        for (int i = 0; i < faces.length; i += 2) {
            for (int su = 0; su < SHEEN_SUB; su++) {
                for (int sv = 0; sv < SHEEN_SUB; sv++) {
                    double u0 = faces[i] + su * step - cu;
                    double v0 = faces[i + 1] + sv * step - cv;
                    sheenPoint(bb, p.axis(), ra, u0, v0, drift);
                    sheenPoint(bb, p.axis(), ra, u0 + step, v0, drift);
                    sheenPoint(bb, p.axis(), ra, u0 + step, v0 + step, drift);
                    sheenPoint(bb, p.axis(), ra, u0, v0 + step, drift);
                }
            }
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
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

    /** Swallows the geometry of render types that would draw outside the image. */
    private enum Discard implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
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
}
