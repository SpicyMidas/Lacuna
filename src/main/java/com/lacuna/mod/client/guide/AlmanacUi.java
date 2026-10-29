package com.lacuna.mod.client.guide;

import com.lacuna.mod.Lacuna;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The Almanac's GUI sheet (textures/gui/almanac.png, painted by tools/art/m20_almanac.py) and how its pieces are drawn.
 * Every stretchable piece is a 9-slice whose edges and centre are tiled, never stretched, so the leather grain and the
 * parchment stay pixel-crisp at any book size. The regions below mirror the art module.
 */
final class AlmanacUi {
    static final ResourceLocation SHEET = Lacuna.id("textures/gui/almanac.png");

    static final int INK = 0xFF3B2A1A;
    static final int INK_SOFT = 0xFF6B5A44;
    static final int INK_DIM = 0xFF9A8A70;
    static final int RUBY = 0xFF8E0F2A;
    static final int RUBY_SOFT = 0xFFA8505E;
    static final int GOLD = 0xFF8A6418;
    static final int RULE = 0x553B2A1A;

    /** Glyphs (9 x 9) in sheet order. */
    static final int GLYPH_WHERE = 0, GLYPH_CONTENTS = 1, GLYPH_BULLET = 2, GLYPH_STAR = 3;

    static final int ARROW_W = 18;
    static final int ARROW_H = 10;
    static final int ORNAMENT_W = 64;
    static final int ORNAMENT_H = 7;

    private AlmanacUi() {}

    /** The leather cover with its ruby inlay and gold corner caps, around the whole book. */
    static void cover(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, x, y, w, h, 0, 0, 64, 64, 12);
    }

    static void leftPage(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, x, y, w, h, 64, 0, 48, 48, 6);
    }

    static void rightPage(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, x, y, w, h, 112, 0, 48, 48, 6);
    }

    /** The fold between the pages: a 16 px strip centred on the seam, repeated down its height. */
    static void spine(GuiGraphics g, int centreX, int y, int h) {
        tile(g, centreX - 8, y, 16, h, 160, 0, 16, 48);
    }

    /** The "where to find it" box: warm parchment, a ruby bar on the left, a thin gold rim. */
    static void whereBox(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, x, y, w, h, 176, 0, 24, 24, 5);
    }

    /** A hovered contents row. */
    static void rowHover(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, x, y, w, h, 176, 24, 24, 12, 3);
    }

    /** A chapter tab sticking out of the book's fore-edge: 0 normal, 1 hovered, 2 the current chapter. */
    static void tab(GuiGraphics g, int x, int y, int w, int h, int state) {
        int u = state == 1 ? 220 : 200;
        int v = state == 2 ? 20 : 0;
        nineSlice(g, x, y, w, h, u, v, 20, 20, 4);
    }

    static void arrow(GuiGraphics g, int x, int y, boolean next, boolean hover) {
        g.blit(SHEET, x, y, next ? ARROW_W : 0, hover ? 74 : 64, ARROW_W, ARROW_H);
    }

    /** The flourish under titles: centred in {@code w}, with thin ink rules running out to both sides. */
    static void ornament(GuiGraphics g, int x, int y, int w) {
        int ox = x + (w - ORNAMENT_W) / 2;
        g.blit(SHEET, ox, y, 64, 48, ORNAMENT_W, ORNAMENT_H);
        int mid = y + ORNAMENT_H / 2;
        if (ox - 2 > x + 4) {
            g.fill(x + 4, mid, ox - 2, mid + 1, RULE);
        }
        if (ox + ORNAMENT_W + 2 < x + w - 4) {
            g.fill(ox + ORNAMENT_W + 2, mid, x + w - 4, mid + 1, RULE);
        }
    }

    static void glyph(GuiGraphics g, int index, int x, int y) {
        g.blit(SHEET, x, y, 128 + index * 10, 48, 9, 9);
    }

    /** 9-slice of the sheet region (u, v, rw, rh) with a uniform border, edges and centre tiled. */
    static void nineSlice(GuiGraphics g, int x, int y, int w, int h, int u, int v, int rw, int rh, int b) {
        int bw = Math.min(b, w / 2);
        int bh = Math.min(b, h / 2);
        int cw = rw - 2 * b;
        int ch = rh - 2 * b;
        g.blit(SHEET, x, y, u, v, bw, bh);
        g.blit(SHEET, x + w - bw, y, u + rw - bw, v, bw, bh);
        g.blit(SHEET, x, y + h - bh, u, v + rh - bh, bw, bh);
        g.blit(SHEET, x + w - bw, y + h - bh, u + rw - bw, v + rh - bh, bw, bh);
        tile(g, x + bw, y, w - 2 * bw, bh, u + b, v, cw, bh);
        tile(g, x + bw, y + h - bh, w - 2 * bw, bh, u + b, v + rh - bh, cw, bh);
        tile(g, x, y + bh, bw, h - 2 * bh, u, v + b, bw, ch);
        tile(g, x + w - bw, y + bh, bw, h - 2 * bh, u + rw - bw, v + b, bw, ch);
        tile(g, x + bw, y + bh, w - 2 * bw, h - 2 * bh, u + b, v + b, cw, ch);
    }

    /** Fills (x, y, w, h) by repeating the sheet region (u, v, tw, th). */
    static void tile(GuiGraphics g, int x, int y, int w, int h, int u, int v, int tw, int th) {
        if (w <= 0 || h <= 0 || tw <= 0 || th <= 0) {
            return;
        }
        for (int dy = 0; dy < h; dy += th) {
            int bh = Math.min(th, h - dy);
            for (int dx = 0; dx < w; dx += tw) {
                int bw = Math.min(tw, w - dx);
                g.blit(SHEET, x + dx, y + dy, u, v, bw, bh);
            }
        }
    }
}
