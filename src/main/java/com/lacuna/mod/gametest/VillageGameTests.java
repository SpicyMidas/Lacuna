package com.lacuna.mod.gametest;

import static com.lacuna.mod.gametest.LacunaGameTests.CENTER;
import static com.lacuna.mod.gametest.LacunaGameTests.PLATFORM;
import static com.lacuna.mod.gametest.LacunaGameTests.check;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.VillageBounds;
import com.lacuna.mod.logic.VillageLaws;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModVillagers;
import com.lacuna.mod.village.CaravanEffect;
import com.lacuna.mod.village.ContractData;
import com.lacuna.mod.village.GuildBoardBlock;
import com.lacuna.mod.village.GuildContractItem;
import com.lacuna.mod.village.GuildType;
import com.lacuna.mod.village.Schools;
import com.lacuna.mod.village.VillageEvents;
import com.lacuna.mod.village.VillagePools;
import com.lacuna.mod.village.VillageRecord;
import com.lacuna.mod.village.VillageSavedData;
import com.lacuna.mod.village.VillageService;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests of the village civic system (spec 18, revision 2: it runs in vanilla villages). Villages are generated
 * in memory with {@link Structure#generate} (exactly what /place structure does before placing blocks), so the tests
 * see the real vanilla pools with the civic pieces injected at server start.
 */
@GameTestHolder(Lacuna.MODID)
@PrefixGameTestTemplate(false)
public final class VillageGameTests {
    private VillageGameTests() {}

    static StructureStart generateVillage(ServerLevel level, String type, ChunkPos chunk) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.withDefaultNamespace("village_" + type));
        var generator = level.getChunkSource().getGenerator();
        return structure.generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed(), chunk, 0, level, biome -> true);
    }

    // ------------------------------------------------------------------ records and lookups
    @GameTest(template = PLATFORM, batch = "village_ledger") // own batch: founded villages (radius 48) must not overlap
    public static void playerLedgerFoundsAVillageOnce(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.VILLAGE_LEDGER.get());
        BlockPos abs = helper.absolutePos(CENTER);
        ServerLevel level = helper.getLevel();
        var first = VillageService.foundPlayerVillage(level, abs, null).orElseThrow();
        var second = VillageService.foundPlayerVillage(level, abs, null).orElseThrow();
        check(helper, first == second, "founding is idempotent");
        check(helper, first.kind == VillageRecord.Kind.PLAYER && first.radius == VillageBounds.PLAYER_RADIUS, "player village of radius 48");
        check(helper, VillageService.at(level, abs.east(40)).orElse(null) == first, "the village covers its radius");
        check(helper, VillageService.at(level, abs.east(60)).isEmpty(), "...and no further");
        UUID player = UUID.randomUUID();
        VillageService.set(level, first, player, 80);
        check(helper, VillageService.tier(first, player) == ReputationMath.Tier.HONORED, "honored");
        check(helper, VillageService.priceFactor(first, player) < 1.0, "honored players pay less");
        helper.setBlock(CENTER, Blocks.AIR);
        check(helper, VillageService.at(level, abs).isEmpty(), "breaking the ledger dissolves the founded village");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void vanillaVillageRecordFromStructureStart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = generateVillage(level, "plains", new ChunkPos(3000, 3000));
        check(helper, start.isValid(), "a plains village generates");
        VillageRecord record = VillageService.register(level, start);
        try {
            check(helper, VillageService.register(level, start) == record, "registration is idempotent");
            check(helper, "plains".equals(record.type) && record.kind == VillageRecord.Kind.VANILLA, "vanilla plains record");
            var box = start.getBoundingBox();
            BlockPos middle = new BlockPos((box.minX() + box.maxX()) / 2, box.minY() + 2, (box.minZ() + box.maxZ()) / 2);
            check(helper, record.center.getX() == middle.getX() && record.center.getZ() == middle.getZ(), "centre is the box centre");
            check(helper, record.radius == VillageBounds.radius(box.minX(), box.minZ(), box.maxX(), box.maxZ()), "radius from the box");
            check(helper, record.radius >= VillageBounds.MIN_RADIUS && record.radius <= VillageBounds.MAX_RADIUS,
                    "radius clamped, got " + record.radius);
            check(helper, VillageService.at(level, middle).orElse(null) == record, "lookup finds the village at its centre");
            check(helper, VillageService.at(level, middle.below(80)).isEmpty(), "deep below is not inside");
            check(helper, VillageService.at(level, middle.east(record.radius + 30)).isEmpty(), "far outside is not inside");
            check(helper, record.laws[0] != record.laws[1], "two distinct laws seeded from the start");
            int[] again = VillageLaws.choose(VillageLaws.villageSeed(level.getSeed(), start.getChunkPos().toLong()));
            check(helper, again[0] == record.laws[0] && again[1] == record.laws[1], "laws are seeded from world seed + start chunk");
        } finally {
            VillageSavedData.get(level.getServer()).remove(record.key);
        }
        helper.succeed();
    }

    /**
     * The lazy path used by every rule: a loaded chunk that a village references creates the record, but only when
     * the village's start chunk is loaded too (nothing is generated or loaded for a lookup).
     */
    @GameTest(template = PLATFORM, batch = "village_refs") // own batch: the test chunk briefly holds a village
    public static void villageRecordIsCreatedLazilyFromLoadedChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(CENTER);
        ChunkPos here = new ChunkPos(abs);
        LevelChunk chunk = level.getChunkAt(abs);
        Map<Structure, StructureStart> savedStarts = new HashMap<>(chunk.getAllStarts());
        Map<Structure, LongSet> savedRefs = new HashMap<>();
        chunk.getAllReferences().forEach((s, refs) -> savedRefs.put(s, new LongOpenHashSet(refs)));
        VillageSavedData data = VillageSavedData.get(level.getServer());
        StructureStart start = generateVillage(level, "desert", here);
        check(helper, start.isValid(), "a desert village generates around the test chunk");
        String key = VillageRecord.vanillaKey(level.dimension(), here.toLong());
        ChunkPos far = new ChunkPos(here.x + 4000, here.z);
        try {
            // a reference to a village whose start chunk is not loaded: no record, nothing loaded
            chunk.addReferenceForStructure(start.getStructure(), far.toLong());
            check(helper, VillageService.at(level, abs).isEmpty(), "no record while the start chunk is unloaded");
            check(helper, data.byKey(VillageRecord.vanillaKey(level.dimension(), far.toLong())).isEmpty(), "nothing registered");
            check(helper, level.getChunkSource().getChunkNow(far.x, far.z) == null, "the far start chunk was not loaded");
            // the real village: its start lives in this loaded chunk
            chunk.setStartForStructure(start.getStructure(), start);
            chunk.addReferenceForStructure(start.getStructure(), here.toLong());
            int before = data.size();
            VillageService.at(level, abs);
            VillageRecord record = data.byKey(key).orElse(null);
            check(helper, record != null, "a loaded chunk that references a village creates its record");
            check(helper, "desert".equals(record.type), "the record knows the village style");
            VillageService.at(level, abs);
            check(helper, data.size() == before + 1, "lookups are idempotent");
            check(helper, VillageService.at(level, record.center).orElse(null) == record, "the record is in the spatial index");
            check(helper, VillageService.structureStart(level, record).orElse(null) == start, "the record finds its loaded start");
        } finally {
            data.remove(key);
            chunk.setAllStarts(savedStarts);
            chunk.setAllReferences(savedRefs);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ civic buildings in the vanilla pools
    @GameTest(template = PLATFORM)
    public static void civicBuildingsAreInTheVanillaPools(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        var templates = helper.getLevel().getStructureManager();
        for (String type : VillagePools.TYPES) {
            StructureTemplatePool houses = pools.get(ResourceLocation.withDefaultNamespace("village/" + type + "/houses"));
            check(helper, houses != null, "vanilla houses pool for " + type);
            for (String building : VillagePools.BUILDINGS) {
                int n = VillagePools.count(houses, VillagePools.template(type, building));
                check(helper, n == VillagePools.HOUSE_WEIGHT, type + "/" + building + " has weight " + n);
                check(helper, templates.get(VillagePools.template(type, building)).isPresent(), "template " + type + "/" + building + " exists");
                check(helper, pools.get(VillagePools.template(type, building)) != null, "single-building pool " + type + "/" + building);
            }
            StructureTemplatePool centers = pools.get(ResourceLocation.withDefaultNamespace("village/" + type + "/town_centers"));
            check(helper, centers != null, "vanilla town centre pool for " + type);
            int squares = VillagePools.count(centers, VillagePools.template(type, VillagePools.SQUARE));
            double share = squares / (double) centers.size();
            check(helper, Math.abs(share - VillagePools.SQUARE_SHARE) < 0.02, type + " civic square share " + share);
            check(helper, templates.get(VillagePools.template(type, VillagePools.SQUARE)).isPresent(), "civic square template " + type);
            check(helper, centers.rawTemplates.stream().anyMatch(p -> VillagePools.civicBuilding(p.getFirst()).isPresent()),
                    "the raw element list (written by the pool codec) holds the square too");
        }
        check(helper, VillagePools.inject(access) == 0, "injection is idempotent");
        helper.succeed();
    }

    /** Generates (without placing) 24 villages of every style and counts the civic buildings in their pieces. */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void vanillaVillagesGetCivicBuildings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int samples = 24;
        StringBuilder report = new StringBuilder();
        for (String type : VillagePools.TYPES) {
            Map<String, Integer> villagesWith = new TreeMap<>();
            int allFour = 0;
            int valid = 0;
            for (int i = 0; i < samples; i++) {
                StructureStart start = generateVillage(level, type, new ChunkPos(4000 + i * 37, -4000 - i * 23));
                if (!start.isValid()) {
                    continue;
                }
                valid++;
                Set<String> found = new HashSet<>();
                for (var piece : start.getPieces()) {
                    if (piece instanceof PoolElementStructurePiece pe) {
                        VillagePools.civicBuilding(pe.getElement()).ifPresent(found::add);
                    }
                }
                found.forEach(b -> villagesWith.merge(b, 1, Integer::sum));
                allFour += found.containsAll(VillagePools.BUILDINGS) ? 1 : 0;
            }
            report.append(type).append(": ").append(valid).append(" villages, ").append(villagesWith)
                    .append(", all four buildings in ").append(allFour).append("; ");
            check(helper, valid >= samples / 2, type + " villages generate");
            for (String building : VillagePools.BUILDINGS) {
                check(helper, villagesWith.getOrDefault(building, 0) * 3 >= valid,
                        type + ": " + building + " in fewer than a third of villages: " + villagesWith);
            }
            check(helper, allFour * 5 >= valid * 2, type + ": at least 40% of villages should get all four civic buildings: "
                    + allFour + "/" + valid);
        }
        Lacuna.LOGGER.info("[GameTest] civic buildings per village style: {}", report);
        helper.succeed();
    }

    // ------------------------------------------------------------------ rules
    @GameTest(template = PLATFORM)
    public static void villagePricesFollowReputationAndCoinValue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = generateVillage(level, "savanna", new ChunkPos(-3000, 3000));
        VillageRecord record = VillageService.register(level, start);
        try {
            UUID honored = UUID.randomUUID();
            UUID hostile = UUID.randomUUID();
            VillageService.set(level, record, honored, 100);
            VillageService.set(level, record, hostile, -100);
            record.coinValue = 1.25;
            MerchantOffer bread = new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BREAD), 12, 1, 0.05F);
            MerchantOffer coinPriced = new MerchantOffer(new ItemCost(ModItems.VILLAGE_COIN.get(), 20), new ItemStack(ModItems.RUBY.get()), 12, 1, 0.05F);
            VillageEvents.adjustPrices(record, honored, List.of(bread, coinPriced));
            check(helper, bread.getSpecialPriceDiff() == -5, "honored: vanilla trades x0.75, got " + bread.getSpecialPriceDiff());
            check(helper, coinPriced.getSpecialPriceDiff() == -8, "coin trades also scale with the coin value, got " + coinPriced.getSpecialPriceDiff());
            MerchantOffer hostileBread = new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BREAD), 12, 1, 0.05F);
            VillageEvents.adjustPrices(record, hostile, List.of(hostileBread));
            check(helper, hostileBread.getSpecialPriceDiff() == 5, "hostile: x1.25, got " + hostileBread.getSpecialPriceDiff());
            record.policy = "austerity";
            check(helper, Math.abs(VillageService.priceFactor(record, UUID.randomUUID()) - 1.10) < 1e-9, "austerity x1.10");
        } finally {
            VillageSavedData.get(level.getServer()).remove(record.key);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ professions, currency, guilds, schools
    @GameTest(template = PLATFORM)
    public static void villageProfessionsHaveJobSitesAndTrades(GameTestHelper helper) {
        var mint = PoiTypes.forState(ModBlocks.MINT_PRESS.get().defaultBlockState());
        check(helper, mint.isPresent() && mint.get().is(ModVillagers.COINER_POI.getKey()), "mint press is the coiner job site");
        var bench = PoiTypes.forState(ModBlocks.JUDGE_BENCH.get().defaultBlockState());
        check(helper, bench.isPresent() && bench.get().is(ModVillagers.MAGISTRATE_POI.getKey()), "judge bench is the magistrate job site");
        var trades = VillagerTrades.TRADES.get(ModVillagers.COINER.get());
        check(helper, trades != null && trades.get(1) != null && trades.get(1).length >= 3, "coiner has level 1 trades");
        check(helper, com.lacuna.mod.merchant.CurrencyService.currency() == ModItems.VILLAGE_COIN.get(), "village coin is the currency");
        helper.succeed();
    }

    @GameTest(template = PLATFORM, batch = "village_school")
    public static void schoolsTeachNearbyVillagers(GameTestHelper helper) {
        helper.setBlock(CENTER.east(3), ModBlocks.SCHOOL_DESK.get());
        var villager = helper.spawn(EntityType.VILLAGER, CENTER.west(2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
        villager.setVillagerXp(1); // xp > 0: the villager keeps its profession without a job site
        BlockPos abs = helper.absolutePos(CENTER);
        var t = VillageService.foundPlayerVillage(helper.getLevel(), abs, null).orElseThrow();
        try {
            Schools.run(helper.getLevel(), t);
            check(helper, villager.getVillagerXp() >= 11, "villager near the desk learned something");
        } finally {
            VillageSavedData.get(helper.getLevel().getServer()).remove(t.key);
        }
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void guildContractsAndCaravanIndex(GameTestHelper helper) {
        var contract = new ItemStack(ModItems.GUILD_CONTRACT.get());
        contract.set(com.lacuna.mod.registry.ModComponents.GUILD_CONTRACT.get(), new ContractData("minecraft:paper", 16, 6, "t", "scholars"));
        var data = GuildContractItem.data(contract);
        check(helper, data.count() == 16 && data.rewardCoins() == 6 && "scholars".equals(data.guild()), "contract component round trip");
        check(helper, Math.abs(CaravanEffect.raise(1.4) - 1.5) < 1e-9, "price index capped at 1.5");
        check(helper, Math.abs(CaravanEffect.decay(1.02) - 1.0) < 1e-9, "price index decays to 1.0");
        var board = ModBlocks.GUILD_BOARD.get().defaultBlockState().setValue(GuildBoardBlock.GUILD, GuildType.GEMCUTTERS);
        helper.setBlock(CENTER, board);
        var poi = PoiTypes.forState(helper.getBlockState(CENTER));
        check(helper, poi.isPresent() && poi.get().is(ModVillagers.GUILDMASTER_POI.getKey()), "guild board is a job site");
        helper.succeed();
    }
}
