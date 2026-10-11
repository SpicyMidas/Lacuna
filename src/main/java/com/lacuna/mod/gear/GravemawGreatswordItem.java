package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.util.SafeRun;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gravemaw Greatsword (spec M7): hold use while standing still to charge (max 40 ticks); releasing at >= 20 ticks
 * sends an instant crescent wave (8 blocks, +-35 degrees) dealing 10 + 0.3 x charge. Moving cancels the charge.
 */
public class GravemawGreatswordItem extends SwordItem {
    public GravemawGreatswordItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        double moved = Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo);
        if (moved > Balance.GRAVEMAW_STILL_EPSILON) {
            entity.stopUsingItem(); // moving cancels the charge (releaseUsing is not called)
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int charge = Math.min(Balance.GRAVEMAW_MAX_CHARGE, getUseDuration(stack, entity) - timeLeft);
        if (charge < Balance.GRAVEMAW_MIN_CHARGE || !(level instanceof ServerLevel server) || !(entity instanceof Player player)) {
            return;
        }
        SafeRun.run("gravemaw", () -> wave(server, player, stack, charge));
    }

    private void wave(ServerLevel level, Player player, ItemStack stack, int charge) {
        float damage = Balance.GRAVEMAW_WAVE_BASE + Balance.GRAVEMAW_WAVE_PER_TICK * charge;
        Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 facing = player.getLookAngle().multiply(1, 0, 1).normalize();
        double cos = Math.cos(Math.toRadians(Balance.GRAVEMAW_ARC_DEGREES));
        double range = Balance.GRAVEMAW_RANGE;
        AABB box = player.getBoundingBox().inflate(range, 2.0, range);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator())) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(origin);
            Vec3 flat = to.multiply(1, 0, 1);
            if (flat.length() > range || flat.lengthSqr() < 1.0E-4 || flat.normalize().dot(facing) < cos) {
                continue;
            }
            if (target.hurt(ModDamageTypes.source(level, ModDamageTypes.GRAVEMAW_WAVE, player), damage)) {
                target.knockback(1.0, -facing.x, -facing.z);
            }
        }
        for (double d : new double[] {2.5, 5.0, 7.5}) {
            for (int i = -2; i <= 2; i++) {
                double angle = Math.toRadians(Balance.GRAVEMAW_ARC_DEGREES * i / 2.0);
                Vec3 dir = new Vec3(facing.x * Math.cos(angle) - facing.z * Math.sin(angle), 0,
                        facing.x * Math.sin(angle) + facing.z * Math.cos(angle));
                Vec3 p = origin.add(dir.scale(d));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.5F);
        stack.hurtAndBreak(3, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        player.getCooldowns().addCooldown(this, Balance.GRAVEMAW_COOLDOWN);
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
        tooltip.add(Component.translatable("item.lacuna.gravemaw_greatsword.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.gravemaw_greatsword.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
