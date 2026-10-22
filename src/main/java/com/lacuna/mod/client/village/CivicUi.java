package com.lacuna.mod.client.village;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.logic.ReputationMath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The civic GUI sheet (textures/gui/civic.png, painted by tools/art/m18_civic_gui.py) and its 9-slice drawing. Edges
 * and centres are tiled, never stretched, so the parchment and wood grain stay pixel-crisp at any panel size.
 */
final class CivicUi {
    static final ResourceLocation SHEET = Lacuna.id("textures/gui/civic.png");

    static final int INK = 0xFF3B2A1A;
    static final int INK_SOFT = 0xFF6B5A44;
    static final int INK_DIM = 0xFF9A8A70;
    static final int GOOD = 0xFF2E6B1E;
    static final int BAD = 0xFF8A2A1E;
    static final int GOLD = 0xFF8A6418;

    /** Glyphs (9 x 9) in sheet order. */
    static final int CHECK = 0, CROSS = 1, SEAL = 2, CLOCK = 3, SCALES = 4, QUILL = 5, STAR = 6, PEOPLE = 7;

    private CivicUi() {}

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, x, y, w, h, 0, 0, 48, 48, 8);
    }

    static void inset(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, x, y, w, h, 48, 0, 24, 24, 3);
    }

    static void row(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        nineSlice(g, x, y, w, h, hover ? 96 : 72, 0, 24, 24, 3);
    }

    /** The red title ribbon: fixed 10 px swallow-tail ends, tiled middle. */
    static void banner(GuiGraphics g, int x, int y, int w) {
        g.blit(SHEET, x, y, 120, 0, 10, 18);
        tile(g, x + 10, y, w - 20, 18, 130, 0, 44, 18);
        g.blit(SHEET, x + w - 10, y, 174, 0, 10, 18);
    }

    static void tab(GuiGraphics g, int x, int y, boolean selected) {
        if (selected) {
            g.blit(SHEET, x, y, 212, 0, 28, 27);
        } else {
            g.blit(SHEET, x, y + 3, 184, 0, 28, 24);
        }
    }

    static void glyph(GuiGraphics g, int index, int x, int y) {
        g.blit(SHEET, x, y, 128 + index * 10, 48, 9, 9);
    }

    /** Reputation bar (102 x 7) filled from -100 up to {@code rep}, with the brass pin at {@code rep}. */
    static void repBar(GuiGraphics g, int x, int y, int rep) {
        g.blit(SHEET, x, y, 0, 48, 102, 7);
        int fill = 1 + Math.round((ReputationMath.clamp(rep) + 100) / 200.0F * 99);
        g.blit(SHEET, x, y, 0, 56, fill + 1, 7);
        g.blit(SHEET, x + fill - 2, y - 2, 0, 76, 5, 11);
    }

    /** Small brass progress bar (52 x 5). */
    static void bar(GuiGraphics g, int x, int y, float fraction) {
        g.blit(SHEET, x, y, 0, 64, 52, 5);
        int w = Math.round(Math.max(0, Math.min(1, fraction)) * 50);
        if (w > 0) {
            g.blit(SHEET, x + 1, y, 1, 70, w, 5);
        }
    }

    static void scrollbar(GuiGraphics g, int x, int y, int h, float position, float visibleFraction, boolean hover) {
        nineSlice(g, x, y, 8, h, 104, 48, 8, 24, 2);
        int knobH = 15;
        int knobY = y + 1 + Math.round((h - 2 - knobH) * Math.max(0, Math.min(1, position)));
        g.blit(SHEET, x + 1, knobY, hover ? 118 : 112, 48, 6, knobH);
    }

    static int tierColor(int rep) {
        return switch (ReputationMath.tier(rep)) {
            case HOSTILE -> 0xFFA8322A;
            case WARY -> 0xFFB0661E;
            case NEUTRAL -> 0xFF6B6458;
            case FRIENDLY -> 0xFF3E7A2E;
            case HONORED -> 0xFFA07A10;
        };
    }

    /** 9-slice of the sheet region (u, v, rw, rh) with a uniform border, edges and centre tiled. */
    static void nineSlice(GuiGraphics g, int x, int y, int w, int h, int u, int v, int rw, int rh, int b) {
        int cw = rw - 2 * b;
        int ch = rh - 2 * b;
        int iw = w - 2 * b;
        int ih = h - 2 * b;
        g.blit(SHEET, x, y, u, v, b, b);
        g.blit(SHEET, x + w - b, y, u + rw - b, v, b, b);
        g.blit(SHEET, x, y + h - b, u, v + rh - b, b, b);
        g.blit(SHEET, x + w - b, y + h - b, u + rw - b, v + rh - b, b, b);
        tile(g, x + b, y, iw, b, u + b, v, cw, b);
        tile(g, x + b, y + h - b, iw, b, u + b, v + rh - b, cw, b);
        tile(g, x, y + b, b, ih, u, v + b, b, ch);
        tile(g, x + w - b, y + b, b, ih, u + rw - b, v + b, b, ch);
        tile(g, x + b, y + b, iw, ih, u + b, v + b, cw, ch);
    }

    /** Fills (x, y, w, h) by repeating the sheet region (u, v, tw, th). */
    static void tile(GuiGraphics g, int x, int y, int w, int h, int u, int v, int tw, int th) {
        if (w <= 0 || h <= 0) {
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
