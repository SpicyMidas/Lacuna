package com.lacuna.mod.eclipse;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ChargeMath;
import com.lacuna.mod.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Eclipser (spec 11.2): black sword that builds charge (0-100) on hits, loses it when the wielder is hurt, decays
 * after 8 s idle, and spends it on a dash (charge >= 20) that grants Shadow Walk. Only Unbreaking applies.
 */
public class EclipserItem extends SwordItem {
    public EclipserItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static EclipseCharge state(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ECLIPSE_CHARGE, EclipseCharge.EMPTY);
    }

    public static int charge(ItemStack stack) {
        return state(stack).charge();
    }

    public static void setState(ItemStack stack, EclipseCharge state) {
        stack.set(ModComponents.ECLIPSE_CHARGE, state);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide() && LacunaConfig.get(LacunaConfig.ECLIPSER_ENABLED)) {
            long now = attacker.level().getGameTime();
            EclipseCharge s = state(stack);
            if (ChargeMath.canGain(now, s.lastHit())) {
                EclipseCharge next = new EclipseCharge(ChargeMath.afterHit(s.charge()), now);
                setState(stack, next);
                showCharge(attacker, next.charge());
            }
            slash(target, attacker);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /** Strength of the swing that is landing, captured before vanilla resets the attack timer (AttackEntityEvent). */
    private static final java.util.Map<java.util.UUID, Float> SWING = new java.util.HashMap<>();
    private static int swings;

    public static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && player.getMainHandItem().getItem() instanceof EclipserItem) {
            SWING.put(player.getUUID(), player.getAttackStrengthScale(0.5F));
        }
    }

    /**
     * The Eclipser's own cut replaces the vanilla white sweep: a black and crimson crescent through the target, and on
     * a full, grounded swing the crescent sweeps the creatures right around the target too.
     */
    private static void slash(LivingEntity target, LivingEntity attacker) {
        if (!(attacker.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        float strength = attacker instanceof Player p ? SWING.getOrDefault(p.getUUID(), 1.0F) : 1.0F;
        net.minecraft.world.phys.Vec3 from = attacker.position().add(0, attacker.getBbHeight() * 0.62, 0);
        net.minecraft.world.phys.Vec3 at = target.position().add(0, target.getBbHeight() * 0.55, 0);
        com.lacuna.mod.fx.LacunaFx.send(level, at, com.lacuna.mod.network.FxPayload.line("eclipser_slash", from, at)
                .withEntity(attacker.getId()).withParams(swings++, strength, 0.0F));
        com.lacuna.mod.util.Sfx.varied(target, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.62F, 0.06F);
        com.lacuna.mod.util.Sfx.varied(target, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK, 0.45F, 0.55F, 0.05F);
        if (strength >= 0.9F) {
            com.lacuna.mod.util.Sfx.at(target, net.minecraft.sounds.SoundEvents.TRIDENT_HIT, 0.5F, 0.7F);
        }
        if (strength < 0.9F || !(attacker instanceof Player player) || !player.onGround() || player.isSprinting()) {
            return;
        }
        float damage = 1.0F + 0.5F * (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0, 0.25, 1.0),
                e -> e != player && e != target && e.isAlive() && !player.isAlliedTo(e)
                        && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand stand && stand.isMarker())
                        && !(e instanceof net.minecraft.world.entity.OwnableEntity own && player.getUUID().equals(own.getOwnerUUID()))
                        && player.distanceToSqr(e) < 9.0)) {
            other.knockback(0.4F, Math.sin(player.getYRot() * Math.PI / 180.0), -Math.cos(player.getYRot() * Math.PI / 180.0));
            other.hurt(level.damageSources().playerAttack(player), damage);
        }
    }

    /** No vanilla sweep (its white crescent): the Eclipser sweeps with its own black and crimson cut above. */
    @Override
    public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility ability) {
        return ability != net.neoforged.neoforge.common.ItemAbilities.SWORD_SWEEP && super.canPerformAction(stack, ability);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || level.getGameTime() % 20 != 0) {
            return;
        }
        EclipseCharge s = state(stack);
        if (s.charge() <= 0) {
            return;
        }
        int decayed = ChargeMath.afterDecaySecond(s.charge(), level.getGameTime(), s.lastHit());
        if (decayed != s.charge()) {
            setState(stack, new EclipseCharge(decayed, s.lastHit()));
            if (selected) {
                showCharge(entity, decayed);
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.ECLIPSER_ENABLED)) {
            return InteractionResultHolder.pass(stack);
        }
        int charge = charge(stack);
        if (!ChargeMath.canDash(charge)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("lacuna.eclipser.not_enough", charge), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer && EclipseDash.perform(serverPlayer, stack, hand, charge)) {
            player.getCooldowns().addCooldown(this, LacunaConfig.get(LacunaConfig.ECLIPSE_COOLDOWN));
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    public static void showCharge(Entity entity, int charge) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("lacuna.eclipser.charge", charge)
                    .withStyle(charge >= 67 ? ChatFormatting.RED : ChatFormatting.DARK_RED), true);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.UNBREAKING);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.UNBREAKING);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return true; // only Unbreaking passes supportsEnchantment
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.eclipser.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("lacuna.eclipser.charge", charge(stack)).withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.lacuna.eclipser.use", ChargeMath.MIN_DASH).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Charge resets whenever the wielder takes damage (called from LivingDamageEvent.Post). */
    public static void resetOnDamage(LivingEntity entity) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = entity.getItemInHand(hand);
            if (stack.getItem() instanceof EclipserItem && charge(stack) > 0) {
                setState(stack, new EclipseCharge(0, state(stack).lastHit()));
                showCharge(entity, 0);
            }
        }
    }
}
