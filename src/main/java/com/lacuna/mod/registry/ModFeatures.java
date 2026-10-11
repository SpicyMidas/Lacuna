package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.wood.HangingStrandsDecorator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Code-defined worldgen types (everything else in worldgen is datapack JSON). */
public final class ModFeatures {
    public static final DeferredRegister<TreeDecoratorType<?>> TREE_DECORATORS = DeferredRegister.create(Registries.TREE_DECORATOR_TYPE, Lacuna.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Lacuna.MODID);

    public static final DeferredHolder<TreeDecoratorType<?>, TreeDecoratorType<HangingStrandsDecorator>> HANGING_STRANDS =
            TREE_DECORATORS.register("hanging_strands", () -> new TreeDecoratorType<>(HangingStrandsDecorator.CODEC));

    private ModFeatures() {}
}
