package com.lacuna.mod.client.mirror;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

/**
 * Entity geometry for the mirror images, recorded once and replayed by every plane. An entity is rendered through its
 * own renderer into this buffer source (armour, held items, leashes, flames and enchantment glint included); each render
 * type it used becomes a small pooled vertex buffer. Each plane's image then replays those buffers under its own
 * reflection matrix, so the cost of rendering an entity does not grow with the number of mirrors showing it. A draw
 * replays with its render type's own state; whatever target that state binds (Fabulous output targets), the image
 * target is bound again before drawing. Outline types are swallowed. Render thread only.
 */
final class MirrorCapture implements MultiBufferSource {
    private static final int INITIAL_BYTES = 16 * 1024;
    private static final int MAX_MEMORY = 32;
    private static final int MAX_BUFFERS = 1024;

    /** One replayable draw: a render type and the geometry recorded for it; late ones go after all the others. */
    record Draw(RenderType type, VertexBuffer buffer, boolean late) {}

    private record Open(BufferBuilder builder, ByteBufferBuilder memory) {}

    private static Set<RenderType> glints;

    private final Map<RenderType, Open> open = new LinkedHashMap<>();
    private final ArrayDeque<ByteBufferBuilder> free = new ArrayDeque<>();
    private final List<ByteBufferBuilder> memory = new ArrayList<>();
    private final List<VertexBuffer> buffers = new ArrayList<>();
    private int used;
    private List<Draw> out;
    private VertexSorting sorting;

    /** A new frame: every pooled vertex buffer can be filled again. */
    void frame() {
        used = 0;
    }

    /**
     * Renders one entity at a camera-relative position and returns its draws in first-use order (possibly none). A
     * renderer that throws leaves nothing behind: the half-built geometry is dropped and the exception passed on.
     */
    List<Draw> record(EntityRenderDispatcher dispatcher, Entity e, double x, double y, double z, float yRot, float partial,
                      int light, VertexSorting sorting) {
        List<Draw> draws = new ArrayList<>(4);
        out = draws;
        this.sorting = sorting;
        try {
            dispatcher.render(e, x, y, z, yRot, partial, new PoseStack(), this, light);
            finishAll();
            return draws;
        } catch (RuntimeException ex) {
            abandon();
            throw ex;
        } finally {
            out = null;
            this.sorting = null;
        }
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (out == null || type.isOutline()) {
            return Discard.INSTANCE;
        }
        Open o = open.get(type);
        if (o != null) {
            if (type.canConsolidateConsecutiveGeometry()) {
                return o.builder();
            }
            // strips: one mesh per request, as vanilla's buffer source does
            open.remove(type);
            finish(type, o);
        }
        ByteBufferBuilder bytes = free.poll();
        if (bytes == null) {
            if (memory.size() >= MAX_MEMORY) {
                return Discard.INSTANCE;
            }
            bytes = new ByteBufferBuilder(INITIAL_BYTES);
            memory.add(bytes);
        }
        BufferBuilder builder = new BufferBuilder(bytes, type.mode(), type.format());
        open.put(type, new Open(builder, bytes));
        return builder;
    }

    private void finishAll() {
        Iterator<Map.Entry<RenderType, Open>> it = open.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<RenderType, Open> entry = it.next();
            it.remove();
            finish(entry.getKey(), entry.getValue());
        }
    }

    private void finish(RenderType type, Open o) {
        MeshData mesh = o.builder().build();
        if (mesh != null) {
            VertexBuffer buffer = nextBuffer();
            if (buffer == null) {
                mesh.close();
            } else {
                if (type.sortOnUpload() && sorting != null) {
                    mesh.sortQuads(o.memory(), sorting);
                }
                buffer.bind();
                buffer.upload(mesh); // closes the mesh, which frees its bytes
                out.add(new Draw(type, buffer, late(type)));
            }
        }
        free.push(o.memory());
    }

    private VertexBuffer nextBuffer() {
        if (used < buffers.size()) {
            return buffers.get(used++);
        }
        if (buffers.size() >= MAX_BUFFERS) {
            return null;
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        buffers.add(buffer);
        used++;
        return buffer;
    }

    /** Translucent layers and enchantment glint (drawn where the base geometry already is) go last. */
    private static boolean late(RenderType type) {
        if (glints == null) {
            glints = Set.of(RenderType.armorEntityGlint(), RenderType.glintTranslucent(), RenderType.glint(),
                    RenderType.entityGlint(), RenderType.entityGlintDirect());
        }
        return type.sortOnUpload() || glints.contains(type);
    }

    /** Drops everything half-built: the byte buffers are freed and made again on demand. */
    private void abandon() {
        open.clear();
        free.clear();
        for (ByteBufferBuilder bytes : memory) {
            bytes.close();
        }
        memory.clear();
    }

    /** Frees the byte buffers and the vertex buffers (render thread). */
    void release() {
        abandon();
        for (VertexBuffer buffer : buffers) {
            buffer.close();
        }
        buffers.clear();
        used = 0;
    }

    /** Draws one recorded draw into {@code into} with the current model-view and projection matrices. */
    static void replay(Draw d, RenderTarget into) {
        d.type().setupRenderState();
        // an output state may have bound another target (Fabulous): the image is where this goes
        into.bindWrite(false);
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            d.buffer().bind();
            d.buffer().drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
        }
        d.type().clearRenderState();
    }

    /** Swallows the geometry of render types that must not reach the image. */
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
