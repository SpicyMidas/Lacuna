package com.lacuna.mod.client.solar;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * A buffer source with one fixed buffer per solar render type, so effects can interleave layers freely (a shared
 * buffer would be flushed on every render type switch) and everything is drawn at {@code endBatch()} in a fixed
 * order: covering layers first, light added on top ({@link SolarRenderTypes#drawOrder()}). Rebuilt when a shader
 * reload changes which render types are in use.
 */
final class SolarBuffers {
    private static List<RenderType> types = List.of();
    private static MultiBufferSource.BufferSource source;
    private static final List<ByteBufferBuilder> OWNED = new ArrayList<>();

    private SolarBuffers() {}

    static MultiBufferSource.BufferSource get() {
        List<RenderType> now = SolarRenderTypes.drawOrder();
        if (source == null || !now.equals(types)) {
            if (source != null) {
                source.endBatch();
            }
            OWNED.forEach(ByteBufferBuilder::close);
            OWNED.clear();
            LinkedHashMap<RenderType, ByteBufferBuilder> fixed = new LinkedHashMap<>();
            for (RenderType type : now) {
                if (!fixed.containsKey(type)) {
                    ByteBufferBuilder buffer = new ByteBufferBuilder(Math.max(4096, type.bufferSize()));
                    fixed.put(type, buffer);
                    OWNED.add(buffer);
                }
            }
            ByteBufferBuilder shared = new ByteBufferBuilder(4096);
            OWNED.add(shared);
            source = MultiBufferSource.immediateWithBuffers(fixed, shared);
            types = now;
        }
        return source;
    }
}
