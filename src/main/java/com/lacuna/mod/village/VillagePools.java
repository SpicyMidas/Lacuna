package com.lacuna.mod.village;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.util.SafeRun;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Brings the civic buildings (village hall, mint, guild hall, schoolhouse) into newly generated vanilla villages of
 * every style, when a server is about to start (before any chunk is generated):
 * <ul>
 *   <li>the <b>civic square</b> joins the vanilla {@code minecraft:village/<type>/town_centers} pool so that about
 *       {@link #SQUARE_SHARE} of the villages grow around it; the square holds all four buildings in its corner lots;</li>
 *   <li>each building also joins {@code minecraft:village/<type>/houses} with a small weight, so the other villages
 *       sometimes get one along their streets.</li>
 * </ul>
 * The vanilla pool files are not overridden: elements are appended to the loaded pools through the access-transformed
 * {@code templates}/{@code rawTemplates} fields. Idempotent; behind {@code villages.civicBuildings}.
 */
public final class VillagePools {
    public static final List<String> TYPES = List.of("plains", "desert", "savanna", "taiga", "snowy");
    public static final List<String> BUILDINGS = List.of("village_hall", "mint", "guild_hall", "schoolhouse");
    public static final String SQUARE = "civic_square";
    /** Share of new villages that start from the civic square (vanilla town centres keep the rest). */
    public static final double SQUARE_SHARE = 0.6;
    /**
     * Weight of each civic building in a houses pool (vanilla houses weigh 1-5, the pools total 68-87). Kept low: the
     * buildings are large and rarely fit between streets anyway, and the square already holds a full set.
     */
    public static final int HOUSE_WEIGHT = 2;
    private static final int MAX_RAW_WEIGHT = 150; // the pool codec's limit per element entry
    private static final ResourceKey<StructureProcessorList> EMPTY = ResourceKey.create(Registries.PROCESSOR_LIST,
            ResourceLocation.withDefaultNamespace("empty"));
    private static final ResourceKey<StructureProcessorList> MOSSY = ResourceKey.create(Registries.PROCESSOR_LIST,
            ResourceLocation.withDefaultNamespace("mossify_10_percent"));

    private VillagePools() {}

    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!VillageService.enabled() || !LacunaConfig.get(LacunaConfig.VILLAGES_CIVIC_BUILDINGS)) {
            return;
        }
        SafeRun.run(VillageService.FEATURE, () -> {
            int added = inject(event.getServer().registryAccess());
            Lacuna.LOGGER.info("Lacuna: {} civic building entries added to the vanilla village pools", added);
        });
    }

    public static ResourceLocation template(String type, String building) {
        return Lacuna.id("village/" + type + "/" + building);
    }

    /** Weight that makes the square {@code share} of a pool whose other entries weigh {@code others} in total. */
    public static int squareWeight(int others, double share) {
        return Math.max(1, (int) Math.round(others * share / (1.0 - share)));
    }

    /** Appends the squares and the buildings to every vanilla village style; returns how many elements were added. */
    public static int inject(RegistryAccess access) {
        Registry<StructureTemplatePool> pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processors = access.registryOrThrow(Registries.PROCESSOR_LIST);
        int added = 0;
        for (String type : TYPES) {
            Holder<StructureProcessorList> processor = processors.getHolder(type.equals("plains") || type.equals("taiga") ? MOSSY : EMPTY)
                    .<Holder<StructureProcessorList>>map(h -> h)
                    .orElseGet(() -> Holder.direct(new StructureProcessorList(List.of())));
            StructureTemplatePool centers = vanillaPool(pools, type, "town_centers");
            if (centers != null && count(centers, template(type, SQUARE)) == 0) {
                add(centers, element(type, SQUARE, processor), squareWeight(centers.size(), SQUARE_SHARE));
                added++;
            }
            StructureTemplatePool houses = vanillaPool(pools, type, "houses");
            if (houses != null) {
                for (String building : BUILDINGS) {
                    if (count(houses, template(type, building)) == 0) {
                        add(houses, element(type, building, processor), HOUSE_WEIGHT);
                        added++;
                    }
                }
            }
        }
        return added;
    }

    private static StructureTemplatePool vanillaPool(Registry<StructureTemplatePool> pools, String type, String name) {
        StructureTemplatePool pool = pools.get(ResourceLocation.withDefaultNamespace("village/" + type + "/" + name));
        if (pool == null) {
            Lacuna.LOGGER.warn("Lacuna: vanilla pool minecraft:village/{}/{} is missing, no civic buildings there", type, name);
        }
        return pool;
    }

    private static StructurePoolElement element(String type, String name, Holder<StructureProcessorList> processor) {
        return StructurePoolElement.single(template(type, name).toString(), processor).apply(StructureTemplatePool.Projection.RIGID);
    }

    private static void add(StructureTemplatePool pool, StructurePoolElement element, int weight) {
        for (int i = 0; i < weight; i++) {
            pool.templates.add(element);
        }
        List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(pool.rawTemplates);
        for (int left = weight; left > 0; left -= MAX_RAW_WEIGHT) {
            raw.add(Pair.of(element, Math.min(left, MAX_RAW_WEIGHT)));
        }
        pool.rawTemplates = raw;
    }

    /** How many weighted entries of the template {@code location} a pool holds. */
    public static int count(StructureTemplatePool pool, ResourceLocation location) {
        int n = 0;
        for (StructurePoolElement element : pool.templates) {
            if (location(element).filter(location::equals).isPresent()) {
                n++;
            }
        }
        return n;
    }

    public static Optional<ResourceLocation> location(StructurePoolElement element) {
        return element instanceof SinglePoolElement single ? single.template.left() : Optional.empty();
    }

    /** "village_hall", "mint", "guild_hall", "schoolhouse" (or "civic_square") for a Lacuna civic element. */
    public static Optional<String> civicBuilding(StructurePoolElement element) {
        return location(element).filter(l -> l.getNamespace().equals(Lacuna.MODID) && l.getPath().startsWith("village/"))
                .map(l -> l.getPath().substring(l.getPath().lastIndexOf('/') + 1))
                .filter(name -> BUILDINGS.contains(name) || SQUARE.equals(name));
    }
}
