package com.lacuna.mod.lantern;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Null Lantern (spec M6, revised): while held in either hand, hostile and angered neutral mobs cannot target or hurt
 * the holder at any distance, even when they were already attacking or the holder strikes them: mobs hunting the
 * holder forget them at once (brain memories and anger included, creepers stop swelling, charging attacks are
 * cancelled) and natural hostile spawns within 24 blocks are blocked. Bosses ({@code lacuna:cannot_be_banished})
 * are unaffected. 1 durability / 200 ticks.
 */
public class NullLanternItem extends Item {
    public static final String FEATURE = "null_lantern";

    public NullLanternItem(Properties properties) {
        super(properties);
    }

    public static boolean holds(Player player) {
        return player.getMainHandItem().getItem() instanceof NullLanternItem || player.getOffhandItem().getItem() instanceof NullLanternItem;
    }

    private static boolean active() {
        return LacunaConfig.get(LacunaConfig.LANTERN_ENABLED) && !SafeRun.isDisabled(FEATURE);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof Player player) || !active()) {
            return;
        }
        InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND
                : player.getOffhandItem() == stack ? InteractionHand.OFF_HAND : null;
        if (hand == null) {
            return;
        }
        long now = level.getGameTime();
        if (now % Balance.LANTERN_TICKS_PER_DURABILITY == 0) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        if (now % 100 == 0) {
            com.lacuna.mod.util.Sfx.at(player, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, 0.25F, 0.5F);
        }
        if (level instanceof ServerLevel server && now % 5 != 0) {
            // between the wide sweeps, mobs right next to the holder are checked every tick
            SafeRun.run(FEATURE, () -> {
                for (Mob mob : server.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6.0), m -> affects(m, player))) {
                    forget(mob, player);
                }
            });
        }
        if (now % 5 == 0 && level instanceof ServerLevel server) {
            SafeRun.run(FEATURE, () -> {
                double r = Math.max(32.0, LacunaConfig.get(LacunaConfig.LANTERN_RADIUS));
                for (Mob mob : server.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r), m -> affects(m, player))) {
                    forget(mob, player);
                }
                if (now % 10 == 0) {
                    // a faint veil of shadow around the holder, so everyone can tell the lantern is working
                    server.sendParticles(com.lacuna.mod.registry.ModParticles.SHADOW_MOTE.get(), player.getX(), player.getY() + 1.0,
                            player.getZ(), 2, 0.45, 0.6, 0.45, 0.005);
                }
            });
        }
    }

    /** Hostile mobs and neutral mobs that can turn on a player (golems keep guarding villages); never bosses. */
    private static boolean isThreat(Mob mob) {
        if (mob.getType().is(ModTags.Entities.CANNOT_BE_BANISHED) || mob instanceof net.minecraft.world.entity.animal.AbstractGolem) {
            return false;
        }
        return mob instanceof Enemy || mob instanceof NeutralMob;
    }

    /** True when {@code mob} must leave {@code player} alone right now: always, for every threat, provoked or not. */
    private static boolean shouldIgnore(Mob mob, Player player) {
        return isThreat(mob);
    }

    private static boolean affects(Mob mob, Player player) {
        if (!shouldIgnore(mob, player)) {
            return false;
        }
        if (mob.getTarget() == player) {
            return true;
        }
        if (mob instanceof NeutralMob neutral && player.getUUID().equals(neutral.getPersistentAngerTarget())) {
            return true;
        }
        return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(t -> t == player).isPresent()
                || mob.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(id -> id.equals(player.getUUID())).isPresent();
    }

    /** Drops every way {@code mob} could be hunting {@code player}, with a cold whisper and a puff of shadow. */
    private static void forget(Mob mob, Player player) {
        if (mob.level() instanceof ServerLevel server) {
            server.sendParticles(com.lacuna.mod.registry.ModParticles.SHADOW_MOTE.get(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.7,
                    mob.getZ(), 8, 0.3, 0.3, 0.3, 0.01);
            com.lacuna.mod.util.Sfx.varied(mob, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.7F, 0.1F);
            com.lacuna.mod.util.Sfx.at(mob, net.minecraft.sounds.SoundEvents.SCULK_CLICKING_STOP, 0.6F, 0.6F);
        }
        if (mob.getTarget() == player) {
            mob.setTarget(null);
        }
        if (mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(t -> t == player).isPresent()) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        if (mob.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(id -> id.equals(player.getUUID())).isPresent()) {
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        }
        if (mob instanceof NeutralMob neutral && player.getUUID().equals(neutral.getPersistentAngerTarget())) {
            neutral.stopBeingAngry();
        }
        if (mob instanceof Creeper creeper && creeper.getSwellDir() > 0) {
            creeper.setSwellDir(-1);
        }
        if (mob.getLastHurtByMob() == player) {
            mob.setLastHurtByMob(null); // HurtByTargetGoal would pick the holder right back up
        }
        if (mob.isUsingItem()) {
            mob.stopUsingItem(); // a skeleton's drawn bow or a crossbow being loaded at the holder
        }
        mob.setAggressive(false);
    }

    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player) || !(event.getEntity() instanceof Mob mob)
                || player.level().isClientSide() || !active() || !holds(player)) {
            return;
        }
        if (shouldIgnore(mob, player)) {
            event.setCanceled(true);
        }
    }

    /** Contact and melee damage (slimes, magma cubes, stray shots...) from unprovoked threats is cancelled too. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || !active() || !holds(player)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof Mob mob && shouldIgnore(mob, player)) {
            event.setCanceled(true);
        }
    }

    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !active()) {
            return;
        }
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        double r = Balance.LANTERN_SPAWN_RADIUS;
        for (Player player : event.getLevel().players()) {
            if (player.distanceToSqr(event.getX(), event.getY(), event.getZ()) <= r * r && holds(player)) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
                return;
            }
        }
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(ModItems.UMBRAL_STEEL_INGOT.get());
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.null_lantern.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.null_lantern.use").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
