package com.lacuna.mod.gametest;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.banish.BanishmentManager;
import com.lacuna.mod.beam.BeamHandler;
import com.lacuna.mod.eclipse.EclipserItem;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.Block;
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
}
