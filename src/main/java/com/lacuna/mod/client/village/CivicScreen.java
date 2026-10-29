package com.lacuna.mod.client.village;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.village.CivicNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The civic block screen: a vanilla-container-like parchment panel in an oak frame, the block's title on a red
 * ribbon, tabs when the block has several functions, a scrollable list of cards with tooltips, vanilla buttons for the
 * actions and a footer with the player's reputation and coins. Purely a view: every number comes from the server's
 * snapshot and every button sends an action the server validates and answers with a new snapshot.
 */
public final class CivicScreen extends Screen {
    private static final int W = 248;
    private static final int H = 206;
    private static final int ROW_H = 22;
    private static final int ROW_STEP = 24;
    private static final int REFRESH_TICKS = 40;
    private static final int FEEDBACK_TICKS = 160;
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    final BlockPos pos;
    final String kind;
    private CompoundTag data;
    private CivicPages.Built built;
    private int tab = -1;
    private double scroll;
    private Component feedback = Component.empty();
    private boolean feedbackOk = true;
    private int feedbackTicks;
    private int refreshTicks;
    private int left;
    private int top;

    CivicScreen(CivicNet.Snapshot snapshot) {
        super(CivicPages.build(snapshot.kind(), snapshot.data()).title());
        this.pos = snapshot.pos();
        this.kind = snapshot.kind();
        apply(snapshot);
    }

    /** A fresh snapshot for this block: new data (same tab and scroll), plus the outcome of the last action. */
    void apply(CivicNet.Snapshot snapshot) {
        this.data = snapshot.data();
        this.built = CivicPages.build(kind, data);
        if (tab < 0 || tab >= built.pages().size()) {
            tab = Math.min(built.defaultTab(), built.pages().size() - 1);
        }
        if (!snapshot.feedback().getString().isEmpty()) {
            feedback = snapshot.feedback();
            feedbackOk = snapshot.ok();
            feedbackTicks = FEEDBACK_TICKS;
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    private CivicPage page() {
        return built.pages().get(tab);
    }

    private boolean hasTabs() {
        return built.pages().size() > 1;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2 + (hasTabs() ? 11 : 0);
        List<CivicPage.ButtonSpec> specs = page().buttons;
        if (specs.isEmpty()) {
            return;
        }
        int gap = 4;
        int bw = Math.min(114, (W - 16 - gap * (specs.size() - 1)) / specs.size());
        int total = bw * specs.size() + gap * (specs.size() - 1);
        int x = left + (W - total) / 2;
        for (CivicPage.ButtonSpec spec : specs) {
            Button b = Button.builder(spec.label(), btn -> send(spec.action())).bounds(x, top + 148, bw, 20).build();
            b.active = spec.active();
            if (!spec.tooltip().getString().isEmpty()) {
                b.setTooltip(Tooltip.create(spec.tooltip()));
            }
            addRenderableWidget(b);
            x += bw + gap;
        }
    }

    private void send(String action) {
        PacketDistributor.sendToServer(new CivicNet.Action(pos, action));
    }

    private int listTop() {
        return top + 42;
    }

    private int listHeight() {
        return page().buttons.isEmpty() ? 126 : 102;
    }

    private int contentHeight() {
        return page().rows.size() * ROW_STEP;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - (listHeight() - 6));
    }

    @Override
    public void tick() {
        super.tick();
        if (feedbackTicks > 0) {
            feedbackTicks--;
        }
        if (minecraft == null || minecraft.player == null || minecraft.level == null
                || minecraft.player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR || minecraft.level.getBlockState(pos).isAir()) {
            onClose();
            return;
        }
        if (++refreshTicks >= REFRESH_TICKS) {
            refreshTicks = 0;
            send("refresh");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        if (hasTabs()) {
            for (int i = 0; i < built.pages().size(); i++) {
                if (i != tab) {
                    drawTab(g, i, false);
                }
            }
        }
        CivicUi.panel(g, left, top, W, H);
        if (hasTabs()) {
            drawTab(g, tab, true);
        }
        CivicUi.banner(g, left + 8, top + 8, W - 16);
        g.drawCenteredString(font, title, left + W / 2, top + 13, 0xFFF6E3A0);
        CivicPage page = page();
        g.drawString(font, fit(page.tabName, 110), left + 10, top + 31, CivicUi.INK, false);
        FormattedCharSequence village = fit(CivicPages.villageName(data), W - 130);
        g.drawString(font, village, left + W - 10 - font.width(village), top + 31, CivicUi.INK_SOFT, false);
    }

    private void drawTab(GuiGraphics g, int i, boolean selected) {
        int x = left + 8 + i * 31;
        int y = top - 24;
        CivicUi.tab(g, x, y, selected);
        g.renderItem(built.pages().get(i).tabIcon, x + 6, y + (selected ? 6 : 8));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<Component> tooltip = new ArrayList<>();
        renderList(g, mouseX, mouseY, tooltip);
        renderFooter(g, mouseX, mouseY, tooltip);
        if (feedbackTicks > 0 && !feedback.getString().isEmpty()) {
            FormattedCharSequence line = fit(feedback, W - 20);
            g.drawString(font, line, left + (W - font.width(line)) / 2, top + 172, feedbackOk ? CivicUi.GOOD : CivicUi.BAD, false);
        }
        if (hasTabs()) {
            for (int i = 0; i < built.pages().size(); i++) {
                int x = left + 8 + i * 31;
                if (mouseX >= x && mouseX < x + 28 && mouseY >= top - 24 && mouseY < top) {
                    tooltip.add(built.pages().get(i).tabName);
                }
            }
        }
        if (!tooltip.isEmpty()) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, List<Component> tooltip) {
        int lx = left + 8;
        int ly = listTop();
        int lw = W - 16;
        int lh = listHeight();
        CivicUi.inset(g, lx, ly, lw, lh);
        CivicPage page = page();
        if (page.rows.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(page.empty, lw - 20);
            int y = ly + (lh - lines.size() * 10) / 2;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, lx + (lw - font.width(line)) / 2, y, CivicUi.INK_SOFT, false);
                y += 10;
            }
            return;
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        boolean scrolling = maxScroll() > 0;
        int rx = lx + 3;
        int rw = lw - 6 - (scrolling ? 10 : 0);
        boolean inList = mouseX >= lx && mouseX < lx + lw && mouseY >= ly + 2 && mouseY < ly + lh - 2;
        g.enableScissor(lx + 2, ly + 2, lx + lw - 2, ly + lh - 2);
        for (int i = 0; i < page.rows.size(); i++) {
            int ry = ly + 3 + i * ROW_STEP - (int) scroll;
            if (ry + ROW_H < ly || ry > ly + lh) {
                continue;
            }
            CivicPage.Row row = page.rows.get(i);
            boolean hover = inList && mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + ROW_H;
            renderRow(g, row, rx, ry, rw, hover);
            if (hover) {
                if (!row.tooltip.isEmpty()) {
                    tooltip.addAll(row.tooltip);
                } else if (font.width(row.detail) > detailWidth(rw) || font.width(row.title) > titleWidth(row, rw)) {
                    tooltip.add(row.title);
                    if (!row.detail.getString().isEmpty()) {
                        tooltip.add(row.detail.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                }
            }
        }
        g.disableScissor();
        if (scrolling) {
            int sx = lx + lw - 11;
            boolean hoverBar = mouseX >= sx && mouseX < sx + 8 && mouseY >= ly + 2 && mouseY < ly + lh - 2;
            CivicUi.scrollbar(g, sx, ly + 3, lh - 6, (float) (scroll / maxScroll()), (lh - 6) / (float) contentHeight(), hoverBar);
        }
    }

    private int rightWidth(CivicPage.Row row) {
        int w = row.right.getString().isEmpty() ? 0 : font.width(row.right) + 6;
        return w + (row.bar >= 0 ? 56 : 0);
    }

    private int titleWidth(CivicPage.Row row, int rw) {
        return rw - 26 - rightWidth(row);
    }

    private int detailWidth(int rw) {
        return rw - 28;
    }

    private void renderRow(GuiGraphics g, CivicPage.Row row, int x, int y, int w, boolean hover) {
        CivicUi.row(g, x, y, w, ROW_H, hover);
        if (!row.icon.isEmpty()) {
            g.renderItem(row.icon, x + 3, y + 3);
        } else if (row.glyph >= 0) {
            CivicUi.glyph(g, row.glyph, x + 7, y + 7);
        }
        int titleColor = row.dim ? CivicUi.INK_DIM : row.titleColor;
        boolean oneLine = row.detail.getString().isEmpty();
        int ty = oneLine ? y + 7 : y + 3;
        g.drawString(font, fit(row.title, titleWidth(row, w)), x + 23, ty, titleColor, false);
        if (!oneLine) {
            g.drawString(font, fit(row.detail, detailWidth(w)), x + 23, y + 12, row.dim ? CivicUi.INK_DIM : CivicUi.INK_SOFT, false);
        }
        int rx = x + w - 4;
        if (row.bar >= 0) {
            rx -= 52;
            CivicUi.bar(g, rx, y + (oneLine ? 9 : 5), row.bar);
            rx -= 4;
        }
        if (!row.right.getString().isEmpty()) {
            int rw = font.width(row.right);
            g.drawString(font, row.right, rx - rw, oneLine ? y + 7 : y + 3, row.dim ? CivicUi.INK_DIM : row.rightColor, false);
        }
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY, List<Component> tooltip) {
        int rep = data.getInt("rep");
        int fy = top + 183;
        Component label = Component.translatable("lacuna.civic.footer.rep", CivicPages.signed(rep), CivicPages.tier(rep));
        FormattedCharSequence text = fit(label, 96);
        g.drawString(font, text, left + 10, fy + 2, CivicUi.tierColor(rep), false);
        int bx = left + 108;
        CivicUi.repBar(g, bx, fy + 3, rep);
        if (mouseX >= left + 8 && mouseX < bx + 104 && mouseY >= fy - 2 && mouseY < fy + 12) {
            tooltip.add(Component.translatable("lacuna.civic.footer.rep_tip", CivicPages.signed(rep), CivicPages.tier(rep)));
            tooltip.add(Component.translatable("lacuna.civic.footer.price", CivicPages.fmt(data.getDouble("price")))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            for (ReputationMath.Tier t : ReputationMath.Tier.values()) {
                tooltip.add(Component.translatable("lacuna.civic.tier_range." + t.name().toLowerCase(java.util.Locale.ROOT))
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
        int coins = data.getInt("coins");
        int cx = left + W - 30;
        g.renderItem(new ItemStack(ModItems.VILLAGE_COIN.get()), cx - 4, fy - 2);
        g.drawString(font, Integer.toString(coins), cx + 13, fy + 2, CivicUi.INK, false);
        if (mouseX >= cx - 4 && mouseX < left + W - 8 && mouseY >= fy - 2 && mouseY < fy + 14) {
            tooltip.add(Component.translatable("lacuna.civic.footer.coins", coins));
        }
    }

    private FormattedCharSequence fit(Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(CommonComponents.ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, CommonComponents.ELLIPSIS));
    }

    // ------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasTabs() && button == 0) {
            for (int i = 0; i < built.pages().size(); i++) {
                int x = left + 8 + i * 31;
                if (mouseX >= x && mouseX < x + 28 && mouseY >= top - 24 && mouseY < top) {
                    selectTab(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectTab(int i) {
        if (i != tab && i >= 0 && i < built.pages().size()) {
            tab = i;
            scroll = 0;
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - scrollY * 12));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (hasTabs() && keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            selectTab(keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
