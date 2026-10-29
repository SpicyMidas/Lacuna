package com.lacuna.mod.client.guide;

import com.lacuna.mod.Lacuna;
import net.minecraft.client.Minecraft;

/**
 * Opens the Almanac and remembers, for the rest of the game session, where it was left open: an entry id (or the
 * contents) and how many pages into it, so the book reopens there even after the window changed size and the pages
 * reflowed.
 */
public final class AlmanacClient {
    private static String anchor = AlmanacLayout.CONTENTS;
    private static int offset;

    private AlmanacClient() {}

    /** Called by the Almanac item on the client. */
    public static void open() {
        try {
            Minecraft.getInstance().setScreen(new AlmanacScreen());
        } catch (RuntimeException e) {
            Lacuna.LOGGER.warn("[Lacuna] the Almanac could not be opened", e);
        }
    }

    static String anchor() {
        return anchor;
    }

    static int offset() {
        return offset;
    }

    static void remember(String entryOrContents, int pagesIn) {
        anchor = entryOrContents == null ? AlmanacLayout.CONTENTS : entryOrContents;
        offset = Math.max(0, pagesIn);
    }
}
