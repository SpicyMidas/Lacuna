package com.lacuna.mod.gametest;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.solar.SolarRayEntity;
import com.lacuna.mod.solar.SunsFuryEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sun's Fury revision 2 (spec 11.3 + feedback): the trident-style throw, the harmless focus phase that sweeps the
 * Overworld day time to noon without ever going backwards, and the strike that burns. Platform template as in
 * {@link LacunaGameTests}.
 */
@GameTestHolder(Lacuna.MODID)
@PrefixGameTestTemplate(false)
public final class SolarGameTests {
    /** The platform's stone floor is at relative y = 1 (the structure block sits one layer below the template). */
    static final BlockPos FLOOR = new BlockPos(4, 1, 4);
    static final BlockPos ON_FLOOR = FLOOR.above();

    private SolarGameTests() {}

    private static AABB around(GameTestHelper helper, double radius) {
        return new AABB(helper.absolutePos(ON_FLOOR)).inflate(radius);
    }

    @GameTest(template = LacunaGameTests.PLATFORM)
    public static void sunsFuryThrowsLikeATrident(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(ON_FLOOR);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, -10.0F);
        ItemStack stack = new ItemStack(ModItems.SUNS_FURY.get());
        int duration = stack.getUseDuration(player);

        // released too early: nothing is thrown, nothing is spent
        stack.releaseUsing(level, player, duration - (Balance.SUNS_FURY_MIN_CHARGE - 1));
        List<SunsFuryEntity> none = level.getEntitiesOfClass(SunsFuryEntity.class, around(helper, 8));
        LacunaGameTests.check(helper, none.isEmpty(), "a short tap must not throw");
        LacunaGameTests.check(helper, stack.getCount() == 1, "a short tap must not consume the spear");

        // fully drawn: thrown at the spec speed and consumed (survival)
        stack.releaseUsing(level, player, duration - Balance.SUNS_FURY_FULL_CHARGE - 5);
        List<SunsFuryEntity> thrown = level.getEntitiesOfClass(SunsFuryEntity.class, around(helper, 8));
        try {
            LacunaGameTests.check(helper, thrown.size() == 1, "a full draw throws one spear, got " + thrown.size());
            double speed = thrown.get(0).getDeltaMovement().length();
            LacunaGameTests.check(helper, Math.abs(speed - Balance.SUNS_FURY_SPEED) < 0.35,
                    "full draw speed should be about " + Balance.SUNS_FURY_SPEED + ", got " + speed);
            LacunaGameTests.check(helper, stack.isEmpty(), "the spear is consumed outside Creative");
        } finally {
            thrown.forEach(SunsFuryEntity::discard); // never let a real strike loose on the test grid
        }
        helper.succeed();
    }

    @GameTest(template = LacunaGameTests.PLATFORM, timeoutTicks = 400)
    public static void sunsFuryFocusSweepsToNoonWithoutHarm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // the next midnight ahead (never earlier than now), so the whole test only ever moves time forward
        long now = level.getDayTime();
        long midnight = Math.floorDiv(now, SolarMath.DAY) * SolarMath.DAY + 18000L;
        long start = midnight < now ? midnight + SolarMath.DAY : midnight;
        level.setDayTime(start);
        long target = SolarMath.nextNoon(start);

        // a pig: unlike a zombie it does not burn by itself once the sun is up
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(6, 2, 4));
        float health = pig.getHealth();
        SunsFuryEntity spear = new SunsFuryEntity(ModEntities.SUNS_FURY.get(), level);
        BlockPos at = helper.absolutePos(ON_FLOOR);
        spear.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        spear.setDryRun(true);
        level.addFreshEntity(spear);
        spear.beginFocus(level, new Vec3(0.3, -1.0, 0.2));

        long[] last = {start};
        long[] struck = {-1L};
        helper.onEachTick(() -> {
            long time = level.getDayTime();
            if (time < last[0]) {
                helper.fail("day time went backwards: " + last[0] + " -> " + time);
            }
            last[0] = time;
            if (!spear.isRemoved()) {
                if (pig.getHealth() < health || pig.isOnFire()) {
                    helper.fail("nothing may be harmed during the focus phase");
                }
            } else if (struck[0] < 0) {
                struck[0] = time;
            }
        });
        helper.succeedWhen(() -> {
            LacunaGameTests.check(helper, struck[0] >= 0, "the focus phase is still running");
            // the strike starts at the next noon (the daylight cycle may have added a tick or two since)
            LacunaGameTests.check(helper, struck[0] >= target && struck[0] <= target + 2,
                    "strike at noon " + target + ", day time was " + struck[0] + " (start " + start + ")");
            LacunaGameTests.check(helper, Math.floorDiv(struck[0], SolarMath.DAY) == Math.floorDiv(start, SolarMath.DAY) + 1,
                    "the day count moves on to the next day");
            LacunaGameTests.check(helper, !level.getEntitiesOfClass(SolarRayEntity.class, around(helper, 4)).isEmpty(),
                    "the focus ends in a strike");
            pig.discard();
        });
    }

    @GameTest(template = LacunaGameTests.PLATFORM, timeoutTicks = 100)
    public static void solarStrikeBurnsCreatures(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(6, 2, 4));
        float health = pig.getHealth();
        SolarRayEntity ray = new SolarRayEntity(ModEntities.SOLAR_RAY.get(), level);
        BlockPos at = helper.absolutePos(ON_FLOOR);
        ray.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ray.setSparesBlocks(true);
        BlockPos floorPos = helper.absolutePos(FLOOR);
        BlockState floor = level.getBlockState(floorPos);
        level.addFreshEntity(ray);
        helper.succeedWhen(() -> {
            LacunaGameTests.check(helper, pig.getHealth() < health && pig.isOnFire(), "the strike burns creatures in its radius");
            LacunaGameTests.check(helper, !floor.isAir() && level.getBlockState(floorPos) == floor, "blocks were spared");
            pig.discard();
            ray.discard();
        });
    }
}
