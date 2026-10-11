package com.lacuna.mod.wood;

import com.lacuna.mod.Lacuna;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * The four Lacuna wood families (spec M8). Block set types, wood types and tree growers are created in this class
 * initialiser, which runs before any block supplier that uses them (static init order requirement).
 */
public enum WoodFamily {
    CINDERVINE("cindervine"),
    CRYSTAL_WILLOW("crystal_willow"),
    TIDEBLOOM("tidebloom"),
    ECHO_BIRCH("echo_birch");

    /** Chopping Echo Birch rings like amethyst. */
    public static final SoundType ECHO_WOOD = new SoundType(1.0F, 1.2F, SoundEvents.AMETHYST_BLOCK_BREAK,
            SoundEvents.AMETHYST_BLOCK_STEP, SoundEvents.WOOD_PLACE, SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.WOOD_FALL);

    public final String id;
    public final BlockSetType setType;
    public final WoodType woodType;
    public final TreeGrower grower;
    public final ResourceKey<ConfiguredFeature<?, ?>> treeFeature;

    WoodFamily(String id) {
        this.id = id;
        this.setType = BlockSetType.register(new BlockSetType(Lacuna.MODID + ":" + id));
        this.woodType = WoodType.register(new WoodType(Lacuna.MODID + ":" + id, setType));
        this.treeFeature = ResourceKey.create(Registries.CONFIGURED_FEATURE, Lacuna.id(id + "_tree"));
        this.grower = new TreeGrower(Lacuna.MODID + "_" + id, Optional.empty(), Optional.of(treeFeature), Optional.empty());
    }

    public SoundType woodSound() {
        return this == ECHO_BIRCH ? ECHO_WOOD : SoundType.WOOD;
    }

    public boolean fireproof() {
        return this == CINDERVINE;
    }
}
