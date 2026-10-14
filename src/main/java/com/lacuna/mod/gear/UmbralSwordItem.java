package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

/**
 * Umbral Steel Sword. "Umbral Rend": every melee hit leaves a rend stack on the target (4 s window, refreshed by
 * each hit of the same wielder); the 3rd consecutive hit detonates the rend for +6 armor-piercing damage
 * (lacuna:umbral_rend), heals the wielder 2, and slows (II) and darkens the target for 2 s. "Nightforged" mining
 * bonus: see {@link UmbralTools}.
 */
public class UmbralSwordItem extends SwordItem {
    /** Rend stacks on a target. {@link #NONE} = no rend. */
    public record Rend(UUID attacker, int stacks, long expires) {
        public static final Rend NONE = new Rend(Util.NIL_UUID, 0, 0L);
    }

    public UmbralSwordItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            SafeRun.run("umbral_rend", () -> rend(level, target, attacker));
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    private static void rend(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        if (!target.isAlive()) {
            target.removeData(ModAttachments.UMBRAL_REND);
            return;
        }
        long now = level.getGameTime();
        Rend current = target.getData(ModAttachments.UMBRAL_REND);
        boolean chained = current.attacker().equals(attacker.getUUID()) && now <= current.expires();
        int stacks = chained ? current.stacks() + 1 : 1;
        if (stacks >= Balance.UMBRAL_REND_HITS) {
            target.removeData(ModAttachments.UMBRAL_REND);
            detonate(level, target, attacker);
        } else {
            target.setData(ModAttachments.UMBRAL_REND, new Rend(attacker.getUUID(), stacks, now + Balance.UMBRAL_REND_WINDOW));
            mark(level, target, stacks);
        }
    }

    /** A rend stack: a small shadow puff that thickens with every stack and a soft sculk click rising in pitch. */
    private static void mark(ServerLevel level, LivingEntity target, int stacks) {
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
        double w = target.getBbWidth() * 0.35;
        level.sendParticles(ModParticles.SHADOW_MOTE.get(), c.x, c.y, c.z, 3 + 3 * stacks, w, target.getBbHeight() * 0.25, w, 0.015);
        level.sendParticles(ModParticles.UMBRAL_WISP.get(), c.x, c.y, c.z, stacks, w, 0.2, w, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.55F, 0.9F + 0.3F * stacks);
        if (stacks == Balance.UMBRAL_REND_HITS - 1) {
            // primed: the next hit tears it open
            level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    /** The third hit: the gathered shadow tears open. */
    private static void detonate(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        target.invulnerableTime = 0; // a separate strike right after the melee hit
        target.hurt(ModDamageTypes.source(level, ModDamageTypes.UMBRAL_REND, attacker), Balance.UMBRAL_REND_DAMAGE);
        if (attacker.isAlive()) {
            attacker.heal(Balance.UMBRAL_REND_HEAL);
        }
        if (target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Balance.UMBRAL_REND_DEBUFF_TICKS, 1), attacker);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, Balance.UMBRAL_REND_DEBUFF_TICKS, 0), attacker);
        }

        Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
        double w = target.getBbWidth() * 0.4;
        double h = target.getBbHeight() * 0.3;
        level.sendParticles(ModParticles.UMBRAL_SLASH.get(), c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0.0);
        level.sendParticles(ModParticles.UMBRAL_SHARD.get(), c.x, c.y, c.z, 14, w, h, w, 0.25);
        level.sendParticles(ModParticles.SHADOW_MOTE.get(), c.x, c.y, c.z, 22, w + 0.2, h + 0.2, w + 0.2, 0.06);
        level.sendParticles(ModParticles.UMBRAL_WISP.get(), c.x, c.y, c.z, 8, w, h, w, 0.08);
        // a little of the torn shadow flows back into the wielder
        Vec3 a = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0);
        level.sendParticles(ModParticles.UMBRAL_WISP.get(), a.x, a.y, a.z, 5, 0.3, 0.4, 0.3, 0.02);
        // crossing black slashes with a crimson flare (client effect shared with the Eclipser)
        Vec3 dir = c.subtract(a).normalize();
        LacunaFx.send(level, c, new FxPayload("eclipse_hit", target.getId(), c, dir,
                target.getBbHeight(), target.getBbWidth(), 1, 0xFFFFFFFF));

        float jitter = 0.9F + level.random.nextFloat() * 0.2F;
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.55F, 1.1F * jitter);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F * jitter);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 0.7F * jitter);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.SCULK_CLICKING_STOP, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.umbral_steel_sword.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.umbral_steel_sword.rend").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.lacuna.umbral_steel_sword.rend_detail", fmt(Balance.UMBRAL_REND_DAMAGE),
                fmt(Balance.UMBRAL_REND_HEAL)).withStyle(ChatFormatting.GRAY));
        UmbralTools.appendNightforged(tooltip);
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
