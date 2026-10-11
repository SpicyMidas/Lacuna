package com.lacuna.mod.client.render;

import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Generic X-ray outline renderer shared by Divine Sight (M1) and the Sunken Bell reveal (M10).
 * Boxes are drawn with {@link LacunaRenderTypes#LINES_NO_DEPTH} so they show through walls.
 */
public final class OutlineRenderer {
    public static final String FEATURE = "outline_renderer";
    private static final Map<String, OutlineSet> SETS = new ConcurrentHashMap<>();

    private OutlineRenderer() {}

    public static void put(String key, OutlineSet set) {
        SETS.put(key, set);
    }

    public static void clear(String key) {
        SETS.remove(key);
    }

    public static void clearAll() {
        SETS.clear();
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SETS.isEmpty()) {
            return;
        }
        SafeRun.run(FEATURE, () -> render(event), OutlineRenderer::clearAll);
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        if (pose == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(LacunaRenderTypes.LINES_NO_DEPTH);
        Iterator<Map.Entry<String, OutlineSet>> it = SETS.entrySet().iterator();
        while (it.hasNext()) {
            OutlineSet set = it.next().getValue();
            if (set.expired(now)) {
                it.remove();
                continue;
            }
            for (OutlineSet.Box box : set.boxes()) {
                draw(pose, lines, box.box().move(-cam.x, -cam.y, -cam.z), box.color());
            }
            for (OutlineSet.TrackedEntity tracked : set.entities()) {
                Entity e = tracked.entity();
                if (e.isRemoved() || e.level() != mc.level) {
                    continue;
                }
                Vec3 lerp = e.getPosition(partial).subtract(e.position());
                AABB box = e.getBoundingBox().move(lerp).move(-cam.x, -cam.y, -cam.z);
                draw(pose, lines, box, tracked.color());
            }
        }
        buffers.endBatch(LacunaRenderTypes.LINES_NO_DEPTH);
    }

    private static void draw(PoseStack pose, VertexConsumer lines, AABB box, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        LevelRenderer.renderLineBox(pose, lines, box.inflate(0.002), r, g, b, 1.0F);
    }
}
