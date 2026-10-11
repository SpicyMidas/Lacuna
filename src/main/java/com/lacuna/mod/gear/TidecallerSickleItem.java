package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.registry.ModTags;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tidecaller Sickle (spec M7): right click hooks the first living non-boss entity within 12 blocks and pulls it in
 * (3 damage, 2 durability, cooldown 100). Hooking while in water or rain stores a Tide Charge (max 3); each charge
 * heals 2 HP on the next melee hit.
 */
public class TidecallerSickleItem extends SwordItem {
    public TidecallerSickleItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static int tide(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TIDE_CHARGES.get(), 0);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide() || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(Balance.SICKLE_RANGE));
        HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, stop,
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(Balance.SICKLE_RANGE)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !e.getType().is(ModTags.Entities.CANNOT_BE_BANISHED));
        if (hit == null) {
            return InteractionResultHolder.fail(stack);
        }
        Entity target = hit.getEntity();
        Vec3 pull = player.position().subtract(target.position());
        double distance = Math.min(pull.length(), 8.0);
        Vec3 velocity = pull.normalize().scale(Balance.SICKLE_PULL * distance / 8.0 + 0.4).add(0, 0.25, 0);
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        target.hurt(level.damageSources().playerAttack(player), Balance.SICKLE_HOOK_DAMAGE);
        server.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.8F);
        if (player.isInWaterOrRain() && tide(stack) < Balance.SICKLE_MAX_TIDE) {
            stack.set(ModComponents.TIDE_CHARGES.get(), tide(stack) + 1);
            player.displayClientMessage(Component.translatable("lacuna.sickle.tide", tide(stack), Balance.SICKLE_MAX_TIDE), true);
        }
        stack.hurtAndBreak(2, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
        player.getCooldowns().addCooldown(this, Balance.SICKLE_COOLDOWN);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        int charges = tide(stack);
        if (charges > 0 && !attacker.level().isClientSide()) {
            attacker.heal(Balance.SICKLE_TIDE_HEAL);
            stack.set(ModComponents.TIDE_CHARGES.get(), charges - 1);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return Enchants.unbreakingOnly(enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return Enchants.unbreakingOnly(enchantment);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.tidecaller_sickle.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("lacuna.sickle.tide", tide(stack), Balance.SICKLE_MAX_TIDE).withStyle(ChatFormatting.AQUA));
    }
}
