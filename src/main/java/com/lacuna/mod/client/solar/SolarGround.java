package com.lacuna.mod.client.solar;

import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.GroundDecal;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The ground marks of Sun's Fury (the focus reticle, its pull-in rings and light pools, the strike's molten pool and
 * shockwaves) lying on the terrain instead of one flat quad: {@code solar_ring} marks drawn through a
 * {@link GroundDecal}, one cached height grid per spear / column entity. Kept apart from the ray drawing in
 * {@link SolarVisuals}.
 */
final class SolarGround {
    private SolarGround() {}

    /** The terrain grid of {@code entity}'s marks, centred at world {@code ground} (its y: the ground level). */
    static GroundDecal at(FxRenderContext ctx, Entity entity, Vec3 ground, double reach) {
        return GroundDecal.shared(entity).at(ctx.level(), ground, reach);
    }

    /**
     * A {@code solar_ring} mark ({@link SolarRenderTypes#RETICLE}, {@code POOL}, {@code THIN}, {@code SHOCKWAVE}: u band
     * {@code mode * 2 .. mode * 2 + 1}) of half-size {@code half} centred at world {@code c}, {@code lift} above the
     * terrain.
     */
    static void mark(FxRenderContext ctx, GroundDecal g, VertexConsumer vc, Vec3 c, float half, int mode, float lift, int argb) {
        float u0 = mode * 2.0F;
        g.square(ctx, vc, c.x, c.z, half, u0, 0.0F, u0 + 1.0F, 1.0F, lift, argb);
    }
}
