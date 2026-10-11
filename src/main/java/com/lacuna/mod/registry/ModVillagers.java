package com.lacuna.mod.registry;

import com.google.common.collect.ImmutableSet;
import com.lacuna.mod.Lacuna;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Township villager professions (spec 18.3/18.4) and their job-site POIs. */
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Lacuna.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, Lacuna.MODID);

    public static final DeferredHolder<PoiType, PoiType> COINER_POI = poi("coiner", ModBlocks.MINT_PRESS);
    public static final DeferredHolder<PoiType, PoiType> MAGISTRATE_POI = poi("magistrate", ModBlocks.JUDGE_BENCH);
    public static final DeferredHolder<PoiType, PoiType> GUILDMASTER_POI = poi("guildmaster", ModBlocks.GUILD_BOARD);

    public static final DeferredHolder<VillagerProfession, VillagerProfession> COINER = profession("coiner", COINER_POI);
    public static final DeferredHolder<VillagerProfession, VillagerProfession> MAGISTRATE = profession("magistrate", MAGISTRATE_POI);
    public static final DeferredHolder<VillagerProfession, VillagerProfession> GUILDMASTER = profession("guildmaster", GUILDMASTER_POI);

    private ModVillagers() {}

    private static DeferredHolder<PoiType, PoiType> poi(String id, Supplier<? extends Block> block) {
        return POI_TYPES.register(id, () -> new PoiType(ImmutableSet.copyOf(block.get().getStateDefinition().getPossibleStates()), 1, 1));
    }

    private static DeferredHolder<VillagerProfession, VillagerProfession> profession(String id, DeferredHolder<PoiType, PoiType> poi) {
        return PROFESSIONS.register(id, () -> new VillagerProfession(id, holder -> holder.is(poi.getKey()), holder -> holder.is(poi.getKey()),
                ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_TOOLSMITH));
    }
}
