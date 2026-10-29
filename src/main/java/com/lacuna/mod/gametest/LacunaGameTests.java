package com.lacuna.mod.gametest;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.banish.BanishmentManager;
import com.lacuna.mod.beam.BeamHandler;
import com.lacuna.mod.eclipse.EclipserItem;
import com.lacuna.mod.flute.DeathMemory;
import com.lacuna.mod.flute.EchoEntity;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModWoodBlocks;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import com.lacuna.mod.pearl.PearlLink;
import com.lacuna.mod.pearl.PearlLinks;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world GameTests (spec section 4, gate 5). All use the 9x6x9 platform template
 * {@code lacuna:gametest_platform} (stone floor at y=0) produced by tools/gen_structures.py.
 */
@GameTestHolder(Lacuna.MODID)
@PrefixGameTestTemplate(false)
public final class LacunaGameTests {
    public static final String PLATFORM = "gametest_platform";
    static final BlockPos CENTER = new BlockPos(4, 1, 4);

    private LacunaGameTests() {}

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    static List<ItemStack> drops(GameTestHelper helper, BlockPos relative, ItemStack tool) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relative);
        BlockState state = level.getBlockState(abs);
        return Block.getDrops(state, level, abs, level.getBlockEntity(abs), null, tool);
    }

    // ------------------------------------------------------------------ M1
    @GameTest(template = PLATFORM)
    public static void rubyOreDropsRuby(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.RUBY_ORE.get());
        List<ItemStack> result = drops(helper, CENTER, new ItemStack(Items.IRON_PICKAXE));
        check(helper, result.stream().anyMatch(s -> s.is(ModItems.RUBY.get())), "ruby ore should drop a ruby, got " + result);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void divineGlassIsLitAndSeeThrough(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.DIVINE_GLASS.get());
        BlockPos abs = helper.absolutePos(CENTER);
        BlockState state = helper.getLevel().getBlockState(abs);
        check(helper, state.getLightEmission(helper.getLevel(), abs) == 7, "divine glass should emit light 7");
        check(helper, !state.isSuffocating(helper.getLevel(), abs), "divine glass must not suffocate");
        check(helper, !state.isViewBlocking(helper.getLevel(), abs), "divine glass must not block view");
        List<ItemStack> result = drops(helper, CENTER, ItemStack.EMPTY);
        check(helper, result.stream().anyMatch(s -> s.is(ModBlocks.DIVINE_GLASS.get().asItem())), "divine glass drops itself without silk touch");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void divineClusterDropsShards(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.DIVINE_GLASS_CLUSTER.get());
        List<ItemStack> result = drops(helper, CENTER, new ItemStack(Items.IRON_PICKAXE));
        int shards = result.stream().filter(s -> s.is(ModItems.DIVINE_GLASS_SHARD.get())).mapToInt(ItemStack::getCount).sum();
        check(helper, shards >= 2 && shards <= 4, "cluster should drop 2-4 shards, got " + shards);
        helper.succeed();
    }

    // ------------------------------------------------------------------ M2
    @GameTest(template = PLATFORM)
    public static void ruinBlocksAreUnbreakable(GameTestHelper helper) {
        for (Block block : List.of(ModBlocks.RUIN_STONE.get(), ModBlocks.RUIN_STONE_BRICKS.get(), ModBlocks.RUIN_PILLAR.get(),
                ModBlocks.RUIN_WALL.get(), ModBlocks.MURAL_BLOCK_1.get())) {
            helper.setBlock(CENTER, block);
            BlockPos abs = helper.absolutePos(CENTER);
            BlockState state = helper.getLevel().getBlockState(abs);
            check(helper, state.getDestroySpeed(helper.getLevel(), abs) < 0, block + " must be unbreakable");
            check(helper, block.getExplosionResistance() >= 3_600_000.0F, block + " must be blast proof");
            check(helper, state.getPistonPushReaction() == PushReaction.BLOCK, block + " must not be pushable");
        }
        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void banishAndReturnPig(GameTestHelper helper) {
        if (ModDimensions.forgottenLand(helper.getLevel().getServer()) == null) {
            // the GameTest server builds its own fixed dimension set, so datapack dimensions are absent there;
            // the banishment round trip is covered by the dedicated-server smoke test (tools/smoke_phase1.txt)
            Lacuna.LOGGER.warn("banishAndReturnPig: no Forgotten Land in the GameTest world, covered by the smoke test");
            helper.succeed();
            return;
        }
        Pig pig = helper.spawn(EntityType.PIG, CENTER);
        Entity banished = BanishmentManager.banish(pig);
        check(helper, banished != null && ModDimensions.isForgottenLand(banished.level()), "pig should be in the Forgotten Land");
        check(helper, BanishmentManager.recordOf(banished).isPresent(), "a banishment record must exist");
        Entity back = BanishmentManager.returnEntity(banished);
        check(helper, back != null && back.level().dimension() == Level.OVERWORLD, "pig should be back in the overworld");
        check(helper, BanishmentManager.recordOf(back).isEmpty(), "record must be removed after return");
        back.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ M3
    @GameTest(template = PLATFORM)
    public static void beamDamagesTarget(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 4));
        player.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, -90.0F, 0.0F); // looking east (+x)
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 1, 4));
        float before = zombie.getHealth();
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F); // living entities look along their head rotation
        player.setXRot(0.0F);
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), Blocks.AIR);
            helper.setBlock(new BlockPos(x, 2, 4), Blocks.AIR);
        }
        BeamHandler.tick(helper.getLevel(), player, new ItemStack(ModItems.DIVINE_MAGNIFYING_GLASS.get()), 5);
        check(helper, zombie.getHealth() < before, "beam should damage the zombie (" + before + " -> " + zombie.getHealth() + ")");
        zombie.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ M4
    @GameTest(template = PLATFORM)
    public static void eclipserChargesOnHitAndResetsOnDamage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = new ItemStack(ModItems.ECLIPSER.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, CENTER);
        sword.getItem().hurtEnemy(sword, zombie, player);
        check(helper, EclipserItem.charge(sword) == 10, "one hit should give 10 charge, got " + EclipserItem.charge(sword));
        sword.getItem().hurtEnemy(sword, zombie, player);
        check(helper, EclipserItem.charge(sword) == 10, "a second hit in the same tick must not add charge");
        EclipserItem.resetOnDamage(player);
        check(helper, EclipserItem.charge(sword) == 0, "taking damage resets the charge");
        zombie.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ M5 (spec-required pearl tests)
    private static PearlLink lockPair(GameTestHelper helper, BlockPos floorRelative) {
        ItemStack[] pair = PearlLinks.split();
        PearlLink link = PearlLinks.link(pair[0]);
        helper.setBlock(floorRelative, Blocks.STONE);
        helper.setBlock(floorRelative.above(), Blocks.AIR);
        boolean locked = PearlLinks.lock(helper.getLevel(), helper.absolutePos(floorRelative), Direction.UP, pair[0], null);
        check(helper, locked, "arrival shard should lock on top of the floor");
        check(helper, link != null && link.equals(PearlLinks.link(pair[1])), "both halves share the link id");
        return link;
    }

    @GameTest(template = PLATFORM)
    public static void pearlLinkRoundTrip(GameTestHelper helper) {
        PearlLink link = lockPair(helper, new BlockPos(2, 0, 2));
        var target = PearlLinks.resolve(helper.getLevel().getServer(), Level.OVERWORLD, link);
        check(helper, target.isPresent() && target.get().pos().equals(helper.absolutePos(new BlockPos(2, 1, 2))),
                "departure must resolve to the anchor (any holder: links are not bound to a player)");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void pearlPairsDoNotInterfere(GameTestHelper helper) {
        PearlLink a = lockPair(helper, new BlockPos(1, 0, 1));
        PearlLink b = lockPair(helper, new BlockPos(6, 0, 6));
        var ta = PearlLinks.resolve(helper.getLevel().getServer(), Level.OVERWORLD, a);
        var tb = PearlLinks.resolve(helper.getLevel().getServer(), Level.OVERWORLD, b);
        check(helper, ta.isPresent() && tb.isPresent() && !ta.get().pos().equals(tb.get().pos()), "each pair keeps its own anchor");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void pearlBrokenAnchorInvalidates(GameTestHelper helper) {
        PearlLink link = lockPair(helper, new BlockPos(3, 0, 3));
        helper.getLevel().removeBlock(helper.absolutePos(new BlockPos(3, 1, 3)), false);
        check(helper, PearlLinks.resolve(helper.getLevel().getServer(), Level.OVERWORLD, link).isEmpty(),
                "a destroyed anchor must not resolve");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void pearlCrossDimensionRules(GameTestHelper helper) {
        PearlLink link = lockPair(helper, new BlockPos(4, 0, 4));
        check(helper, PearlLinks.resolve(helper.getLevel().getServer(), Level.NETHER, link).isPresent(), "travel across dimensions works");
        check(helper, PearlLinks.resolve(helper.getLevel().getServer(), ModDimensions.FORGOTTEN_LAND, link).isEmpty(),
                "no Forgotten Land crossings");
        helper.succeed();
    }

    // ------------------------------------------------------------------ M6
    @GameTest(template = PLATFORM)
    public static void fluteRemembersRecentDeaths(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, CENTER);
        zombie.kill();
        helper.runAfterDelay(2, () -> {
            var deaths = DeathMemory.take(helper.getLevel(), net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(CENTER)), 3);
            check(helper, deaths.size() == 1, "one recent death expected, got " + deaths.size());
            EchoEntity echo = ModEntities.ECHO.get().create(helper.getLevel());
            check(helper, echo != null, "echo entity type must create an instance");
            echo.setup(null, deaths.get(0).maxHealth(), deaths.get(0).attack());
            check(helper, echo.getMaxHealth() <= 20.0F && echo.getMaxHealth() >= 1.0F, "echo health is capped at 20");
            echo.discard();
            check(helper, DeathMemory.take(helper.getLevel(), net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(CENTER)), 3).isEmpty(),
                    "a death can only be called once");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ M7
    @GameTest(template = PLATFORM)
    public static void specialWeaponsAcceptOnlyUnbreaking(GameTestHelper helper) {
        var enchantments = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var unbreaking = enchantments.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
        var sharpness = enchantments.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
        for (var item : List.of(ModItems.ECLIPSER.get(), ModItems.GRAVEMAW_GREATSWORD.get(), ModItems.TIDECALLER_SICKLE.get(),
                ModItems.HEARTWOOD_BOW.get(), ModItems.ECHO_DAGGER.get(), ModItems.PYRRHIC_SCEPTER.get())) {
            ItemStack stack = new ItemStack(item);
            check(helper, stack.supportsEnchantment(unbreaking), item + " must accept Unbreaking");
            check(helper, !stack.supportsEnchantment(sharpness), item + " must reject Sharpness");
        }
        check(helper, new ItemStack(ModItems.GRAVEMAW_GREATSWORD.get()).getMaxDamage() == 700, "Gravemaw durability 700");
        check(helper, new ItemStack(ModItems.ECLIPSER.get()).getMaxDamage() == 250, "Eclipser durability 250");
        helper.succeed();
    }

    // ------------------------------------------------------------------ M8 (one stripping check per family)
    private static void checkStrip(GameTestHelper helper, DeferredBlock<? extends Block> from, DeferredBlock<? extends Block> to) {
        var strip = from.getData(NeoForgeDataMaps.STRIPPABLES);
        check(helper, strip != null && strip.strippedBlock() == to.get(), from.getId() + " must strip to " + to.getId());
    }

    @GameTest(template = PLATFORM)
    public static void woodFamiliesStripWithAxe(GameTestHelper helper) {
        checkStrip(helper, ModWoodBlocks.CINDERVINE_LOG, ModWoodBlocks.STRIPPED_CINDERVINE_LOG);
        checkStrip(helper, ModWoodBlocks.CINDERVINE_WOOD, ModWoodBlocks.STRIPPED_CINDERVINE_WOOD);
        checkStrip(helper, ModWoodBlocks.CRYSTAL_WILLOW_LOG, ModWoodBlocks.STRIPPED_CRYSTAL_WILLOW_LOG);
        checkStrip(helper, ModWoodBlocks.CRYSTAL_WILLOW_WOOD, ModWoodBlocks.STRIPPED_CRYSTAL_WILLOW_WOOD);
        checkStrip(helper, ModWoodBlocks.TIDEBLOOM_LOG, ModWoodBlocks.STRIPPED_TIDEBLOOM_LOG);
        checkStrip(helper, ModWoodBlocks.TIDEBLOOM_WOOD, ModWoodBlocks.STRIPPED_TIDEBLOOM_WOOD);
        checkStrip(helper, ModWoodBlocks.ECHO_BIRCH_LOG, ModWoodBlocks.STRIPPED_ECHO_BIRCH_LOG);
        checkStrip(helper, ModWoodBlocks.ECHO_BIRCH_WOOD, ModWoodBlocks.STRIPPED_ECHO_BIRCH_WOOD);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void woodLeavesArePersistentWhenPlaced(GameTestHelper helper) {
        BlockState placed = ModWoodBlocks.ECHO_BIRCH_LEAVES.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        helper.setBlock(CENTER, placed);
        check(helper, helper.getBlockState(CENTER).getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT), "persistent leaves stay");
        check(helper, new ItemStack(ModWoodBlocks.CINDERVINE_PLANKS.get()).getBurnTime(null) == 400, "cindervine planks burn 400 ticks");
        helper.succeed();
    }

    // ------------------------------------------------------------------ M9
    @GameTest(template = PLATFORM)
    public static void mobsHaveSpecAttributes(GameTestHelper helper) {
        var moth = helper.spawn(ModEntities.LANTERN_MOTH.get(), CENTER);
        check(helper, moth.getMaxHealth() == 6.0F, "lantern moth has 6 HP");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void silkDustGlowSchedulesItsRemoval(GameTestHelper helper) {
        BlockState glow = ModBlocks.SILK_DUST_GLOW.get().defaultBlockState();
        helper.setBlock(CENTER, glow);
        BlockPos abs = helper.absolutePos(CENTER);
        ServerLevel level = helper.getLevel();
        check(helper, level.getBlockState(abs).getLightEmission(level, abs) == 9, "glow emits light 9");
        check(helper, level.getBlockTicks().hasScheduledTick(abs, ModBlocks.SILK_DUST_GLOW.get()), "removal tick scheduled");
        check(helper, level.getBlockState(abs).getCollisionShape(level, abs).isEmpty(), "glow has no collision");
        level.getBlockState(abs).tick(level, abs, level.getRandom());
        check(helper, level.getBlockState(abs).isAir(), "glow removes itself on its tick");
        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void decoyLuresIdleMonsters(GameTestHelper helper) {
        var zombie = helper.spawn(EntityType.ZOMBIE, CENTER.east(3));
        var decoy = helper.spawn(ModEntities.DECOY.get(), CENTER);
        helper.succeedWhen(() -> check(helper, zombie.getTarget() == decoy, "zombie should target the decoy"));
    }

    // ------------------------------------------------------------------ M10a Forgotten Land biome blocks
    @GameTest(template = PLATFORM)
    public static void ashenSnowFootprintsFade(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.ASHEN_SNOW_BLOCK.get());
        BlockPos abs = helper.absolutePos(CENTER);
        ServerLevel level = helper.getLevel();
        var pig = helper.spawn(EntityType.PIG, CENTER.above());
        level.getBlockState(abs).getBlock().stepOn(level, abs, level.getBlockState(abs), pig);
        check(helper, level.getBlockState(abs).getValue(com.lacuna.mod.fl.AshenSnowBlock.FOOTPRINT) == 3, "fresh footprint");
        check(helper, level.getBlockTicks().hasScheduledTick(abs, ModBlocks.ASHEN_SNOW_BLOCK.get()), "fade tick scheduled");
        for (int i = 0; i < 3; i++) {
            level.getBlockState(abs).tick(level, abs, level.getRandom());
        }
        check(helper, level.getBlockState(abs).getValue(com.lacuna.mod.fl.AshenSnowBlock.FOOTPRINT) == 0, "footprint faded");
        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void updraftSpringLiftsMobs(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.UPDRAFT_SPRING.get());
        var pig = helper.spawn(EntityType.PIG, CENTER);
        double startY = pig.getY();
        helper.succeedWhen(() -> check(helper, pig.getY() > startY + 2.5, "pig should rise in the updraft"));
    }

    @GameTest(template = PLATFORM)
    public static void sporeMushroomRevealsHiddenMobs(GameTestHelper helper) {
        helper.setBlock(CENTER.below(), Blocks.MOSS_BLOCK);
        helper.setBlock(CENTER, ModBlocks.SPORE_MUSHROOM.get());
        var spider = helper.spawn(EntityType.SPIDER, CENTER.east(3));
        int n = com.lacuna.mod.fl.SporeMushroomBlock.revealAround(helper.getLevel(), helper.absolutePos(CENTER));
        check(helper, n >= 1 && spider.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "spider should glow");
        check(helper, helper.getLevel().getBlockState(helper.absolutePos(CENTER)).getLightEmission(helper.getLevel(),
                helper.absolutePos(CENTER)) == 7, "spore mushroom emits light 7");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void buriedWatchDropsChronalShards(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.BURIED_WATCH.get());
        List<ItemStack> result = drops(helper, CENTER, new ItemStack(Items.IRON_PICKAXE));
        check(helper, result.stream().anyMatch(st -> st.is(ModItems.CHRONAL_SHARD.get()) && st.getCount() >= 1 && st.getCount() <= 2),
                "buried watch drops 1-2 chronal shards");
        helper.succeed();
    }

    // ------------------------------------------------------------------ M10b Sunken Bell, Obelisk
    @GameTest(template = PLATFORM)
    public static void sunkenBellScanFindsNearbyOres(GameTestHelper helper) {
        helper.setBlock(CENTER.east(3), Blocks.IRON_ORE);
        helper.setBlock(CENTER.west(2).above(), ModBlocks.RUBY_ORE.get());
        BlockPos center = helper.absolutePos(CENTER);
        var found = com.lacuna.mod.structure.OreScan.scan(helper.getLevel(), center, 4, 64); // small radius: neighbouring tests may hold ores too
        check(helper, found.contains(helper.absolutePos(CENTER.east(3))), "iron ore found");
        check(helper, found.contains(helper.absolutePos(CENTER.west(2).above())), "ruby ore found (c:ores)");
        check(helper, found.size() <= 64, "at most 64 positions");
        var limited = com.lacuna.mod.structure.OreScan.scan(helper.getLevel(), center, 4, 1);
        check(helper, limited.size() == 1 && limited.get(0).equals(helper.absolutePos(CENTER.west(2).above())), "nearest first");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void obeliskRewardsOncePerPlayer(GameTestHelper helper) {
        var data = com.lacuna.mod.structure.ObeliskSavedData.get(helper.getLevel().getServer());
        java.util.UUID player = java.util.UUID.randomUUID();
        String key = "lacuna:gametest|" + helper.absolutePos(CENTER).asLong();
        check(helper, !data.claimed(player, key), "not claimed yet");
        data.reward(player, key, helper.getLevel().getGameTime() + 24000);
        check(helper, data.claimed(player, key), "claimed after reward");
        check(helper, data.cooldownUntil(key) > helper.getLevel().getGameTime(), "obelisk cools down");
        helper.setBlock(CENTER, ModBlocks.OBELISK_INSCRIPTION.get());
        check(helper, helper.getLevel().getBlockState(helper.absolutePos(CENTER)).getDestroySpeed(helper.getLevel(),
                helper.absolutePos(CENTER)) < 0, "inscription is unbreakable in survival");
        helper.succeed();
    }

    // ------------------------------------------------------------------ M10c Mirror Labyrinth
    @GameTest(template = PLATFORM)
    public static void defeatedReflectionOpensTheLabyrinth(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.LABYRINTH_TRIGGER.get());
        helper.setBlock(CENTER.east(2), ModBlocks.LABYRINTH_EXIT.get());
        helper.setBlock(CENTER.east(2).above(), ModBlocks.LABYRINTH_EXIT.get());
        helper.setBlock(CENTER.west(2), ModBlocks.MIRROR_GLASS.get());
        com.lacuna.mod.structure.LabyrinthHandler.onReflectionGone(helper.getLevel(), helper.absolutePos(CENTER), true);
        helper.assertBlockPresent(Blocks.AIR, CENTER.east(2));
        helper.assertBlockPresent(Blocks.AIR, CENTER.east(2).above());
        helper.assertBlockPresent(ModBlocks.MIRROR_GLASS.get(), CENTER.west(2));
        check(helper, helper.getBlockState(CENTER).getValue(com.lacuna.mod.structure.LabyrinthTriggerBlock.SOLVED), "trigger solved");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void lostReflectionKeepsTheLabyrinthSealed(GameTestHelper helper) {
        helper.setBlock(CENTER, ModBlocks.LABYRINTH_TRIGGER.get());
        helper.setBlock(CENTER.east(2), ModBlocks.LABYRINTH_EXIT.get());
        com.lacuna.mod.structure.LabyrinthHandler.onReflectionGone(helper.getLevel(), helper.absolutePos(CENTER), false);
        helper.assertBlockPresent(ModBlocks.LABYRINTH_EXIT.get(), CENTER.east(2));
        check(helper, !helper.getBlockState(CENTER).getValue(com.lacuna.mod.structure.LabyrinthTriggerBlock.SOLVED), "still armed");
        helper.succeed();
    }

    // ------------------------------------------------------------------ M10d Last Merchant
    @GameTest(template = PLATFORM)
    public static void lastMerchantTradesMinedMaterials(GameTestHelper helper) {
        var merchant = helper.spawn(ModEntities.LAST_MERCHANT.get(), CENTER);
        var offers = merchant.getOffers();
        check(helper, offers.size() >= 9, "merchant should stock at least 9 offers, has " + offers.size());
        var currency = ModItems.RUBY.get(); // the Last Merchant deals in rubies only
        for (var good : List.of(ModItems.DIVINE_GLASS_SHARD.get(), ModItems.CHRONAL_SHARD.get(), Items.ECHO_SHARD, ModItems.SUNS_FURY.get())) {
            check(helper, offers.stream().anyMatch(o -> o.getResult().is(good) && o.getBaseCostA().is(currency)),
                    "sells " + good + " for rubies");
        }
        check(helper, offers.stream().anyMatch(o -> o.getResult().is(currency)), "buys materials for rubies");
        helper.succeed();
    }

    // ------------------------------------------------------------------ M12 polish
    @GameTest(template = PLATFORM)
    public static void creativeTabIsCuratedAndComplete(GameTestHelper helper) {
        var ordered = new java.util.ArrayList<>(com.lacuna.mod.registry.ModCreativeTabs.ordered());
        check(helper, ordered.get(0) == ModItems.ALMANAC.get() && ordered.get(1) == ModItems.TOTEM_OF_BANISHING.get(),
                "the Almanac, then the relics come first");
        for (var holder : com.lacuna.mod.registry.ModRegistries.ITEMS.getEntries()) {
            boolean technical = com.lacuna.mod.registry.ModCreativeTabs.technical(holder.get());
            check(helper, ordered.contains(holder.get()) != technical, holder.getId() + (technical ? " is technical but in" : " missing from")
                    + " the creative tab");
        }
        var advancements = helper.getLevel().getServer().getAdvancements();
        for (String key : List.of("root", "enter_forgotten_land", "survive_banishment", "forge_eclipser", "throw_suns_fury",
                "link_pearl", "village_friend")) {
            check(helper, advancements.get(com.lacuna.mod.Lacuna.id("lacuna/" + key)) != null, "advancement lacuna/" + key + " loaded");
        }
        helper.succeed();
    }
}
