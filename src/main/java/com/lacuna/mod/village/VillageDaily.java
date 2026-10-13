package com.lacuna.mod.village;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.CouncilMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;

/**
 * The village's daily routine (spec 18.3/18.4): runs once per in-game day for every village a player is in — at dawn
 * when someone is there, otherwise as soon as a player arrives (missed days are caught up, at most 30). Samples the
 * reputation of the players present for the council, recovers the coin value and the price index, convenes the
 * council every 7 days and lets the schools teach.
 */
public final class VillageDaily {
    private static final int MAX_CATCH_UP_DAYS = 30;
    private static final ResourceLocation MARTIAL_LAW = com.lacuna.mod.Lacuna.id("martial_law");

    private VillageDaily() {}

    static void tick(ServerLevel level, VillageRecord t) {
        long day = VillageService.day(level);
        if (t.lastDailyDay >= day) {
            return;
        }
        long missed = t.lastDailyDay < 0 ? 1 : Math.min(MAX_CATCH_UP_DAYS, day - t.lastDailyDay);
        t.lastDailyDay = day;
        sampleReputation(level, t);
        for (long i = 0; i < missed; i++) {
            t.coinValue = CouncilMath.recover(t.coinValue);
            t.priceIndex = CaravanEffect.decay(t.priceIndex);
        }
        if (LacunaConfig.get(LacunaConfig.VILLAGES_COUNCIL)) {
            council(level, t, day);
        }
        if (LacunaConfig.get(LacunaConfig.VILLAGES_GUILDS)) {
            Schools.run(level, t);
        }
        VillageSavedData.get(level.getServer()).touch();
    }

    /** Every 7 days the council picks a policy for 7 days, weighted by the week's average reputation. */
    static void council(ServerLevel level, VillageRecord t, long day) {
        if (t.nextCouncilDay < 0) {
            t.nextCouncilDay = day + CouncilMath.TERM_DAYS;
            return;
        }
        if (day < t.nextCouncilDay) {
            return;
        }
        double avg = t.weekRepSamples > 0 ? t.weekRepSum / t.weekRepSamples : 0.0;
        t.policy = CouncilMath.choose(avg, new java.util.Random(t.seed ^ day * 31L));
        t.policyEndDay = day + CouncilMath.TERM_DAYS;
        t.nextCouncilDay = day + CouncilMath.TERM_DAYS;
        t.weekRepSum = 0;
        t.weekRepSamples = 0;
        Component msg = Component.translatable("lacuna.village.council", VillageService.villageName(t),
                Component.translatable("lacuna.village.policy." + t.policy)).withStyle(ChatFormatting.GOLD);
        level.getPlayers(p -> t.contains(level.dimension(), p.blockPosition())).forEach(p -> p.sendSystemMessage(msg));
    }

    /** Every 5 s: Martial Law makes the village golems hit 50 % harder; Festival lights up the plaza. */
    static void policyEffects(ServerLevel level, VillageRecord t) {
        boolean martial = "martial_law".equals(t.policy);
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, new AABB(t.center).inflate(t.radius, 24, t.radius),
                g -> !g.isPlayerCreated())) {
            AttributeInstance attack = golem.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack == null) {
                continue;
            }
            if (martial && !attack.hasModifier(MARTIAL_LAW)) {
                attack.addTransientModifier(new AttributeModifier(MARTIAL_LAW, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else if (!martial && attack.hasModifier(MARTIAL_LAW)) {
                attack.removeModifier(MARTIAL_LAW);
            }
        }
        if ("festival".equals(t.policy)) {
            var r = level.getRandom();
            for (int i = 0; i < 3; i++) {
                double x = t.plaza.getX() + 0.5 + (r.nextDouble() - 0.5) * 16;
                double z = t.plaza.getZ() + 0.5 + (r.nextDouble() - 0.5) * 16;
                level.sendParticles(ParticleTypes.FIREWORK, x, t.plaza.getY() + 6 + r.nextInt(4), z, 24, 0.6, 0.6, 0.6, 0.08);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, t.plaza.getX() + 0.5, t.plaza.getY() + 1.5, t.plaza.getZ() + 0.5,
                    10, 3, 1, 3, 0.0);
        }
    }

    static void sampleReputation(ServerLevel level, VillageRecord t) {
        var players = level.getPlayers(p -> t.contains(level.dimension(), p.blockPosition()));
        if (players.isEmpty()) {
            return;
        }
        double avg = players.stream().mapToInt(p -> t.reputation(p.getUUID())).average().orElse(0);
        t.weekRepSum += avg;
        t.weekRepSamples++;
    }
}
