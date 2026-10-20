package com.lacuna.mod.chrono;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModParticles;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * The living side of the Chrono Hourglass's sped-up time (players and mobs in reach, once per tick each, whatever the
 * number of overlapping hourglasses).
 * <ul>
 *   <li>Players, {@link Balance#HOURGLASS_PLAYER_SPEED} times: natural regeneration runs extra food ticks while it is
 *   actually healing (hurt, food at 18 or more, {@code naturalRegeneration} on), so hearts come back twice as fast and
 *   cost the same exhaustion per heart as vanilla, never more than vanilla's own pace per extra tick. Harmful effects
 *   run their course faster (poison and wither deal the same total damage, only sooner); beneficial effects are left
 *   alone so they never wear off early. Starvation, the hunger bar and everything else stay at normal speed.</li>
 *   <li>Mobs, {@link Balance#HOURGLASS_SPEED} times (applied once a second): babies and tadpoles grow up, breeding
 *   cooldowns run out, chickens lay eggs and armadillos shed scutes sooner. Item despawn, conversions (zombie villagers,
 *   drowned, strays), raids and villager trades keep their pace.</li>
 * </ul>
 */
final class LivingHaste {
    private static final IntOpenHashSet PLAYERS = new IntOpenHashSet();
    private static final IntOpenHashSet MOBS = new IntOpenHashSet();

    private LivingHaste() {
    }

    /** A new game tick (or level) started: every player and mob may be sped up once again. */
    static void newTick() {
        PLAYERS.clear();
        MOBS.clear();
    }

    // ------------------------------------------------------------------ players

    static void players(ServerLevel level, AABB box, long now) {
        int extra = Balance.HOURGLASS_PLAYER_SPEED - 1;
        if (extra <= 0) {
            return;
        }
        boolean regen = level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative() || !box.contains(player.position())
                    || !PLAYERS.add(player.getId())) {
                continue;
            }
            if (regen) {
                heal(level, player, extra);
            }
            shakeOff(player, extra, now);
        }
    }

    /** Extra food ticks, only while they lead to healing (vanilla's two regeneration branches of {@link FoodData#tick}). */
    private static void heal(ServerLevel level, ServerPlayer player, int extra) {
        FoodData food = player.getFoodData();
        for (int i = 0; i < extra; i++) {
            if (!player.isHurt() || food.getFoodLevel() < 18) {
                return;
            }
            float before = player.getHealth();
            food.tick(player);
            if (player.getHealth() > before && level.getRandom().nextInt(3) == 0) {
                level.sendParticles(ModParticles.CHRONO_TICK.get(), player.getX(), player.getY() + player.getBbHeight() * 0.6,
                        player.getZ(), 2, 0.3, 0.35, 0.3, 0.004);
            }
        }
    }

    /**
     * Harmful effects tick down (and apply) extra times. An effect is never taken below one tick here: vanilla's own tick
     * expires it, so hidden weaker effects and removal events behave exactly as usual. The client is resynced once a second.
     */
    private static void shakeOff(ServerPlayer player, int extra, long now) {
        List<MobEffectInstance> harmful = null;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !effect.isInfiniteDuration()
                    && effect.getDuration() > 1) {
                if (harmful == null) {
                    harmful = new ArrayList<>(2);
                }
                harmful.add(effect);
            }
        }
        if (harmful == null) {
            return;
        }
        boolean sync = now % 20 == 0;
        for (MobEffectInstance effect : harmful) {
            for (int i = 0; i < extra && effect.getDuration() > 1 && player.getEffect(effect.getEffect()) == effect; i++) {
                effect.tick(player, () -> {
                });
            }
            if (sync && player.getEffect(effect.getEffect()) == effect) {
                player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effect, false));
            }
        }
        if (sync && player.getRandom().nextInt(3) == 0) {
            ((ServerLevel) player.level()).sendParticles(ModParticles.CHRONO_TICK.get(), player.getX(), player.getY() + 1.0,
                    player.getZ(), 1, 0.3, 0.4, 0.3, 0.004);
        }
    }

    // ------------------------------------------------------------------ mobs

    /** Called once a second: {@code ticks} extra ticks of every mob timer that is safe to hurry. */
    static void mobs(ServerLevel level, AABB box, int ticks) {
        RandomSource random = level.getRandom();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Entity::isAlive)) {
            if (!MOBS.add(mob.getId())) {
                continue;
            }
            boolean growing = false;
            boolean timer = false;
            if (mob instanceof AgeableMob ageable) {
                int age = ageable.getAge();
                if (age != 0) {
                    ageable.setAge(age < 0 ? Math.min(0, age + ticks) : Math.max(0, age - ticks));
                    growing = age < 0;
                }
            } else if (mob instanceof Tadpole tadpole) {
                tadpole.ageUp(Math.max(1, ticks / 20)); // seconds; turns into a frog when grown
                growing = true;
            }
            if (mob instanceof Chicken chicken && !chicken.isBaby() && chicken.eggTime > 1) {
                chicken.eggTime = Math.max(1, chicken.eggTime - ticks);
                timer = true;
            } else if (mob instanceof Armadillo armadillo && !armadillo.isBaby() && armadillo.scuteTime > 1) {
                armadillo.scuteTime = Math.max(1, armadillo.scuteTime - ticks);
                timer = true;
            }
            if (growing && random.nextInt(3) == 0 || timer && random.nextInt(6) == 0) {
                level.sendParticles(ModParticles.CHRONO_TICK.get(), mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(),
                        1, 0.2, 0.1, 0.2, 0.004);
            }
        }
    }
}
