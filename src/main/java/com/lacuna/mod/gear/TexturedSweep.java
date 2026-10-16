package com.lacuna.mod.gear;

import com.lacuna.mod.eclipse.EclipserItem;
import com.lacuna.mod.registry.ModParticles;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;

/**
 * The Eclipser and the Gravemaw Greatsword sweep exactly like a vanilla sword - same conditions (vanilla decides them:
 * full charge, on the ground, not running, not a sweep-disabling crit), same targets, damage, knockback and sound, and the
 * slash particle placed one block in front of the player at mid-height like {@code Player#sweepAttack} - but with their
 * own slash sprites (black and crimson, blue and violet). The vanilla sweep is taken over through
 * {@link SweepAttackEvent} and replayed right after the main hit lands ({@code hurtEnemy}), as vanilla does it.
 */
public final class TexturedSweep {
    private record Pending(UUID target, float strength) {}

    private static final Map<UUID, Float> STRENGTH = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private TexturedSweep() {}

    public static void register(IEventBus bus) {
        bus.addListener(TexturedSweep::onAttack);
        bus.addListener(TexturedSweep::onSweep);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            STRENGTH.remove(e.getEntity().getUUID());
            PENDING.remove(e.getEntity().getUUID());
        });
    }

    @Nullable
    private static SimpleParticleType slashOf(ItemStack weapon) {
        if (weapon.getItem() instanceof EclipserItem) {
            return ModParticles.ECLIPSE_SLASH.get();
        }
        if (weapon.getItem() instanceof GravemawGreatswordItem) {
            return ModParticles.UMBRAL_SLASH.get();
        }
        return null;
    }

    /** The attack strength of the swing, read before vanilla resets the attack timer. */
    private static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && slashOf(player.getMainHandItem()) != null) {
            STRENGTH.put(player.getUUID(), player.getAttackStrengthScale(0.5F));
        }
    }

    /** Vanilla decided this swing sweeps: take the sweep over (no white sweep) and replay it once the hit lands. */
    private static void onSweep(SweepAttackEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !event.isSweeping() || slashOf(player.getMainHandItem()) == null) {
            return;
        }
        PENDING.put(player.getUUID(), new Pending(event.getTarget().getUUID(), STRENGTH.getOrDefault(player.getUUID(), 1.0F)));
        event.setSweeping(false);
        event.setCanceled(true);
    }

    /** Called from the weapons' {@code hurtEnemy}, i.e. after the main hit succeeded, where vanilla sweeps. */
    public static void afterHit(LivingEntity attacker, LivingEntity target, ItemStack weapon) {
        if (!(attacker instanceof Player player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Pending pending = PENDING.remove(player.getUUID());
        SimpleParticleType slash = slashOf(weapon);
        if (pending == null || slash == null || !pending.target().equals(target.getUUID())) {
            return;
        }
        float f2 = pending.strength();
        float f = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + f2 * f2 * 0.8F);
        float f7 = 1.0F + (float) player.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO) * f;
        DamageSource source = player.damageSources().playerAttack(player);
        double reachSq = Mth.square(player.entityInteractionRange());
        float yaw = player.getYRot() * (float) (Math.PI / 180.0);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, weapon.getSweepHitBox(player, target))) {
            if (other != player && other != target && !player.isAlliedTo(other)
                    && (!(other instanceof ArmorStand stand) || !stand.isMarker()) && player.distanceToSqr(other) < reachSq) {
                float f5 = EnchantmentHelper.modifyDamage(level, weapon, other, source, f7) * f2;
                other.knockback(0.4F, Mth.sin(yaw), -Mth.cos(yaw));
                other.hurt(source, f5);
                EnchantmentHelper.doPostAttackEffects(level, other, source);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1.0F, 1.0F);
        // Player#sweepAttack, with the weapon's own slash
        double d0 = -Mth.sin(yaw);
        double d1 = Mth.cos(yaw);
        level.sendParticles(slash, player.getX() + d0, player.getY(0.5), player.getZ() + d1, 0, d0, 0.0, d1, 0.0);
    }
}
