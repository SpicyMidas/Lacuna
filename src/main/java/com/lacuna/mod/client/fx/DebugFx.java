package com.lacuna.mod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.lacuna.mod.network.FxPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Pipeline check effect (kind {@code debug}): a shader beam from {@code a} to {@code b}, a flare at {@code b} and a
 * ground ring, for 100 ticks. Only spawned by the development ClientDemo script ({@code fx debug ...}).
 */
public final class DebugFx implements ClientFx {
    private final Vec3 a;
    private final Vec3 b;
    private int age;

    private DebugFx(Vec3 a, Vec3 b) {
        this.a = a;
        this.b = b;
    }

    public static void register() {
        FxHandlers.register("debug", p -> FxManager.add(new DebugFx(p.a(), p.b())));
    }

    public static FxPayload payload(Vec3 a, Vec3 b) {
        return FxPayload.line("debug", a, b);
    }

    @Override
    public boolean tick() {
        return ++age < 100;
    }

    @Override
    public void render(FxRenderContext ctx) {
        PoseStack.Pose pose = ctx.pose().last();
        Vec3 ra = ctx.rel(a);
        Vec3 rb = ctx.rel(b);
        float len = (float) a.distanceTo(b);
        VertexConsumer beam = ctx.buffers().getBuffer(FxRenderTypes.beam());
        FxDraw.ribbon(pose, beam, ra, rb, 1.2F, 0xFFFFC040, 0.0F, len);
        FxDraw.ribbon(pose, beam, ra, rb, 3.0F, 0x80FF9020, 0.0F, len);
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), rb, 2.5F, 0.0F, 0xFFFFD27A);
        VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
        FxDraw.ground(pose, ring, rb.add(0, 0.05, 0), 3.0F + (age % 20) * 0.2F, 0xC0FFE0A0);
    }
}
