package com.lacuna.mod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * Pipeline check effect (kind {@code debug}): a volumetric beam from {@code a} to {@code b} ({@link FxTubes}), a flare at
 * {@code b} and a
 * ground ring, for 100 ticks. Only spawned by the development ClientDemo script ({@code fx debug ...}).
 */
public final class DebugFx implements ClientFx {
    private final Vec3 a;
    private final Vec3 b;
    private final GroundDecal decal = new GroundDecal();
    private int age;

    private DebugFx(Vec3 a, Vec3 b) {
        this.a = a;
        this.b = b;
    }

    public static void register() {
        FxHandlers.register("debug", p -> FxManager.add(new DebugFx(p.a(), p.b())));
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
        // a volumetric beam (FxTubes), or the flat ribbons when it cannot be drawn in 3D
        FxTubes.beamOrRibbon(pose, ctx.buffers(), FxRenderTypes.beam(), ra, rb, 1.2F, 1.2F, 0xFFFFC040, 0xFFFFC040, 0.0F, len, 0);
        FxTubes.beamOrRibbon(pose, ctx.buffers(), FxRenderTypes.beam(), ra, rb, 3.0F, 3.0F, 0x80FF9020, 0x80FF9020, 0.0F, len, 0);
        VertexConsumer glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
        FxDraw.billboard(pose, glow, ctx.camera(), rb, 2.5F, 0.0F, 0xFFFFD27A);
        VertexConsumer ring = ctx.buffers().getBuffer(FxRenderTypes.additive(FxRenderTypes.RING));
        decal.at(ctx.level(), b, 7.0).square(ctx, ring, b.x, b.z, 3.0F + (age % 20) * 0.2F, 0.0F, 0xC0FFE0A0);
    }
}
