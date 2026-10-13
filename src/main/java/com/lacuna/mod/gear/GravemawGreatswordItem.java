package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
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
            if (level instanceof ServerLevel server) {
                // the gathered shadow scatters
                server.sendParticles(ModParticles.SHADOW_MOTE.get(), entity.getX(), entity.getY() + 1.0, entity.getZ(), 10, 0.5, 0.6, 0.5, 0.03);
                server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 0.6F);
            }
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int charge = getUseDuration(stack, entity) - remaining;
        float f = Math.min(1.0F, charge / (float) Balance.GRAVEMAW_MAX_CHARGE);
        if (charge % 5 == 0 && charge <= Balance.GRAVEMAW_MAX_CHARGE) {
            // a hum that rises with the charge
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                    0.5F + 0.4F * f, 0.5F + 1.1F * f);
        }
        if (charge == Balance.GRAVEMAW_MIN_CHARGE) {
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.7F);
            LacunaFx.send(server, entity.position(), FxPayload.at("gravemaw_ready", entity.position()).withEntity(entity.getId()));
        } else if (charge == Balance.GRAVEMAW_MAX_CHARGE) {
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.9F, 0.8F);
            LacunaFx.send(server, entity.position(), FxPayload.at("gravemaw_ready", entity.position()).withEntity(entity.getId())
                    .withParams(1.0F, 0.0F, 0.0F));
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel server) {
            // heavy blade: a dark slash and a low thud on every melee hit
            server.sendParticles(ModParticles.UMBRAL_SLASH.get(), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
            server.sendParticles(ModParticles.UMBRAL_SHARD.get(), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    6, 0.25, 0.3, 0.25, 0.12);
            server.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.55F);
        }
        return super.hurtEnemy(stack, target, attacker);
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
                Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
                level.sendParticles(ModParticles.CRIMSON_SPARK.get(), c.x, c.y, c.z, 10, 0.2, 0.3, 0.2, 0.25);
                level.sendParticles(ModParticles.UMBRAL_SHARD.get(), c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.2);
            }
        }
        // the wave itself is drawn by the clients (GravemawFx): a curved wall of umbral energy sweeping 8 blocks out
        float strength = (charge - Balance.GRAVEMAW_MIN_CHARGE) / (float) (Balance.GRAVEMAW_MAX_CHARGE - Balance.GRAVEMAW_MIN_CHARGE);
        LacunaFx.send(level, player.position(), FxPayload.line("gravemaw_wave", player.position(), player.position().add(facing))
                .withEntity(player.getId()).withParams(strength, (float) range, (float) Balance.GRAVEMAW_ARC_DEGREES));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.8F, 0.6F);
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
