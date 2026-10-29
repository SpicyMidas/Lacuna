package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.fl.ClearIceCapFeature;
import com.lacuna.mod.fl.FloatingIslandFeature;
import com.lacuna.mod.wood.HangingStrandsDecorator;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
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

    // M10: Forgotten Land biome features
    public static final DeferredHolder<Feature<?>, FloatingIslandFeature> FLOATING_ISLAND =
            FEATURES.register("floating_island", () -> new FloatingIslandFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, ClearIceCapFeature> CLEAR_ICE_CAP =
            FEATURES.register("clear_ice_cap", () -> new ClearIceCapFeature(NoneFeatureConfiguration.CODEC));
    // M11: trees that grew into a village's buildings, doorways or streets are cleared (last decoration step)
    public static final DeferredHolder<Feature<?>, com.lacuna.mod.village.VillageClearingFeature> VILLAGE_CLEARING =
            FEATURES.register("village_clearing", () -> new com.lacuna.mod.village.VillageClearingFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}
