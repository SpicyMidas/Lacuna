package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Umbral Steel Axe. "Eclipse Cleave": a fully charged melee hit (attack strength >= 0.9, captured when the attack
 * starts) releases a shadow shockwave at the target's feet: every other living entity within 3 blocks
 * takes 60 % of the hit (lacuna:eclipse_cleave) and is thrown away from the target. The wielder, owned pets,
 * villagers, player-made golems and (without PvP) players are spared. No cooldown: the charge is the price.
 */
public class UmbralAxeItem extends AxeItem {
    /** A charged swing of the axe at {@code targetId}, made at game tick {@code tick}. */
    private record ChargedSwing(int targetId, long tick) {}

    private static final Map<Player, ChargedSwing> CHARGED = new WeakHashMap<>();

    public UmbralAxeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /** AttackEntityEvent fires before the attack: the attack strength is still the one the swing was made with. */
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (player.getMainHandItem().getItem() instanceof UmbralAxeItem
                && player.getAttackStrengthScale(0.5F) >= Balance.UMBRAL_CLEAVE_MIN_CHARGE) {
            CHARGED.put(player, new ChargedSwing(event.getTarget().getId(), player.level().getGameTime()));
        } else {
            CHARGED.remove(player);
        }
    }

    /** The charged hit landed: release the shockwave around its target. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof Player player) || event.getSource().getDirectEntity() != player
                || !event.getSource().is(DamageTypes.PLAYER_ATTACK) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChargedSwing swing = CHARGED.get(player);
        LivingEntity target = event.getEntity();
        if (swing == null || swing.targetId() != target.getId() || swing.tick() != level.getGameTime()) {
            return;
        }
        CHARGED.remove(player);
        if (!(player.getMainHandItem().getItem() instanceof UmbralAxeItem) || !LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return;
        }
        float damage = event.getOriginalDamage() * Balance.UMBRAL_CLEAVE_FACTOR;
        SafeRun.run("eclipse_cleave", () -> cleave(level, player, target, damage));
    }

    private static void cleave(ServerLevel level, Player player, LivingEntity target, float damage) {
        double r = Balance.UMBRAL_CLEAVE_RADIUS;
        Vec3 feet = target.position();
        AABB box = new AABB(feet, feet).inflate(r, 2.0, r);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> !spared(player, target, e))) {
            double dx = victim.getX() - feet.x;
            double dz = victim.getZ() - feet.z;
            if (dx * dx + dz * dz > r * r) {
                continue;
            }
            if (victim.hurt(ModDamageTypes.source(level, ModDamageTypes.ECLIPSE_CLEAVE, player), damage)) {
                if (dx * dx + dz * dz < 1.0E-4) {
                    dx = level.random.nextDouble() - 0.5;
                    dz = level.random.nextDouble() - 0.5;
                }
                victim.knockback(Balance.UMBRAL_CLEAVE_KNOCKBACK, -dx, -dz); // away from the cleaved target
                Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
                level.sendParticles(ModParticles.UMBRAL_SHARD.get(), c.x, c.y, c.z, 6, 0.2, 0.3, 0.2, 0.18);
                level.sendParticles(ModParticles.SHADOW_MOTE.get(), c.x, c.y, c.z, 5, 0.25, 0.35, 0.25, 0.03);
            }
        }
        shockwave(level, feet, r);
    }

    /** True for entities the shockwave must not touch. */
    private static boolean spared(Player player, LivingEntity target, LivingEntity e) {
        if (e == player || e == target || !e.isAlive() || e.isSpectator() || e == player.getVehicle() || player.isAlliedTo(e)) {
            return true;
        }
        if (e instanceof ArmorStand || e instanceof AbstractVillager || e instanceof SnowGolem
                || (e instanceof IronGolem golem && golem.isPlayerCreated())) {
            return true;
        }
        if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return true;
        }
        if (e instanceof Player other) {
            return !player.canHarmPlayer(other) || player.getServer() == null || !player.getServer().isPvpAllowed();
        }
        return false;
    }

    /** A dark ring racing outward from the target's feet, with a low, heavy boom. */
    private static void shockwave(ServerLevel level, Vec3 feet, double r) {
        double y = feet.y + 0.15;
        int points = 28;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            double cx = Math.cos(a);
            double cz = Math.sin(a);
            // count 0 = one particle with an exact velocity: the motes fly outward along the ground
            level.sendParticles(ModParticles.SHADOW_MOTE.get(), feet.x + cx * 0.5, y, feet.z + cz * 0.5, 0, cx, 0.02, cz, 0.32);
            if (i % 2 == 0) {
                level.sendParticles(ModParticles.UMBRAL_SHARD.get(), feet.x + cx * r * 0.85, y + 0.1, feet.z + cz * r * 0.85,
                        0, cx * 0.3, 0.35, cz * 0.3, 0.6);
            }
        }
        level.sendParticles(ModParticles.UMBRAL_SLASH.get(), feet.x, y + 0.9, feet.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ModParticles.SHADOW_MOTE.get(), feet.x, y + 0.4, feet.z, 14, 0.5, 0.2, 0.5, 0.05);
        // expanding black/crimson ground ring (client effect shared with the Eclipser's dash end)
        LacunaFx.send(level, feet, FxPayload.at("eclipse_end", feet));

        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.9F, 0.6F);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.25F, 0.6F);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.umbral_steel_axe.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.umbral_steel_axe.cleave").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.lacuna.umbral_steel_axe.cleave_detail",
                Math.round(Balance.UMBRAL_CLEAVE_FACTOR * 100) + "%", (int) Balance.UMBRAL_CLEAVE_RADIUS).withStyle(ChatFormatting.GRAY));
        UmbralTools.appendNightforged(tooltip);
    }
}
