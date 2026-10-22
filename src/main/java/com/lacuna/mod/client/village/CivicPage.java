package com.lacuna.mod.client.village;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One tab of a civic screen: a heading, a scrollable list of rows and the page's buttons. */
final class CivicPage {
    final Component tabName;
    final ItemStack tabIcon;
    final List<Row> rows = new ArrayList<>();
    final List<ButtonSpec> buttons = new ArrayList<>();
    Component empty = Component.empty();

    CivicPage(Component tabName, ItemStack tabIcon) {
        this.tabName = tabName;
        this.tabIcon = tabIcon;
    }

    Row add(Component title) {
        Row row = new Row(title);
        rows.add(row);
        return row;
    }

    CivicPage button(Component label, String action, boolean active, Component tooltip) {
        buttons.add(new ButtonSpec(label, action, active, tooltip));
        return this;
    }

    CivicPage empty(Component text) {
        this.empty = text;
        return this;
    }

    record ButtonSpec(Component label, String action, boolean active, Component tooltip) {}

    /** A list card: item icon or glyph, title, grey detail line, optional right-hand value and progress bar. */
    static final class Row {
        final Component title;
        ItemStack icon = ItemStack.EMPTY;
        int glyph = -1;
        Component detail = Component.empty();
        Component right = Component.empty();
        int titleColor = CivicUi.INK;
        int rightColor = CivicUi.INK_SOFT;
        float bar = -1;
        boolean dim;
        final List<Component> tooltip = new ArrayList<>();

        Row(Component title) {
            this.title = title;
        }

        Row icon(ItemStack stack) {
            this.icon = stack;
            return this;
        }

        Row glyph(int glyph) {
            this.glyph = glyph;
            return this;
        }

        Row detail(Component detail) {
            this.detail = detail;
            return this;
        }

        Row right(Component right, int color) {
            this.right = right;
            this.rightColor = color;
            return this;
        }

        Row color(int color) {
            this.titleColor = color;
            return this;
        }

        Row bar(float fraction) {
            this.bar = fraction;
            return this;
        }

        Row dim(boolean dim) {
            this.dim = dim;
            return this;
        }

        Row tip(Component... lines) {
            tooltip.addAll(List.of(lines));
            return this;
        }
    }
}
