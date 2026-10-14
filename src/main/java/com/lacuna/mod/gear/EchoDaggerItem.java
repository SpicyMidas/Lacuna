package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Echo Dagger (spec M7): every melee hit repeats once 10 ticks later at 60 % damage (lacuna:echo_strike). */
public class EchoDaggerItem extends SwordItem {
    private record PendingEcho(UUID target, ResourceKey<Level> dimension, UUID attacker, float amount, long due) {}

    private static final List<PendingEcho> QUEUE = new ArrayList<>();

    public EchoDaggerItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /** LivingDamageEvent.Post: schedule the echo of a direct player melee hit made with the dagger. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof Player player) || event.getSource().getDirectEntity() != player
                || !event.getSource().is(DamageTypes.PLAYER_ATTACK) || player.level().isClientSide()
                || !(player.getMainHandItem().getItem() instanceof EchoDaggerItem) || !LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return;
        }
        LivingEntity target = event.getEntity();
        QUEUE.add(new PendingEcho(target.getUUID(), target.level().dimension(), player.getUUID(),
                event.getNewDamage() * Balance.ECHO_DAGGER_FACTOR, player.level().getGameTime() + Balance.ECHO_DAGGER_DELAY));
        com.lacuna.mod.util.Sfx.varied(target, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, 0.8F, 1.7F, 0.1F);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        List<PendingEcho> due = new ArrayList<>();
        Iterator<PendingEcho> it = QUEUE.iterator();
        while (it.hasNext()) {
            PendingEcho p = it.next();
            if (now >= p.due()) {
                due.add(p);
                it.remove();
            }
        }
        SafeRun.run("echo_dagger", () -> {
            for (PendingEcho p : due) {
                ServerLevel level = server.getLevel(p.dimension());
                if (level == null || !(level.getEntity(p.target()) instanceof LivingEntity target) || !target.isAlive()) {
                    continue;
                }
                Entity attacker = level.getEntity(p.attacker());
                target.invulnerableTime = 0; // the echo is a separate, delayed strike
                if (target.hurt(ModDamageTypes.source(level, ModDamageTypes.ECHO_STRIKE, attacker), p.amount())) {
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                            target.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
                    com.lacuna.mod.util.Sfx.varied(target, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F, 0.1F);
                    com.lacuna.mod.util.Sfx.varied(target, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.8F, 0.1F);
                    com.lacuna.mod.util.Sfx.at(target, net.minecraft.sounds.SoundEvents.SCULK_CLICKING, 0.7F, 1.3F);
                }
            }
        }, QUEUE::clear);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
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
        tooltip.add(Component.translatable("item.lacuna.echo_dagger.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
