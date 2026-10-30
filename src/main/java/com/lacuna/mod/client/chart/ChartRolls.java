package com.lacuna.mod.client.chart;

import com.lacuna.mod.Lacuna;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The held Wayfarer's Chart's two rolls, as on its item icon: the parchment rolled up along the top and bottom edges of
 * the sheet, around a rod with a wooden knob at each end. Drawn in the 128 x 128 map plane set up by
 * {@link ChartHandRenderer}, just outside the map area (y -12..0 and 128..140): they hide the worn top and bottom edges
 * of the vanilla parchment, whose worn left and right edges stay. Shaded per vertex (light from the top left, in front)
 * over a small texture (tools/art/m21_structure_maps.py): rolled paper, the roll's end and the knobs' wood.
 */
final class ChartRolls {
    private static final ResourceLocation TEXTURE = Lacuna.id("textures/map/wayfarers_chart_roll.png");
    /** The map's own text shader (texture x colour x lightmap), opaque, both faces: the rolls are closed solids. */
    private static final RenderType TYPE = RenderType.create("lacuna_chart_roll", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS, 1536, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TEXT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** The parchment quad's edges (ChartPainter draws it from -7 to 135). */
    private static final float LEFT = -7.0F;
    private static final float RIGHT = 135.0F;
    private static final float RADIUS = 6.0F;
    private static final float TOP_ROLL = -6.0F;
    private static final float BOTTOM_ROLL = 134.0F;
    /** The knob's lathe profile outward from the roll's end: {distance, radius} (a rod, then a rounded knob). */
    private static final float[][] KNOB = {{0.0F, 1.8F}, {1.6F, 1.8F}, {1.6F, 2.6F}, {2.4F, 3.3F}, {5.6F, 3.3F}, {6.4F, 2.6F},
            {7.0F, 2.0F}};
    private static final int SIDES = 16;
    private static final int ROWS = 8;
    /** Texture regions (u0, v0, u1, v1): rolled paper, the roll's end, wood. */
    private static final float[] PAPER = {0.0F, 0.0F, 1.0F, 0.5F};
    private static final float[] END = {0.0F, 0.5F, 0.5F, 1.0F};
    private static final float[] WOOD = {0.5F, 0.5F, 1.0F, 1.0F};
    /** Towards the light, in the map plane (x right, y down, -z towards the viewer). */
    private static final float LX = -0.30F;
    private static final float LY = -0.60F;
    private static final float LZ = -0.74F;

    private ChartRolls() {}

    static void render(PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(TYPE);
        Matrix4f m = pose.last().pose();
        roll(vc, m, TOP_ROLL, light);
        roll(vc, m, BOTTOM_ROLL, light);
    }

    private static void roll(VertexConsumer vc, Matrix4f m, float y, int light) {
        // the paper, one texel per map pixel along it
        for (float x = LEFT; x < RIGHT; x += 16.0F) {
            float x1 = Math.min(RIGHT, x + 16.0F);
            float u1 = PAPER[0] + (x1 - x) / 16.0F * (PAPER[2] - PAPER[0]);
            tube(vc, m, x, x1, y, RADIUS, RADIUS, PAPER[0], u1, PAPER[1], PAPER[3], light);
        }
        disc(vc, m, LEFT, y, RADIUS, 0.0F, -1.0F, END, light);
        disc(vc, m, RIGHT, y, RADIUS, 0.0F, 1.0F, END, light);
        knob(vc, m, LEFT, -1.0F, y, light);
        knob(vc, m, RIGHT, 1.0F, y, light);
    }

    /** A lathe along the profile: a tube, or a step (cone) between two radii, for each segment; a cap at the tip. */
    private static void knob(VertexConsumer vc, Matrix4f m, float end, float dir, float y, int light) {
        for (int i = 0; i + 1 < KNOB.length; i++) {
            float x0 = end + dir * KNOB[i][0];
            float x1 = end + dir * KNOB[i + 1][0];
            float u0 = WOOD[0] + KNOB[i][0] / 7.0F * (WOOD[2] - WOOD[0]);
            float u1 = WOOD[0] + KNOB[i + 1][0] / 7.0F * (WOOD[2] - WOOD[0]);
            if (x0 == x1) {
                // a shoulder: it faces back towards the roll where the knob widens
                float r0 = KNOB[i][1];
                float r1 = KNOB[i + 1][1];
                disc(vc, m, x0, y, Math.max(r0, r1), Math.min(r0, r1), r1 > r0 ? -dir : dir, WOOD, light);
            } else {
                tube(vc, m, x0, x1, y, KNOB[i][1], KNOB[i + 1][1], u0, u1, WOOD[1], WOOD[3], light);
            }
        }
        float[] tip = KNOB[KNOB.length - 1];
        disc(vc, m, end + dir * tip[0], y, tip[1], 0.0F, dir, WOOD, light);
    }

    /** The side of a (truncated) cone along x from {@code x0} (radius {@code r0}) to {@code x1} ({@code r1}). */
    private static void tube(VertexConsumer vc, Matrix4f m, float x0, float x1, float y, float r0, float r1, float u0, float u1,
                             float vMin, float vMax, int light) {
        float row = (vMax - vMin) / ROWS;
        for (int i = 0; i < SIDES; i++) {
            float a0 = i * Mth.TWO_PI / SIDES;
            float a1 = (i + 1) * Mth.TWO_PI / SIDES;
            float v0 = vMin + (i % ROWS) * row;
            float s0 = Mth.sin(a0);
            float c0 = Mth.cos(a0);
            float s1 = Mth.sin(a1);
            float c1 = Mth.cos(a1);
            int b0 = shade(0.0F, s0, -c0);
            int b1 = shade(0.0F, s1, -c1);
            vertex(vc, m, x0, y + r0 * s0, -r0 * c0, b0, u0, v0, light);
            vertex(vc, m, x1, y + r1 * s0, -r1 * c0, b0, u1, v0, light);
            vertex(vc, m, x1, y + r1 * s1, -r1 * c1, b1, u1, v0 + row, light);
            vertex(vc, m, x0, y + r0 * s1, -r0 * c1, b1, u0, v0 + row, light);
        }
    }

    /**
     * A flat ring at {@code x} facing {@code dir} (+-x), from radius {@code inner} (0: a full disc) to {@code outer}; the
     * texture region is mapped as a disc around its centre.
     */
    private static void disc(VertexConsumer vc, Matrix4f m, float x, float y, float outer, float inner, float dir, float[] region,
                             int light) {
        int b = shade(dir, 0.0F, 0.0F);
        float cu = (region[0] + region[2]) / 2.0F;
        float cv = (region[1] + region[3]) / 2.0F;
        float span = (region[2] - region[0]) / 2.0F * 0.98F;
        float k = inner / outer;
        for (int i = 0; i < SIDES; i++) {
            float a0 = i * Mth.TWO_PI / SIDES;
            float a1 = (i + 1) * Mth.TWO_PI / SIDES;
            float s0 = Mth.sin(a0);
            float c0 = Mth.cos(a0);
            float s1 = Mth.sin(a1);
            float c1 = Mth.cos(a1);
            vertex(vc, m, x, y + inner * s0, -inner * c0, b, cu + span * k * s0, cv + span * k * c0, light);
            vertex(vc, m, x, y + outer * s0, -outer * c0, b, cu + span * s0, cv + span * c0, light);
            vertex(vc, m, x, y + outer * s1, -outer * c1, b, cu + span * s1, cv + span * c1, light);
            vertex(vc, m, x, y + inner * s1, -inner * c1, b, cu + span * k * s1, cv + span * k * c1, light);
        }
    }

    /** Grey level 140..255 for a surface normal: half ambient, half diffuse. */
    private static int shade(float nx, float ny, float nz) {
        float len = Mth.sqrt(LX * LX + LY * LY + LZ * LZ);
        float diffuse = Math.max(0.0F, (nx * LX + ny * LY + nz * LZ) / len);
        return (int) (255.0F * (0.55F + 0.45F * diffuse));
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int grey, float u, float v, int light) {
        vc.addVertex(m, x, y, z).setColor(grey, grey, grey, 255).setUv(u, v).setLight(light);
    }
}
