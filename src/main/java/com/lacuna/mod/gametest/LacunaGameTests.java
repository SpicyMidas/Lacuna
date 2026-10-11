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
            helper.fail("forgotten land dimension missing");
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
    public static void pearlRefusesOtherDimensions(GameTestHelper helper) {
        PearlLink link = lockPair(helper, new BlockPos(4, 0, 4));
        check(helper, PearlLinks.resolve(helper.getLevel().getServer(), Level.NETHER, link).isEmpty(), "no cross-dimension travel");
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
        var crawler = helper.spawn(ModEntities.GLOOMCRAWLER.get(), CENTER.east(2));
        var hound = helper.spawn(ModEntities.CLOCKWORK_HOUND.get(), CENTER.west(2));
        check(helper, moth.getMaxHealth() == 6.0F, "lantern moth has 6 HP");
        check(helper, crawler.getMaxHealth() == 22.0F, "gloomcrawler has 22 HP");
        check(helper, crawler.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 4.0, "gloomcrawler deals 4");
        check(helper, hound.getMaxHealth() == 30.0F, "clockwork hound has 30 HP");
        check(helper, hound.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 5.0, "hound deals 5");
        check(helper, Math.abs(hound.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - 0.33) < 1e-6, "hound speed 0.33");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void houndCannotBeTamedOrFed(GameTestHelper helper) {
        var hound = helper.spawn(ModEntities.CLOCKWORK_HOUND.get(), CENTER);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 64));
        for (int i = 0; i < 20; i++) {
            hound.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        check(helper, !hound.isTame(), "hound must never be tamed");
        check(helper, !hound.isFood(new ItemStack(Items.BEEF)), "hound has no food");
        check(helper, hound.getMaxHealth() == 30.0F, "taming side effects must not change health");
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

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void gloomcrawlerCrouchesInCover(GameTestHelper helper) {
        var crawler = helper.spawn(ModEntities.GLOOMCRAWLER.get(), CENTER);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CENTER.relative(d), leaves);
        }
        helper.succeedWhen(() -> check(helper, crawler.isCrouched() && crawler.getBbHeight() < 0.5F, "gloomcrawler should crouch next to leaves"));
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
        var crawler = helper.spawn(ModEntities.GLOOMCRAWLER.get(), CENTER.east(3));
        int n = com.lacuna.mod.fl.SporeMushroomBlock.revealAround(helper.getLevel(), helper.absolutePos(CENTER));
        check(helper, n >= 1 && crawler.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "gloomcrawler should glow");
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
}
