package com.lacuna.mod.client.sight;

import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
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
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * The Divine Glass X-ray window. Runs at {@code AFTER_BLOCK_ENTITIES}: the opaque world, entities and block entities
 * are in the main target, translucent terrain (water, the glass itself), particles, clouds and weather are not yet.
 * <ol>
 *   <li>mark: the visible Divine Glass shapes (depth-tested against the world, no colour/depth writes) set our
 *       stencil bit;</li>
 *   <li>void: inside the stencil a full-screen quad paints the dark divine void and pushes depth to the far plane;</li>
 *   <li>reveal: target blocks (real models and block-entity renderers, full-bright, faint outline) and living
 *       entities (models plus hostile red / passive green boxes) are drawn with normal depth testing, stencil-clipped;</li>
 *   <li>seal: the glass shapes write their own depth (pushed back a hair) inside the stencil, so everything drawn
 *       later that lies behind the glass (water, particles, clouds, rain) stays hidden, while the vanilla glass
 *       itself still renders on top as the window's sheen.</li>
 * </ol>
 * Fast and Fancy draw everything into the main target. Under Fabulous the translucent layers live in their own
 * targets, which copy their depth from the main target after this stage, so the seal hides them too; only the
 * translucent layers of the revealed entities (drawn into the item-entity target, which has no stencil and an older
 * depth copy) may be missing. Any exception restores the GL state and disables the window (outline fallback).
 */
public final class DivineXrayRenderer {
    private static final int FAR_STENCIL_DEFAULT = -1;
    private static final float VOID_Z = 0.99995F;
    private static final RenderType XRAY_LINES = RenderType.create("lacuna_xray_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private record Window(BlockPos pos, VoxelShape shape) {}

    private DivineXrayRenderer() {}

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || !DivineXray.isActive()
                || DivineXray.glass().isEmpty()) {
            return;
        }
        SafeRun.run(DivineXray.FEATURE, () -> render(event), DivineXray::reset);
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        int mask = DivineXray.stencilMask();
        if (level == null || mask == 0) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        List<Window> windows = visibleGlass(level, cam, frustum);
        if (windows.isEmpty()) {
            return;
        }
        DivineXray.markVisible();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // flush whatever vanilla still has pending so that none of it gets clipped by our stencil
        buffers.endBatch();
        mc.getMainRenderTarget().bindWrite(false);

        float fogStart = RenderSystem.getShaderFogStart();
        boolean projectionSaved = false;
        boolean modelViewPushed = false;
        try {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            RenderSystem.stencilMask(mask);
            RenderSystem.clearStencil(0);
            RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);

            // 1) mark the visible glass
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, mask, mask);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            drawGlass(pose, windows, cam);

            // 2) the void: colour + far depth, only inside the window
            RenderSystem.stencilFunc(GL11.GL_EQUAL, mask, mask);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.backupProjectionMatrix();
            projectionSaved = true;
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelViewPushed = true;
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            drawVoid(level.getGameTime() + partial);
            modelView.popMatrix();
            modelViewPushed = false;
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            projectionSaved = false;

            // 3) reveal targets and creatures (normal depth test, no fog: they float in the void)
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            reveal(mc, level, pose, buffers, cam, frustum, partial);
            RenderSystem.setShaderFogStart(fogStart);

            // 4) seal: glass depth (pushed back a hair) so later translucent/particle passes behind it stay hidden
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(1.0F, 4.0F);
            drawGlass(pose, windows, cam);
        } finally {
            if (modelViewPushed) {
                RenderSystem.getModelViewStack().popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            if (projectionSaved) {
                RenderSystem.restoreProjectionMatrix();
            }
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, FAR_STENCIL_DEFAULT);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.stencilMask(FAR_STENCIL_DEFAULT);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Divine Glass within range, still glass, with a non-empty shape inside the view frustum. */
    private static List<Window> visibleGlass(ClientLevel level, Vec3 cam, Frustum frustum) {
        List<Window> out = new ArrayList<>();
        double maxSq = (DivineXray.GLASS_RANGE + 1.0) * (DivineXray.GLASS_RANGE + 1.0);
        for (BlockPos pos : DivineXray.glass()) {
            if (pos.distToCenterSqr(cam) > maxSq) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModTags.Blocks.DIVINE_GLASS)) {
                continue;
            }
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty() || !frustum.isVisible(shape.bounds().move(pos))) {
                continue;
            }
            out.add(new Window(pos, shape));
        }
        return out;
    }

    private static void drawGlass(PoseStack pose, List<Window> windows, Vec3 cam) {
        RenderSystem.setShader(GameRenderer::getPositionShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        Matrix4f m = pose.last().pose();
        for (Window w : windows) {
            for (AABB box : w.shape().toAabbs()) {
                cube(bb, m, box.move(w.pos()).move(-cam.x, -cam.y, -cam.z));
            }
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void cube(BufferBuilder bb, Matrix4f m, AABB b) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(bb, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(bb, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(bb, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(bb, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(bb, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(bb, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        bb.addVertex(m, ax, ay, az);
        bb.addVertex(m, bx, by, bz);
        bb.addVertex(m, cx, cy, cz);
        bb.addVertex(m, dx, dy, dz);
    }

    /** Full-screen dark violet void with a soft vignette and a slow breathing glow, in normalized device coordinates. */
    private static void drawVoid(float time) {
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.05F);
        float[] inner = {0.055F + 0.015F * pulse, 0.035F + 0.01F * pulse, 0.11F + 0.03F * pulse};
        float[] outer = {0.012F, 0.008F, 0.03F};
        float[] edges = {-1.0F, -0.35F, 0.35F, 1.0F};
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                voidVertex(bb, edges, i, j, inner, outer);
                voidVertex(bb, edges, i + 1, j, inner, outer);
                voidVertex(bb, edges, i + 1, j + 1, inner, outer);
                voidVertex(bb, edges, i, j + 1, inner, outer);
            }
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void voidVertex(BufferBuilder bb, float[] edges, int i, int j, float[] inner, float[] outer) {
        boolean in = i > 0 && i < 3 && j > 0 && j < 3;
        float[] c = in ? inner : outer;
        bb.addVertex(edges[i], edges[j], VOID_Z).setColor(c[0], c[1], c[2], 1.0F);
    }

    private static void reveal(Minecraft mc, ClientLevel level, PoseStack pose, MultiBufferSource.BufferSource buffers,
                               Vec3 cam, Frustum frustum, float partial) {
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();
        List<BlockPos> outlined = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (BlockPos pos : DivineXray.targets()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.is(ModTags.Blocks.DIVINE_SIGHT_TARGETS) || !frustum.isVisible(new AABB(pos))) {
                continue;
            }
            pose.pushPose();
            pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            if (state.getRenderShape() == RenderShape.MODEL) {
                BakedModel model = blocks.getBlockModel(state);
                ModelData data = model.getModelData(level, pos, state, level.getModelData(pos));
                int tint = mc.getBlockColors().getColor(state, level, pos, 0);
                float r = ((tint >> 16) & 0xFF) / 255.0F;
                float g = ((tint >> 8) & 0xFF) / 255.0F;
                float b = (tint & 0xFF) / 255.0F;
                // cutout sheet for every layer: it renders into the main target (stencil-clipped) under every mode
                VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
                random.setSeed(42L);
                for (RenderType layer : model.getRenderTypes(state, random, data)) {
                    blocks.getModelRenderer().renderModel(pose.last(), consumer, state, model, r, g, b,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, data, layer);
                }
            }
            if (state.hasBlockEntity()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
                    if (renderer != null) {
                        renderer.render(be, partial, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    }
                }
            }
            pose.popPose();
            outlined.add(pos);
            colors.add(DivineSight.colorFor(state));
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        List<LivingEntity> shown = new ArrayList<>();
        dispatcher.setRenderShadow(false);
        try {
            for (LivingEntity e : DivineXray.entities()) {
                if (e.isRemoved() || e.level() != level || !frustum.isVisible(e.getBoundingBoxForCulling())) {
                    continue;
                }
                double x = Mth.lerp(partial, e.xOld, e.getX()) - cam.x;
                double y = Mth.lerp(partial, e.yOld, e.getY()) - cam.y;
                double z = Mth.lerp(partial, e.zOld, e.getZ()) - cam.z;
                float yRot = Mth.lerp(partial, e.yRotO, e.getYRot());
                dispatcher.render(e, x, y, z, yRot, partial, pose, buffers, LightTexture.FULL_BRIGHT);
                shown.add(e);
            }
        } finally {
            dispatcher.setRenderShadow(true);
        }
        buffers.endBatch();

        // faint category outlines around the blocks, solid colour boxes around creatures
        VertexConsumer lines = buffers.getBuffer(XRAY_LINES);
        for (int i = 0; i < outlined.size(); i++) {
            int rgb = colors.get(i);
            AABB box = new AABB(outlined.get(i)).move(-cam.x, -cam.y, -cam.z).inflate(0.004);
            LevelRenderer.renderLineBox(pose, lines, box, red(rgb), green(rgb), blue(rgb), 0.4F);
        }
        for (LivingEntity e : shown) {
            int rgb = e instanceof Enemy ? DivineSight.HOSTILE_COLOR : DivineSight.PASSIVE_COLOR;
            Vec3 lerp = e.getPosition(partial).subtract(e.position());
            AABB box = e.getBoundingBox().move(lerp).move(-cam.x, -cam.y, -cam.z).inflate(0.02);
            LevelRenderer.renderLineBox(pose, lines, box, red(rgb), green(rgb), blue(rgb), 0.9F);
        }
        buffers.endBatch(XRAY_LINES);
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }
}
