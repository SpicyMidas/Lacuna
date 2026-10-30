package com.lacuna.mod.client.chart;

import com.lacuna.mod.Lacuna;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The held Wayfarer's Chart's two rolls, flat as on its item icon: a strip of the rolled parchment, with its wooden
 * knobs past the sheet's sides, along the top and bottom edges. Drawn in the 128 x 128 map plane set up by
 * {@link ChartHandRenderer}, just outside the map area, over the vanilla parchment's worn top and bottom edges (its
 * worn left and right edges stay). Texture: tools/art/m21_structure_maps.py (64 x 6 texels).
 */
final class ChartRolls {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/map/wayfarers_chart_roll.png");
    /** The strip runs past the parchment quad (-7..135) by the knobs, at about the parchment's own texel size. */
    private static final float LEFT = -13.0F;
    private static final float RIGHT = 141.0F;
    private static final float HEIGHT = (RIGHT - LEFT) * 6.0F / 64.0F;
    /** In front of the parchment (z 0) and the map (z -0.01): -z is towards the viewer. */
    private static final float Z = -0.02F;

    private ChartRolls() {}

    static void render(PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.text(TEXTURE));
        Matrix4f m = pose.last().pose();
        strip(vc, m, -HEIGHT, 0.0F, light);
        strip(vc, m, 128.0F, 128.0F + HEIGHT, light);
    }

    private static void strip(VertexConsumer vc, Matrix4f m, float top, float bottom, int light) {
        vc.addVertex(m, LEFT, bottom, Z).setColor(255, 255, 255, 255).setUv(0.0F, 1.0F).setLight(light);
        vc.addVertex(m, RIGHT, bottom, Z).setColor(255, 255, 255, 255).setUv(1.0F, 1.0F).setLight(light);
        vc.addVertex(m, RIGHT, top, Z).setColor(255, 255, 255, 255).setUv(1.0F, 0.0F).setLight(light);
        vc.addVertex(m, LEFT, top, Z).setColor(255, 255, 255, 255).setUv(0.0F, 0.0F).setLight(light);
    }
}
