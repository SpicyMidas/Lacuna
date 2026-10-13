package com.lacuna.mod.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.registry.ModDimensions;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Sun's Fury (spec 11.3, revision 2): a single-use spear of sunlight thrown like a trident. Hold right click to draw
 * it back (spear pose); releasing after at least {@link Balance#SUNS_FURY_MIN_CHARGE} ticks throws it, faster the
 * longer it was drawn (up to the spec speed of 2.5). Consumed on throw except in Creative; fizzles (and is kept) in
 * the Forgotten Land.
 */
public class SunsFuryItem extends Item {
    public SunsFuryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.SUNS_FURY_ENABLED)) {
            return InteractionResultHolder.fail(stack);
        }
        if (ModDimensions.isForgottenLand(level)) {
            fizzle(level, player);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** Drawing feedback: light gathers on the raised spear and a chime marks full power. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int charge = getUseDuration(stack, entity) - remainingUseDuration;
        if (charge == Balance.SUNS_FURY_FULL_CHARGE) {
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    1.2F, 1.6F);
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                    0.5F, 1.8F);
        }
        // the light gathering on the spear head is drawn by the clients (client/solar/SolarClient)
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int charge = getUseDuration(stack, entity) - timeLeft;
        float speed = SolarMath.throwSpeed(charge, Balance.SUNS_FURY_MIN_CHARGE, Balance.SUNS_FURY_FULL_CHARGE, Balance.SUNS_FURY_SPEED);
        if (speed <= 0.0F || !LacunaConfig.get(LacunaConfig.SUNS_FURY_ENABLED)) {
            return;
        }
        if (ModDimensions.isForgottenLand(level)) {
            fizzle(level, player);
            return;
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (level.isClientSide()) {
            return;
        }
        SunsFuryEntity spear = new SunsFuryEntity(level, player);
        spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, speed, 0.5F);
        level.addFreshEntity(spear);
        com.lacuna.mod.util.LacunaAdvancements.award(player, "lacuna/throw_suns_fury");
        level.playSound(null, spear, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
        stack.consume(1, player);
    }

    private static void fizzle(Level level, Player player) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 10, 0.2, 0.2, 0.2, 0.01);
            server.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.8F);
            player.displayClientMessage(Component.translatable("lacuna.suns_fury.fizzles"), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.suns_fury.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.suns_fury.use").withStyle(ChatFormatting.GOLD));
    }
}
