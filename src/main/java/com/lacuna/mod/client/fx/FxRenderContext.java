package com.lacuna.mod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/** Everything an effect needs to draw one frame. Positions passed to {@link FxDraw} are relative to {@code cam}. */
public record FxRenderContext(PoseStack pose, Camera camera, Vec3 cam, float partialTick, MultiBufferSource.BufferSource buffers,
                              ClientLevel level) {
    /** World position → camera-relative vector. */
    public Vec3 rel(Vec3 world) {
        return world.subtract(cam);
    }

    /** Ticks with the partial tick, for smooth animation. */
    public float time(int age) {
        return age + partialTick;
    }
}
