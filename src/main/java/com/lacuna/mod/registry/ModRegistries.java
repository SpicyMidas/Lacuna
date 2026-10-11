package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Holds the shared deferred registers so feature registry classes can reference each other without static
 * initialisation cycles. {@link #register(IEventBus)} first class-loads every registry holder, then attaches
 * the registers to the mod event bus.
 */
public final class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Lacuna.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Lacuna.MODID);

    private ModRegistries() {}

    public static void register(IEventBus modBus) {
        ModBlocks.init();
        ModItems.init();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
    }
}
