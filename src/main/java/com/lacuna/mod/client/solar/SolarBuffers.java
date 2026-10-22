package com.lacuna.mod.client.solar;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;

/**
 * A buffer source with one fixed buffer per solar render type, so effects can interleave layers freely (a shared
 * buffer would be flushed on every render type switch) and everything is drawn in a fixed order: covering layers
 * first, light added on top ({@link SolarRenderTypes#drawOrder()}). Light that has a late, depth-aware twin
 * ({@link SolarVolume#late}) is routed into that twin's buffer and kept until {@link SolarVolume#drawLate} draws it
 * after the level; {@link #endEarly} draws the rest after particles. Rebuilt when a shader reload changes which
 * render types are in use.
 */
final class SolarBuffers extends MultiBufferSource.BufferSource {
    private static List<RenderType> types = List.of();
    @Nullable
    private static SolarBuffers source;
    private static final List<ByteBufferBuilder> OWNED = new ArrayList<>();

    private SolarBuffers(ByteBufferBuilder shared, LinkedHashMap<RenderType, ByteBufferBuilder> fixed) {
        super(shared, fixed);
    }

    static SolarBuffers get() {
        List<RenderType> now = SolarRenderTypes.drawOrder();
        if (source == null || !now.equals(types)) {
            if (source != null) {
                source.discardAll();
            }
            OWNED.forEach(ByteBufferBuilder::close);
            OWNED.clear();
            LinkedHashMap<RenderType, ByteBufferBuilder> fixed = new LinkedHashMap<>();
            for (RenderType type : now) {
                own(fixed, type);
            }
            for (RenderType type : SolarVolume.drawOrder()) {
                own(fixed, type);
            }
            ByteBufferBuilder shared = new ByteBufferBuilder(4096);
            OWNED.add(shared);
            source = new SolarBuffers(shared, fixed);
            types = now;
        }
        return source;
    }

    private static void own(LinkedHashMap<RenderType, ByteBufferBuilder> fixed, RenderType type) {
        if (!fixed.containsKey(type)) {
            ByteBufferBuilder buffer = new ByteBufferBuilder(Math.max(4096, type.bufferSize()));
            fixed.put(type, buffer);
            OWNED.add(buffer);
        }
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        SolarVolume.Late late = SolarVolume.late(type);
        return late == null ? super.getBuffer(type) : late.wrap(super.getBuffer(late.type()));
    }

    /** After particles: draws everything that has no late twin, in {@link SolarRenderTypes#drawOrder()}. */
    void endEarly() {
        endLastBatch();
        for (RenderType type : types) {
            endBatch(type);
        }
    }

    /** True if any of these render types has quads waiting. */
    boolean started(List<RenderType> list) {
        for (RenderType type : list) {
            if (startedBuilders.containsKey(type)) {
                return true;
            }
        }
        return false;
    }

    /** Throws away the waiting quads of these render types without drawing them. */
    void discard(List<RenderType> list) {
        for (RenderType type : list) {
            BufferBuilder builder = startedBuilders.remove(type);
            if (builder != null) {
                try (MeshData mesh = builder.build()) {
                    // closed unused
                }
            }
        }
    }

    private void discardAll() {
        discard(new ArrayList<>(startedBuilders.keySet()));
        lastSharedType = null;
    }
}
