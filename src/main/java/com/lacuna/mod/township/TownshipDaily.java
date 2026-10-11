package com.lacuna.mod.township;

import com.lacuna.mod.logic.CouncilMath;
import com.lacuna.mod.logic.TownshipLaws;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;

/**
 * Once per in-game day at dawn (checked every 100 ticks by the ledger): samples the reputation of nearby players for
 * the council; council, coin value, price index and schools hook in here (M11c/M11d).
 */
public final class TownshipDaily {
    private TownshipDaily() {}

    static void tick(ServerLevel level, Township t) {
        long day = TownshipService.day(level);
        if (t.lastDailyDay == day || !TownshipLaws.isDawn(level.getDayTime())) {
            return;
        }
        t.lastDailyDay = day;
        sampleReputation(level, t);
        t.coinValue = CouncilMath.recover(t.coinValue);
        t.priceIndex = CaravanEffect.decay(t.priceIndex);
        council(level, t, day);
        if (com.lacuna.mod.LacunaConfig.get(com.lacuna.mod.LacunaConfig.TOWNSHIP_GUILDS)) {
            Schools.run(level, t);
        }
        TownshipSavedData.get(level.getServer()).touch();
    }

    /** Every 7 days at dawn the council picks a policy for 7 days, weighted by the week's average reputation. */
    static void council(ServerLevel level, Township t, long day) {
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
        Component msg = Component.translatable("lacuna.township.council", Component.translatable("lacuna.township.policy." + t.policy))
                .withStyle(ChatFormatting.GOLD);
        level.getPlayers(p -> t.contains(level.dimension(), p.blockPosition())).forEach(p -> p.sendSystemMessage(msg));
    }

    /** Every 100 ticks: Martial Law makes the guards hit 50 % harder; Festival lights up the plaza. */
    static void policyEffects(ServerLevel level, Township t) {
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
                double x = t.center.getX() + 0.5 + (r.nextDouble() - 0.5) * 16;
                double z = t.center.getZ() + 0.5 + (r.nextDouble() - 0.5) * 16;
                level.sendParticles(ParticleTypes.FIREWORK, x, t.center.getY() + 4 + r.nextInt(4), z, 24, 0.6, 0.6, 0.6, 0.08);
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, t.center.getX() + 0.5, t.center.getY() + 1.5, t.center.getZ() + 0.5,
                    10, 3, 1, 3, 0.0);
        }
    }

    private static final net.minecraft.resources.ResourceLocation MARTIAL_LAW = com.lacuna.mod.Lacuna.id("martial_law");

    static void sampleReputation(ServerLevel level, Township t) {
        var players = level.getPlayers(p -> t.contains(level.dimension(), p.blockPosition()));
        if (players.isEmpty()) {
            return;
        }
        double avg = players.stream().mapToInt(p -> t.reputation(p.getUUID())).average().orElse(0);
        t.weekRepSum += avg;
        t.weekRepSamples++;
    }
}
