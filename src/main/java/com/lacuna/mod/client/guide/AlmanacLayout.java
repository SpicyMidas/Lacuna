package com.lacuna.mod.client.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The Almanac typeset for one page size: the contents (every entry, grouped by chapter) and then every entry from a fresh
 * page, its text wrapped to the page width and running on over as many pages as it needs. Rebuilt whenever the screen
 * is (re)sized, so the book reflows instead of scaling. Entry texts are lang strings with a light markup: a line break
 * starts a paragraph, a paragraph starting with "- " is a bullet, and {@code **...**} is bold.
 */
final class AlmanacLayout {
    static final String CONTENTS = "#contents";
    static final int LINE = 9;
    static final int ROW_H = 12;
    static final int ICON_STEP = 18;

    enum Kind { BOOK_TITLE, TAGLINE, ORNAMENT, CHAPTER_HEAD, ROW, TITLE, ICONS, TEXT, GAP, WHERE_HEAD, WHERE_TEXT, WHERE_END }

    /** One typeset element; {@link #y} is its offset from the top of the page's text area. */
    static final class El {
        final Kind kind;
        final int h;
        int y;
        List<FormattedCharSequence> lines = List.of();
        boolean bullet;
        int indent;
        ItemStack icon = ItemStack.EMPTY;
        List<ItemStack> icons = List.of();
        AlmanacBook.Entry target;
        AlmanacBook.Chapter chapter;
        float scale = 1.0F;
        Component big = Component.empty();
        Component small = Component.empty();

        El(Kind kind, int h) {
            this.kind = kind;
            this.h = h;
        }
    }

    static final class Page {
        final List<El> els = new ArrayList<>();
        /** Contents pages have no entry; their chapter is the first one listed on them. */
        AlmanacBook.Entry entry;
        AlmanacBook.Chapter chapter;
        boolean contents;
        /** Index of this page inside its entry (or inside the contents). */
        int indexInEntry;
    }

    final List<Page> pages;
    final int contentsPages;
    private final Map<String, Integer> entryFirst;
    private final Map<String, Integer> entryCount;
    private final Map<String, Integer> chapterContents;

    private AlmanacLayout(Builder b) {
        this.pages = List.copyOf(b.pages);
        this.contentsPages = b.contentsPages;
        this.entryFirst = b.entryFirst;
        this.entryCount = b.entryCount;
        this.chapterContents = b.chapterContents;
    }

    int spreads() {
        return Math.max(1, (pages.size() + 1) / 2);
    }

    /** The spread showing {@code offset} pages into {@code anchor} (an entry id or {@link #CONTENTS}). */
    int spreadOf(String anchor, int offset) {
        if (anchor == null || CONTENTS.equals(anchor)) {
            return clamp(offset, 0, Math.max(0, contentsPages - 1)) / 2;
        }
        Integer first = entryFirst.get(anchor);
        if (first == null) {
            return 0;
        }
        return (first + clamp(offset, 0, entryCount.getOrDefault(anchor, 1) - 1)) / 2;
    }

    int entryPage(AlmanacBook.Entry entry) {
        return entryFirst.getOrDefault(entry.id(), 0);
    }

    /** The contents page listing {@code chapter}. */
    int chapterContentsPage(AlmanacBook.Chapter chapter) {
        return chapterContents.getOrDefault(chapter.id(), 0);
    }

    static AlmanacLayout build(AlmanacBook book, Font font, int width, int height) {
        Builder b = new Builder(font, width, height);
        b.contents(book);
        for (AlmanacBook.Chapter chapter : book.chapters) {
            for (AlmanacBook.Entry entry : chapter.entries()) {
                b.entry(entry);
            }
        }
        if (b.pages.isEmpty()) {
            b.start(null, null, true);
        }
        return new AlmanacLayout(b);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static String text(String key) {
        return Language.getInstance().getOrDefault(key, key);
    }

    /** Lang text with {@code **bold**} spans, in the given base style. */
    static MutableComponent markup(String s, Style base) {
        MutableComponent out = Component.empty();
        boolean bold = false;
        int i = 0;
        while (true) {
            int j = s.indexOf("**", i);
            String part = j < 0 ? s.substring(i) : s.substring(i, j);
            if (!part.isEmpty()) {
                out.append(Component.literal(part).withStyle(base.withBold(bold)));
            }
            if (j < 0) {
                return out;
            }
            bold = !bold;
            i = j + 2;
        }
    }

    /** {@code text} cut to {@code width} with an ellipsis when it does not fit. */
    static FormattedCharSequence fit(Font font, FormattedText text, int width) {
        if (font.width(text) <= width) {
            return Language.getInstance().getVisualOrder(text);
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("…")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    private static final class Builder {
        final Font font;
        final int width;
        final int height;
        final List<Page> pages = new ArrayList<>();
        final Map<String, Integer> entryFirst = new HashMap<>();
        final Map<String, Integer> entryCount = new HashMap<>();
        final Map<String, Integer> chapterContents = new HashMap<>();
        int contentsPages;
        Page page;
        int y;

        Builder(Font font, int width, int height) {
            this.font = font;
            this.width = width;
            this.height = height;
        }

        void start(AlmanacBook.Chapter chapter, AlmanacBook.Entry entry, boolean contents) {
            page = new Page();
            page.chapter = chapter;
            page.entry = entry;
            page.contents = contents;
            pages.add(page);
            y = 0;
        }

        /** Overflow: the same entry (or the contents) carries on over the page. */
        void turn() {
            Page next = new Page();
            next.chapter = page.chapter;
            next.entry = page.entry;
            next.contents = page.contents;
            next.indexInEntry = page.indexInEntry + 1;
            if (next.contents) {
                next.chapter = null;
            }
            pages.add(next);
            page = next;
            y = 0;
        }

        /** Places {@code el}, turning the page first unless it and {@code keep} more pixels fit. Gaps never open a page. */
        void add(El el, int keep) {
            if (el.kind == Kind.GAP && y == 0) {
                return;
            }
            if (y > 0 && y + el.h + keep > height) {
                if (el.kind == Kind.GAP || el.kind == Kind.WHERE_END) {
                    return; // padding never opens a page: whatever follows turns it if it must
                }
                turn();
            }
            el.y = y;
            page.els.add(el);
            y += el.h;
            if (page.contents && page.chapter == null && el.chapter != null) {
                page.chapter = el.chapter;
            }
        }

        void add(El el) {
            add(el, 0);
        }

        void gap(int h) {
            add(new El(Kind.GAP, h));
        }

        // ------------------------------------------------------------------------------------------ contents

        void contents(AlmanacBook book) {
            start(null, null, true);
            Component big = Component.translatable("almanac.lacuna.ui.title_big");
            float scale = font.width(big) * 2 <= width ? 2.0F : font.width(big) * 1.5F <= width ? 1.5F : 1.0F;
            El t = new El(Kind.BOOK_TITLE, Math.round(LINE * scale) + 2 + LINE + 3);
            t.scale = scale;
            t.big = big;
            t.small = Component.translatable("almanac.lacuna.ui.title_small");
            add(t);
            for (FormattedCharSequence line : font.split(Component.translatable("almanac.lacuna.ui.tagline")
                    .withStyle(ChatFormatting.ITALIC), width - 6)) {
                El l = new El(Kind.TAGLINE, LINE);
                l.lines = List.of(line);
                add(l);
            }
            add(new El(Kind.ORNAMENT, 11));
            for (AlmanacBook.Chapter chapter : book.chapters) {
                gap(3);
                List<FormattedCharSequence> lines = font.split(Component.translatable(chapter.titleKey())
                        .withStyle(ChatFormatting.BOLD), width - 19);
                El head = new El(Kind.CHAPTER_HEAD, Math.max(18, Math.min(2, lines.size()) * LINE + 2) + 1);
                head.lines = lines.size() > 2 ? lines.subList(0, 2) : lines;
                head.icon = chapter.icon();
                head.chapter = chapter;
                add(head, ROW_H);
                chapterContents.put(chapter.id(), pages.size() - 1);
                for (AlmanacBook.Entry entry : chapter.entries()) {
                    El row = new El(Kind.ROW, ROW_H);
                    row.icon = entry.icon();
                    row.target = entry;
                    row.chapter = chapter;
                    row.lines = List.of(fit(font, Component.translatable(entry.titleKey()), width - 17));
                    add(row);
                }
            }
            contentsPages = pages.size();
        }

        // ------------------------------------------------------------------------------------------ an entry

        void entry(AlmanacBook.Entry entry) {
            start(entry.chapter(), entry, false);
            int first = pages.size() - 1;
            entryFirst.put(entry.id(), first);

            List<FormattedCharSequence> titleLines = font.split(Component.translatable(entry.titleKey())
                    .withStyle(ChatFormatting.BOLD), width - 20);
            El title = new El(Kind.TITLE, Math.max(18, titleLines.size() * LINE + 2) + 3);
            title.lines = titleLines;
            title.icon = entry.icon();
            add(title);
            if (!entry.extras().isEmpty()) {
                int perRow = Math.max(1, width / ICON_STEP);
                List<ItemStack> extras = entry.extras();
                for (int i = 0; i < extras.size(); i += perRow) {
                    El icons = new El(Kind.ICONS, ICON_STEP);
                    icons.icons = extras.subList(i, Math.min(extras.size(), i + perRow));
                    add(icons);
                }
            }
            add(new El(Kind.ORNAMENT, 10));

            boolean firstParagraph = true;
            boolean lastBullet = false;
            for (String paragraph : text(entry.textKey()).split("\n")) {
                if (paragraph.isBlank()) {
                    gap(4);
                    continue;
                }
                boolean bullet = paragraph.startsWith("- ");
                String body = bullet ? paragraph.substring(2) : paragraph;
                int indent = bullet ? 8 : 0;
                if (!firstParagraph) {
                    gap(bullet && lastBullet ? 1 : 3);
                }
                List<FormattedCharSequence> lines = font.split(markup(body, Style.EMPTY), width - indent);
                for (int i = 0; i < lines.size(); i++) {
                    El line = new El(Kind.TEXT, LINE);
                    line.lines = List.of(lines.get(i));
                    line.indent = indent;
                    line.bullet = bullet && i == 0;
                    add(line);
                }
                firstParagraph = false;
                lastBullet = bullet;
            }

            if (!entry.whereKey().isEmpty() && Language.getInstance().has(entry.whereKey())) {
                gap(5);
                add(new El(Kind.WHERE_HEAD, 14), LINE);
                for (FormattedCharSequence line : font.split(markup(text(entry.whereKey()), Style.EMPTY), width - 8)) {
                    El l = new El(Kind.WHERE_TEXT, LINE);
                    l.lines = List.of(line);
                    add(l);
                }
                add(new El(Kind.WHERE_END, 4));
            }
            entryCount.put(entry.id(), pages.size() - first);
        }
    }
}
