package com.lacuna.mod.client.portal;

import java.util.function.BooleanSupplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;

/**
 * The loading screen between the overworld and the Forgotten Land (registered for both directions through NeoForge's
 * dimension transition screens). During a Forgotten Portal crossing it shows the very void the transition overlay
 * faded into, so the screen never flickers to the menu background while the far side loads; the usual
 * "Loading terrain..." line appears only if loading takes a while. Any other crossing (the Totem of Banishing, commands)
 * keeps the vanilla screen.
 */
public class ForgottenVoidScreen extends ReceivingLevelScreen {
    private static final Component LOADING = Component.translatable("multiplayer.downloadingTerrain");
    private static final long SHOW_TEXT_AFTER_MS = 1500L;
    private final long opened = Util.getMillis();

    public ForgottenVoidScreen(BooleanSupplier levelReceived, Reason reason) {
        super(levelReceived, reason);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!PortalTransition.covering()) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        renderBackground(g, mouseX, mouseY, partialTick);
        if (Util.getMillis() - opened > SHOW_TEXT_AFTER_MS) {
            g.drawCenteredString(font, LOADING, width / 2, height / 2 - 50, 0xC9C0E0);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!PortalTransition.covering()) {
            super.renderBackground(g, mouseX, mouseY, partialTick);
            return;
        }
        PortalTransition.drawVoid(g, 1.0F, minecraft.getTimer().getGameTimeDeltaPartialTick(false));
    }
}
