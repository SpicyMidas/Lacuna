package com.lacuna.mod.client.chart;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.chart.ChartGrid;
import com.lacuna.mod.chart.ChartNet;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * The chart's pixels as one dynamic texture: texel {@code (u, v)} is world pixel {@code (originX + u, originZ + v)}.
 * It is one texel wider than the view on each side, so the painter can slide the visible window by a fraction of a
 * pixel and the chart scrolls smoothly under its centred holder; it is recomposed only when that window crosses a
 * pixel, the zoom changes or a tile inside it arrives.
 * <p>
 * Sketched (not surveyed) land is drawn faded toward the parchment, with a faint paper grain: like ink laid on lightly
 * from hearsay, next to the full colours of land seen lately.
 */
final class ChartTexture {
    static final int SIZE = ChartGrid.VIEW + 2;
    private static final ResourceLocation ID = Lacuna.id("chart/live");
    /** The vanilla map parchment (#D6BE96) in NativeImage ABGR order. */
    private static final int PARCHMENT = 0xFF96BED6;

    @Nullable
    private static DynamicTexture texture;
    @Nullable
    private static RenderType renderType;
    private static int originX;
    private static int originZ;
    private static int zoom = -1;
    private static boolean dirty = true;

    private ChartTexture() {}

    static void invalidate() {
        dirty = true;
    }

    static void tileArrived(ChartGrid.TileKey key) {
        int x0 = key.tx() << ChartGrid.TILE_SHIFT;
        int z0 = key.tz() << ChartGrid.TILE_SHIFT;
        if (key.zoom() == zoom && x0 < originX + SIZE && x0 + ChartGrid.TILE > originX && z0 < originZ + SIZE
                && z0 + ChartGrid.TILE > originZ) {
            dirty = true;
        }
    }

    /** The render type of the texture showing world pixels from {@code (ox, oz)} at {@code zoom}, recomposed if needed. */
    static RenderType prepare(int newZoom, int ox, int oz) {
        if (texture == null || renderType == null) {
            texture = new DynamicTexture(SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
            renderType = RenderType.text(ID);
            dirty = true;
        }
        if (dirty || newZoom != zoom || ox != originX || oz != originZ) {
            zoom = newZoom;
            originX = ox;
            originZ = oz;
            compose(texture.getPixels());
            texture.upload();
            dirty = false;
        }
        return renderType;
    }

    private static void compose(@Nullable NativeImage image) {
        if (image == null) {
            return;
        }
        for (int v = 0; v < SIZE; v++) {
            int pz = originZ + v;
            int row = pz & (ChartGrid.TILE - 1);
            int u = 0;
            while (u < SIZE) {
                int px = originX + u;
                int col = px & (ChartGrid.TILE - 1);
                int run = Math.min(ChartGrid.TILE - col, SIZE - u);
                ChartNet.Tile tile = ChartClient.tile(new ChartGrid.TileKey(zoom, ChartGrid.tileOf(px), ChartGrid.tileOf(pz)));
                for (int k = 0; k < run; k++) {
                    image.setPixelRGBA(u + k, v, tile == null ? 0 : color(tile, row * ChartGrid.TILE + col + k, px + k + pz));
                }
                u += run;
            }
        }
    }

    private static int color(ChartNet.Tile tile, int i, int parity) {
        int packed = tile.colors()[i] & 0xFF;
        if (packed == 0) {
            return 0;
        }
        int abgr = MapColor.getColorFromPackedId(packed);
        boolean sketched = (tile.rumour()[i >> 3] & (1 << (i & 7))) != 0;
        return sketched ? fade(abgr, (parity & 1) == 0 ? 0.42F : 0.5F) : abgr;
    }

    /** Mixes an opaque ABGR colour toward the parchment by {@code t}. */
    private static int fade(int abgr, float t) {
        int r = mix(abgr & 0xFF, PARCHMENT & 0xFF, t);
        int g = mix(abgr >> 8 & 0xFF, PARCHMENT >> 8 & 0xFF, t);
        int b = mix(abgr >> 16 & 0xFF, PARCHMENT >> 16 & 0xFF, t);
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    private static int mix(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
