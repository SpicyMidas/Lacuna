package com.lacuna.mod.client.guide;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Lacuna Almanac: an open book, two parchment pages in a dark violet leather cover with a ruby inlay. The left
 * page of the first spread carries the title and the contents (every entry grouped under its chapter, each row
 * clickable); every entry follows on its own pages: icon, title, any extra icons, the text and a highlighted "where to
 * find it" box. Chapter tabs on the fore-edge (the first one is the contents) jump to the chapter's part of the
 * contents. Arrows, the arrow keys, Page Up / Down and the mouse wheel turn pages; Home or Backspace go back to the
 * contents. The book is re-typeset for every window size (GUI scale 1-4, small windows) instead of being scaled.
 */
public final class AlmanacScreen extends Screen {
    private static final int MAX_W = 330;
    private static final int MAX_H = 208;
    private static final int MIN_W = 200;
    private static final int MIN_H = 128;
    /** Leather rim of the cover around the pages. */
    private static final int COVER = 7;
    /** How far a chapter tab sticks out of the fore-edge, and the part hidden under the cover. */
    private static final int TAB_OUT = 18;
    private static final int TAB_BASE = 6;
    /** Page margins: outer edge, spine side, top (running header) and bottom (page number, arrows). */
    private static final int M_OUT = 11;
    private static final int M_IN = 9;
    private static final int M_TOP = 18;
    private static final int M_BOTTOM = 16;

    private final AlmanacBook book;
    private AlmanacLayout layout;
    private int spread;
    private int bx;
    private int by;
    private int bw;
    private int bh;
    private int pw;
    private int ph;
    private int cw;
    private final List<Hot> hot = new ArrayList<>();
    private ItemStack tipItem = ItemStack.EMPTY;
    @Nullable
    private Component tipText;
    private boolean failed;

    /** A clickable or hoverable area of the last frame. */
    private record Hot(int x, int y, int w, int h, @Nullable Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    AlmanacScreen() {
        super(Component.translatable("item.lacuna.almanac"));
        this.book = AlmanacBook.load();
    }

    @Override
    protected void init() {
        bw = Math.max(MIN_W, Math.min(MAX_W, width - 8 - TAB_OUT)) & ~1;
        bh = Math.max(MIN_H, Math.min(MAX_H, height - 8));
        bx = (width - bw - TAB_OUT) / 2;
        by = (height - bh) / 2;
        pw = bw / 2 - COVER;
        ph = bh - 2 * COVER;
        cw = pw - M_OUT - M_IN;
        layout = AlmanacLayout.build(book, font, cw, ph - M_TOP - M_BOTTOM);
        spread = clamp(layout.spreadOf(AlmanacClient.anchor(), AlmanacClient.offset()), 0, layout.spreads() - 1);
    }

    // ------------------------------------------------------------------------------------------ navigation

    private void turnTo(int target) {
        int s = clamp(target, 0, layout.spreads() - 1);
        if (s == spread) {
            return;
        }
        spread = s;
        AlmanacLayout.Page left = page(2 * s);
        if (left != null) {
            AlmanacClient.remember(left.contents ? AlmanacLayout.CONTENTS : left.entry.id(), left.indexInEntry);
        }
        playTurn();
    }

    private void openEntry(AlmanacBook.Entry entry) {
        turnTo(layout.entryPage(entry) / 2);
        AlmanacClient.remember(entry.id(), 0);
    }

    private void openContents(int contentsPage) {
        turnTo(contentsPage / 2);
        AlmanacClient.remember(AlmanacLayout.CONTENTS, contentsPage);
    }

    private void playTurn() {
        if (minecraft != null) {
            float pitch = 0.92F + (float) Math.random() * 0.16F;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch, 0.7F));
        }
    }

    @Nullable
    private AlmanacLayout.Page page(int index) {
        return index >= 0 && index < layout.pages.size() ? layout.pages.get(index) : null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> turnTo(spread - 1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> turnTo(spread + 1);
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_BACKSPACE -> openContents(0);
            case GLFW.GLFW_KEY_END -> turnTo(layout.spreads() - 1);
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY > 0) {
            turnTo(spread - 1);
        } else if (scrollY < 0) {
            turnTo(spread + 1);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hot.size() - 1; i >= 0; i--) {
                Hot h = hot.get(i);
                if (h.action() != null && h.contains(mx, my)) {
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (failed) {
            onClose();
        }
    }

    // ------------------------------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        renderTransparentBackground(g);
        guard(() -> {
            drawTabs(g, mx, my, false);
            AlmanacUi.cover(g, bx, by, bw, bh);
            int top = by + COVER;
            AlmanacUi.leftPage(g, bx + COVER, top, pw, ph);
            AlmanacUi.rightPage(g, bx + bw / 2, top, pw, ph);
            AlmanacUi.spine(g, bx + bw / 2, top, ph);
        });
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        guard(() -> {
            hot.clear();
            tipItem = ItemStack.EMPTY;
            tipText = null;
            drawPage(g, 2 * spread, bx + COVER, true, mx, my);
            drawPage(g, 2 * spread + 1, bx + bw / 2, false, mx, my);
            drawArrows(g, mx, my);
            drawTabs(g, mx, my, true);
            if (!tipItem.isEmpty()) {
                g.renderTooltip(font, tipItem, mx, my);
            } else if (tipText != null) {
                g.renderTooltip(font, tipText, mx, my);
            }
        });
    }

    private void guard(Runnable draw) {
        if (failed) {
            return;
        }
        try {
            draw.run();
        } catch (RuntimeException e) {
            failed = true;
            Lacuna.LOGGER.warn("[Lacuna] the Almanac failed to draw and closes", e);
        }
    }

    /** The chapter tabs: backgrounds under the cover (first pass), icons and hover areas on top (second pass). */
    private void drawTabs(GuiGraphics g, int mx, int my, boolean icons) {
        int n = book.chapters.size() + 1;
        int th = clamp((ph - 4) / n, 12, 20);
        int top = by + COVER + 2;
        AlmanacLayout.Page left = page(2 * spread);
        boolean onContents = left == null || left.contents;
        for (int i = 0; i < n; i++) {
            AlmanacBook.Chapter chapter = i == 0 ? null : book.chapters.get(i - 1);
            boolean selected = i == 0 ? onContents : !onContents && left.chapter == chapter;
            int y = top + i * th;
            int h = th - 1;
            int out = selected ? TAB_OUT : TAB_OUT - 3;
            boolean hover = mx >= bx + bw && mx < bx + bw + out && my >= y && my < y + h;
            if (!icons) {
                AlmanacUi.tab(g, bx + bw - TAB_BASE, y, TAB_BASE + out, h, selected ? 2 : hover ? 1 : 0);
                continue;
            }
            ItemStack icon = chapter == null ? new ItemStack(ModItems.ALMANAC.get()) : chapter.icon();
            float scale = h >= 18 ? 1.0F : h >= 13 ? 0.75F : 0.5F;
            int size = Math.round(16 * scale);
            item(g, icon, bx + bw + (out - size) / 2, y + (h - size) / 2, scale);
            int target = chapter == null ? 0 : layout.chapterContentsPage(chapter);
            hot.add(new Hot(bx + bw, y, out, h, () -> openContents(target)));
            if (hover) {
                tipText = chapter == null ? Component.translatable("almanac.lacuna.ui.contents")
                        : Component.translatable(chapter.titleKey());
            }
        }
    }

    private void drawArrows(GuiGraphics g, int mx, int my) {
        int y = by + COVER + ph - 14;
        if (spread > 0) {
            int x = bx + COVER + 7;
            boolean hover = inside(mx, my, x, y, AlmanacUi.ARROW_W, AlmanacUi.ARROW_H);
            AlmanacUi.arrow(g, x, y, false, hover);
            hot.add(new Hot(x, y, AlmanacUi.ARROW_W, AlmanacUi.ARROW_H, () -> turnTo(spread - 1)));
            if (hover) {
                tipText = Component.translatable("almanac.lacuna.ui.previous");
            }
        }
        if (spread < layout.spreads() - 1) {
            int x = bx + bw / 2 + pw - 7 - AlmanacUi.ARROW_W;
            boolean hover = inside(mx, my, x, y, AlmanacUi.ARROW_W, AlmanacUi.ARROW_H);
            AlmanacUi.arrow(g, x, y, true, hover);
            hot.add(new Hot(x, y, AlmanacUi.ARROW_W, AlmanacUi.ARROW_H, () -> turnTo(spread + 1)));
            if (hover) {
                tipText = Component.translatable("almanac.lacuna.ui.next");
            }
        }
    }

    private void drawPage(GuiGraphics g, int index, int px, boolean left, int mx, int my) {
        AlmanacLayout.Page p = page(index);
        if (p == null) {
            return;
        }
        int top = by + COVER;
        int x = px + (left ? M_OUT : M_IN);
        int y0 = top + M_TOP;

        Component header = p.contents ? (index == 0 ? null : Component.translatable("almanac.lacuna.ui.contents"))
                : p.chapter == null ? null : Component.translatable(p.chapter.titleKey());
        if (header != null) {
            FormattedCharSequence line = AlmanacLayout.fit(font, header, cw);
            g.drawString(font, line, x + (cw - font.width(line)) / 2, top + 6, AlmanacUi.RUBY_SOFT, false);
            g.fill(x + 6, top + 15, x + cw - 6, top + 16, AlmanacUi.RULE);
        }

        drawWhereBoxes(g, p, x, y0);
        for (AlmanacLayout.El el : p.els) {
            drawElement(g, el, x, y0 + el.y, mx, my);
        }

        String number = Integer.toString(index + 1);
        g.drawString(font, number, px + (pw - font.width(number)) / 2, top + ph - 12, AlmanacUi.INK_DIM, false);
    }

    /** One box behind every run of "where to find it" elements on the page (a box may continue from the previous page). */
    private void drawWhereBoxes(GuiGraphics g, AlmanacLayout.Page p, int x, int y0) {
        AlmanacLayout.El first = null;
        AlmanacLayout.El last = null;
        for (int i = 0; i <= p.els.size(); i++) {
            AlmanacLayout.El el = i < p.els.size() ? p.els.get(i) : null;
            boolean where = el != null && (el.kind == AlmanacLayout.Kind.WHERE_HEAD || el.kind == AlmanacLayout.Kind.WHERE_TEXT
                    || el.kind == AlmanacLayout.Kind.WHERE_END);
            if (where) {
                if (first == null) {
                    first = el;
                }
                last = el;
                continue;
            }
            if (first != null) {
                int top = y0 + first.y - (first.kind == AlmanacLayout.Kind.WHERE_HEAD ? 0 : 3);
                int bottom = y0 + last.y + last.h + (last.kind == AlmanacLayout.Kind.WHERE_END ? 0 : 3);
                AlmanacUi.whereBox(g, x - 3, top, cw + 6, bottom - top);
                first = null;
                last = null;
            }
        }
    }

    private void drawElement(GuiGraphics g, AlmanacLayout.El el, int x, int y, int mx, int my) {
        switch (el.kind) {
            case BOOK_TITLE -> {
                int w = Math.round(font.width(el.big) * el.scale);
                g.pose().pushPose();
                g.pose().translate(x + (cw - w) / 2.0F, y, 0.0F);
                g.pose().scale(el.scale, el.scale, 1.0F);
                g.drawString(font, el.big, 0, 0, AlmanacUi.RUBY, false);
                g.pose().popPose();
                Component small = el.small.copy().withStyle(ChatFormatting.BOLD);
                g.drawString(font, small, x + (cw - font.width(small)) / 2, y + Math.round(AlmanacLayout.LINE * el.scale) + 2,
                        AlmanacUi.GOLD, false);
            }
            case TAGLINE -> {
                FormattedCharSequence line = el.lines.get(0);
                g.drawString(font, line, x + (cw - font.width(line)) / 2, y, AlmanacUi.INK_SOFT, false);
            }
            case ORNAMENT -> AlmanacUi.ornament(g, x, y + (el.h - AlmanacUi.ORNAMENT_H) / 2, cw);
            case CHAPTER_HEAD -> {
                boolean hover = inside(mx, my, x - 2, y, cw + 4, el.h - 1);
                if (hover) {
                    AlmanacUi.rowHover(g, x - 2, y, cw + 4, el.h - 1);
                }
                item(g, el.icon, x, y + (el.h - 17) / 2, 1.0F);
                int ty = y + (el.h - 1 - el.lines.size() * AlmanacLayout.LINE) / 2 + 1;
                for (FormattedCharSequence line : el.lines) {
                    g.drawString(font, line, x + 19, ty, AlmanacUi.RUBY, false);
                    ty += AlmanacLayout.LINE;
                }
                g.fill(x, y + el.h - 1, x + cw, y + el.h, AlmanacUi.RULE);
                AlmanacBook.Chapter chapter = el.chapter;
                if (!chapter.entries().isEmpty()) {
                    hot.add(new Hot(x - 2, y, cw + 4, el.h - 1, () -> openEntry(chapter.entries().get(0))));
                }
            }
            case ROW -> {
                boolean hover = inside(mx, my, x - 2, y, cw + 4, el.h);
                if (hover) {
                    AlmanacUi.rowHover(g, x - 2, y, cw + 4, el.h);
                }
                item(g, el.icon, x, y, 0.75F);
                g.drawString(font, el.lines.get(0), x + 16, y + 2, hover ? AlmanacUi.RUBY : AlmanacUi.INK, false);
                AlmanacBook.Entry target = el.target;
                hot.add(new Hot(x - 2, y, cw + 4, el.h, () -> openEntry(target)));
            }
            case TITLE -> {
                int iy = y + (el.h - 1 - 16) / 2;
                item(g, el.icon, x, iy, 1.0F);
                if (inside(mx, my, x, iy, 16, 16)) {
                    tipItem = el.icon;
                }
                int ty = y + (el.h - 1 - el.lines.size() * AlmanacLayout.LINE) / 2 + 1;
                for (FormattedCharSequence line : el.lines) {
                    g.drawString(font, line, x + 20, ty, AlmanacUi.RUBY, false);
                    ty += AlmanacLayout.LINE;
                }
            }
            case ICONS -> {
                int ix = x;
                for (ItemStack stack : el.icons) {
                    item(g, stack, ix, y + 1, 1.0F);
                    if (inside(mx, my, ix, y + 1, 16, 16)) {
                        tipItem = stack;
                    }
                    ix += AlmanacLayout.ICON_STEP;
                }
            }
            case TEXT -> {
                if (el.bullet) {
                    AlmanacUi.glyph(g, AlmanacUi.GLYPH_BULLET, x - 1, y);
                }
                g.drawString(font, el.lines.get(0), x + el.indent, y, AlmanacUi.INK, false);
            }
            case WHERE_HEAD -> {
                AlmanacUi.glyph(g, AlmanacUi.GLYPH_WHERE, x + 1, y + 3);
                g.drawString(font, Component.translatable("almanac.lacuna.ui.where").withStyle(ChatFormatting.BOLD),
                        x + 12, y + 4, AlmanacUi.RUBY, false);
            }
            case WHERE_TEXT -> g.drawString(font, el.lines.get(0), x + 4, y, AlmanacUi.INK, false);
            case GAP, WHERE_END -> {
            }
        }
    }

    /** An item icon at (x, y), drawn at {@code scale} (16 px at 1). */
    private void item(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        if (stack.isEmpty()) {
            return;
        }
        if (scale == 1.0F) {
            g.renderItem(stack, x, y);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
