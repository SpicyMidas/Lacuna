package com.lacuna.mod.merchant;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The single currency switch (spec M10/M11c): every Lacuna trade asks this service for its currency item. Until the
 * township coin exists it is the emerald; M11c installs the coin with {@link #install}.
 */
public final class CurrencyService {
    private static volatile Supplier<Item> currency = () -> Items.EMERALD;

    private CurrencyService() {}

    public static Item currency() {
        Item item = currency.get();
        return item == null ? Items.EMERALD : item;
    }

    public static void install(Supplier<Item> supplier) {
        currency = supplier;
    }
}
