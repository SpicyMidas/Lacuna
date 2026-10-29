package com.lacuna.mod.client.chart;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.chart.ChartGrid;
import com.lacuna.mod.chart.ChartNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the chart in the 128 x 128 map plane set up by {@link ChartHandRenderer}: the vanilla parchment, the terrain
 * (sliding under the holder by fractions of a pixel), the markers, the holder's arrow in the middle and a caption with
 * the coordinates. Markers that lie outside the view are pinned, smaller, to its edge in their direction.
 */
final class ChartPainter {
    private static final RenderType BACKGROUND = RenderType.text(ResourceLocation.withDefaultNamespace("textures/map/map_background.png"));
    private static final ResourceLocation DECORATIONS = ResourceLocation.withDefaultNamespace("textures/atlas/map_decorations.png");
    private static final ResourceLocation PLAYER = ResourceLocation.withDefaultNamespace("player");
    /** Marker sprites by {@link ChartNet.Marker} kind (lacuna's textures/map/decorations, stitched into the vanilla atlas). */
    private static final ResourceLocation[] MARKERS = {Lacuna.id("chart_spawn"), Lacuna.id("chart_home"), Lacuna.id("chart_banished_from")};
    private static final float VIEW = ChartGrid.VIEW;
    private static final float EDGE = 3.0F;
    private static final float TEXT_SCALE = 0.5F;

    private ChartPainter() {}

    static void paint(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player, float partialTick) {
        Matrix4f matrix = pose.last().pose();
        quad(buffers.getBuffer(BACKGROUND), matrix, -7.0F, -7.0F, VIEW + 7.0F, VIEW + 7.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, light);
        ChartNet.State state = ChartClient.state();
        if (state == null || !state.dimension().equals(player.level().dimension())) {
            caption(pose, buffers, light, List.of(Component.translatable("lacuna.chart.waiting")));
            return;
        }
        int bpp = ChartGrid.blocksPerPixel(state.zoom());
        Vec3 at = player.getPosition(partialTick);
        double centreX = state.follows() ? at.x * state.scale() : state.anchor().getX() + 0.5;
        double centreZ = state.follows() ? at.z * state.scale() : state.anchor().getZ() + 0.5;
        // world pixel at the chart's top-left corner, and the texture window that covers it
        double left = centreX / bpp - VIEW / 2.0;
        double top = centreZ / bpp - VIEW / 2.0;
        int ox = Mth.floor(left) - 1;
        int oz = Mth.floor(top) - 1;
        RenderType terrain = ChartTexture.prepare(state.zoom(), ox, oz);
        float u0 = (float) ((left - ox) / ChartTexture.SIZE);
        float v0 = (float) ((top - oz) / ChartTexture.SIZE);
        float span = VIEW / ChartTexture.SIZE;
        quad(buffers.getBuffer(terrain), matrix, 0.0F, 0.0F, VIEW, VIEW, -0.01F, u0, v0, u0 + span, v0 + span, light);

        if (Minecraft.getInstance().getTextureManager().getTexture(DECORATIONS) instanceof TextureAtlas atlas) {
            int layer = 0;
            for (ChartNet.Marker marker : state.markers()) {
                if (marker.kind() < 0 || marker.kind() >= MARKERS.length) {
                    continue;
                }
                float x = (float) ((marker.x() + 0.5) / bpp - left);
                float z = (float) ((marker.z() + 0.5) / bpp - top);
                boolean inside = x >= EDGE && x <= VIEW - EDGE && z >= EDGE && z <= VIEW - EDGE;
                sprite(pose, buffers, light, atlas.getSprite(MARKERS[marker.kind()]), Mth.clamp(x, EDGE, VIEW - EDGE),
                        Mth.clamp(z, EDGE, VIEW - EDGE), 180.0F, inside ? 1.0F : 0.6F, layer++);
            }
            if (state.follows()) {
                sprite(pose, buffers, light, atlas.getSprite(PLAYER), VIEW / 2.0F, VIEW / 2.0F, player.getViewYRot(partialTick), 1.0F, layer);
            }
        }
        caption(pose, buffers, light, lines(state, at));
        label(pose, buffers, light, "1:" + bpp);
    }

    private static List<Component> lines(ChartNet.State state, Vec3 at) {
        List<Component> lines = new ArrayList<>(2);
        if (!state.follows()) {
            lines.add(Component.translatable("lacuna.chart.home", state.anchor().getX(), state.anchor().getZ()));
            lines.add(Component.translatable("lacuna.chart.no_bearing"));
            return lines;
        }
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        if (state.dimension().equals(Level.OVERWORLD)) {
            lines.add(Component.translatable("lacuna.chart.position", x, z));
            return lines;
        }
        // like the banishment's way home: the block here, times the scale
        lines.add(Component.translatable("lacuna.chart.overworld", translate(x, state.scale()), translate(z, state.scale())));
        String scale = state.scale() == Math.rint(state.scale()) ? Integer.toString((int) state.scale()) : String.format("%.2f", state.scale());
        lines.add(Component.translatable("lacuna.chart.here", x, z, scale));
        return lines;
    }

    private static int translate(int block, double scale) {
        return (int) Mth.clamp(Math.floor(block * scale), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** Text lines along the bottom edge, vanilla map label style (white on a dark translucent band). */
    private static void caption(PoseStack pose, MultiBufferSource buffers, int light, List<Component> lines) {
        Font font = Minecraft.getInstance().font;
        float lineHeight = (font.lineHeight + 1) * TEXT_SCALE;
        float y = VIEW - 2.0F - lines.size() * lineHeight;
        for (Component line : lines) {
            text(pose, buffers, light, font, line, 2.0F, y);
            y += lineHeight;
        }
    }

    /** The scale in the top right corner. */
    private static void label(PoseStack pose, MultiBufferSource buffers, int light, String scale) {
        Font font = Minecraft.getInstance().font;
        Component text = Component.literal(scale);
        text(pose, buffers, light, font, text, VIEW - 2.0F - font.width(text) * TEXT_SCALE, 2.0F);
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, int light, Font font, Component text, float x, float y) {
        pose.pushPose();
        pose.translate(x, y, -0.025F);
        pose.scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        pose.translate(0.0F, 0.0F, -0.1F);
        font.drawInBatch(text, 0.0F, 0.0F, -1, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, Integer.MIN_VALUE, light);
        pose.popPose();
    }

    /** A map decoration sprite drawn the way vanilla's MapRenderer draws one ({@code rotation} 180 is upright). */
    private static void sprite(PoseStack pose, MultiBufferSource buffers, int light, TextureAtlasSprite sprite, float x, float z,
                               float rotation, float size, int layer) {
        pose.pushPose();
        pose.translate(x, z, -0.02F);
        pose.mulPose(Axis.ZP.rotationDegrees(rotation));
        pose.scale(4.0F * size, 4.0F * size, 3.0F);
        pose.translate(-0.125F, 0.125F, 0.0F);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(sprite.atlasLocation()));
        float depth = layer * -0.001F;
        consumer.addVertex(matrix, -1.0F, 1.0F, depth).setColor(-1).setUv(sprite.getU0(), sprite.getV0()).setLight(light);
        consumer.addVertex(matrix, 1.0F, 1.0F, depth).setColor(-1).setUv(sprite.getU1(), sprite.getV0()).setLight(light);
        consumer.addVertex(matrix, 1.0F, -1.0F, depth).setColor(-1).setUv(sprite.getU1(), sprite.getV1()).setLight(light);
        consumer.addVertex(matrix, -1.0F, -1.0F, depth).setColor(-1).setUv(sprite.getU0(), sprite.getV1()).setLight(light);
        pose.popPose();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z,
                             float u0, float v0, float u1, float v1, int light) {
        consumer.addVertex(matrix, x0, y1, z).setColor(-1).setUv(u0, v1).setLight(light);
        consumer.addVertex(matrix, x1, y1, z).setColor(-1).setUv(u1, v1).setLight(light);
        consumer.addVertex(matrix, x1, y0, z).setColor(-1).setUv(u1, v0).setLight(light);
        consumer.addVertex(matrix, x0, y0, z).setColor(-1).setUv(u0, v0).setLight(light);
    }
}
