package com.lacuna.mod.client.render;

import com.lacuna.mod.Lacuna;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.StencilManager;
import org.lwjgl.opengl.GL11;

/**
 * The one stencil bit Lacuna reserves on the main render target, shared by the Divine Glass X-ray window
 * ({@code client/sight}) and the Mirror Glass reflections ({@code client/mirror}). They draw at different render
 * stages and each clears the bit before using it, so one bit serves both.
 * <p>
 * {@link #ensure} must be called between frames (client tick): it reserves the bit once, enables the stencil buffer on
 * the main target and, once, rebuilds the post chains so that Fabulous' targets get a stencil too (they copy depth from
 * the main target, which only works when both have the same depth-stencil format; NeoForge's PostChain mirrors the
 * screen). The small static helpers wrap the GL stencil calls of the usual "mark, then draw where marked" pattern.
 */
public final class StencilSupport {
    private static final int ALL_BITS = -1;
    /** -2: not reserved yet, -1: no free bit (features needing it stay off), otherwise the bit index. */
    private static int bit = -2;
    private static boolean chainsReloaded;

    private StencilSupport() {}

    /** True once reserving the bit failed: every stencil feature falls back (or stays off) for the session. */
    public static boolean failed() {
        return bit == -1;
    }

    /** Reserves the bit and enables the stencil buffer (once, between frames); true when the stencil is usable. */
    public static boolean ensure(Minecraft mc) {
        if (bit == -2) {
            bit = StencilManager.reserveBit();
            if (bit < 0) {
                bit = -1;
                Lacuna.LOGGER.warn("Lacuna: no free stencil bit; the Divine Glass X-ray window uses outlines and mirror reflections are off");
                return false;
            }
        }
        if (bit < 0) {
            return false;
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (!main.isStencilEnabled()) {
            main.enableStencil();
            Lacuna.LOGGER.info("Lacuna: stencil buffer enabled on the main render target");
        }
        RenderTarget translucent = mc.levelRenderer.getTranslucentTarget();
        if (translucent != null && !translucent.isStencilEnabled() && !chainsReloaded) {
            chainsReloaded = true;
            mc.levelRenderer.onResourceManagerReload(mc.getResourceManager());
        }
        return main.isStencilEnabled();
    }

    /** Stencil mask of the reserved bit, or 0 when it is not reserved or the main target has no stencil yet. */
    public static int mask() {
        return bit >= 0 && Minecraft.getInstance().getMainRenderTarget().isStencilEnabled() ? 1 << bit : 0;
    }

    /** Enables the stencil test and clears our bit in the bound target. */
    public static void begin(int mask) {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        clear(mask);
    }

    /** Clears our bit (only) in the bound target. */
    public static void clear(int mask) {
        RenderSystem.stencilMask(mask);
        RenderSystem.clearStencil(0);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** Whatever is drawn next sets our bit. */
    public static void mark(int mask) {
        RenderSystem.stencilMask(mask);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, mask, mask);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
    }

    /** Whatever is drawn next only lands where our bit is set (and leaves the stencil alone). */
    public static void inside(int mask) {
        RenderSystem.stencilFunc(GL11.GL_EQUAL, mask, mask);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    /** Back to the GL defaults with the stencil test off. */
    public static void end() {
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, ALL_BITS);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.stencilMask(ALL_BITS);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }
}
