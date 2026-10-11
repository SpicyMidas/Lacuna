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
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Null Lantern (spec M6): while held, hostile mobs within 16 blocks cannot target the holder (unless provoked in
 * the last 5 s or within 3 blocks) and natural hostile spawns within 24 blocks are blocked. 1 durability / 200 ticks.
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
        if (now % 20 == 0 && level instanceof ServerLevel server) {
            SafeRun.run(FEATURE, () -> {
                double r = LacunaConfig.get(LacunaConfig.LANTERN_RADIUS);
                for (Mob mob : server.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r), m -> m.getTarget() == player)) {
                    if (mob instanceof Enemy && shouldIgnore(mob, player)) {
                        mob.setTarget(null);
                    }
                }
            });
        }
    }

    private static boolean shouldIgnore(Mob mob, Player player) {
        if (mob.getType().is(ModTags.Entities.CANNOT_BE_BANISHED)) {
            return false; // bosses are not affected
        }
        double dist = mob.distanceTo(player);
        if (dist <= Balance.LANTERN_CLOSE_RANGE || dist > LacunaConfig.get(LacunaConfig.LANTERN_RADIUS)) {
            return false;
        }
        boolean provoked = mob.getLastHurtByMob() == player && mob.tickCount - mob.getLastHurtByMobTimestamp() < Balance.LANTERN_PROVOKE_TICKS;
        return !provoked;
    }

    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player) || !(event.getEntity() instanceof Mob mob)
                || !(mob instanceof Enemy) || player.level().isClientSide() || !active() || !holds(player)) {
            return;
        }
        if (shouldIgnore(mob, player)) {
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
        return repairCandidate.is(ModItems.UMBRAL_IRON_INGOT.get());
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
