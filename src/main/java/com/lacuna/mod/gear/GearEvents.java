package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Gear listeners: Umbral darkness bonus, Starlace Boots glide, special weapon hooks. */
public final class GearEvents {
    private GearEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(GearEvents::onIncomingDamage);
        bus.addListener(GearEvents::onPlayerTick);
        bus.addListener(EchoDaggerItem::onDamage);
        bus.addListener(EchoDaggerItem::onServerTick);
        bus.addListener(EchoDaggerItem::onServerStopped);
        bus.addListener(HeartwoodBowItem::onImpact);
        bus.addListener(UmbralAxeItem::onAttack);
        bus.addListener(UmbralAxeItem::onDamage);
        bus.addListener(UmbralTools::onBreakSpeed);
        bus.addListener(TideCurrents::onServerTick);
        bus.addListener(TideCurrents::onServerStopped);
        bus.addListener(EmberWave::onServerTick);
        bus.addListener(EmberWave::onServerStopped);
        bus.addListener(AnimatedItemEntity::onIncomingDamage);
        bus.addListener(AnimatedItemEntity::onMobGriefing);
    }

    /** +25 % damage dealt with Umbral gear when the wielder stands where max local raw brightness <= 4. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker
                || attacker.level().isClientSide()) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!weapon.is(ModTags.Items.UMBRAL_GEAR)) {
            return;
        }
        if (attacker.level().getMaxLocalRawBrightness(attacker.blockPosition()) <= Balance.UMBRAL_DARK_LIGHT) {
            event.setAmount(event.getAmount() * (1.0F + (float) LacunaConfig.get(LacunaConfig.UMBRAL_DARK_BONUS)));
        }
    }

    /** Starlace Boots: sneaking while falling more than 4 blocks grants slow falling once per fall (200-tick cooldown). */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.onGround() || player.fallDistance <= Balance.STARLACE_GLIDE_FALL
                || !player.isShiftKeyDown() || !LacunaConfig.get(LacunaConfig.STARLACE_GLIDE_ENABLED)) {
            return;
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (!boots.is(ModItems.STARLACE_BOOTS.get()) || player.getCooldowns().isOnCooldown(boots.getItem())
                || player.hasEffect(MobEffects.SLOW_FALLING)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Balance.STARLACE_GLIDE_TICKS, 0, false, true, true));
        player.getCooldowns().addCooldown(boots.getItem(), Balance.STARLACE_GLIDE_COOLDOWN);
        boots.hurtAndBreak(1, player, EquipmentSlot.FEET);
    }
}
