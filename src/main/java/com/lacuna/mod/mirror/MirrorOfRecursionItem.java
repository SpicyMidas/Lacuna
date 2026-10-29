package com.lacuna.mod.mirror;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Mirror of Recursion (spec M6): raised like a shield it cancels reflectable projectiles hitting its front 180
 * degrees and sends them back to their shooter (1 durability each), keeping up to 3 echoes; sneak + use re-fires
 * the oldest echo at 0.85^(reuse+1) power, and an echo used 3 times destabilises.
 */
public class MirrorOfRecursionItem extends Item {
    public static final String FEATURE = "mirror";

    public MirrorOfRecursionItem(Properties properties) {
        super(properties);
    }

    public static List<StoredProjectile> stored(ItemStack stack) {
        return stack.getOrDefault(ModComponents.MIRROR_STORED.get(), List.of());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.MIRROR_ENABLED)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                SafeRun.run(FEATURE, () -> refire((ServerLevel) level, player, stack));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    private static void refire(ServerLevel level, Player player, ItemStack stack) {
        List<StoredProjectile> list = new ArrayList<>(stored(stack));
        if (list.isEmpty()) {
            player.displayClientMessage(Component.translatable("lacuna.mirror.empty"), true);
            return;
        }
        StoredProjectile echo = list.remove(0);
        if (echo.reuseCount() >= Balance.MIRROR_MAX_REUSE) {
            stack.set(ModComponents.MIRROR_STORED.get(), List.copyOf(list));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY(), player.getZ(), 20, 0.3, 0.3, 0.3, 0.2);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
            player.displayClientMessage(Component.translatable("lacuna.mirror.destabilized"), true);
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(echo.entityType()).orElse(null);
        Entity created = type == null ? null : type.create(level);
        if (!(created instanceof Projectile projectile)) {
            stack.set(ModComponents.MIRROR_STORED.get(), List.copyOf(list));
            return;
        }
        double power = Math.pow(Balance.MIRROR_REUSE_FACTOR, echo.reuseCount() + 1);
        Vec3 look = player.getLookAngle();
        projectile.setPos(player.getX() + look.x * 0.8, player.getEyeY() - 0.1, player.getZ() + look.z * 0.8);
        projectile.setOwner(player);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(echo.baseDamage() * power);
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }
        projectile.shoot(look.x, look.y, look.z, (float) (Math.max(0.5, echo.speed()) * power), 0.0F);
        level.addFreshEntity(projectile);
        list.add(echo.reused());
        stack.set(ModComponents.MIRROR_STORED.get(), List.copyOf(list));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** ProjectileImpactEvent: reflect projectiles hitting a player who raises the mirror. */
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof MirrorOfRecursionItem)
                || !LacunaConfig.get(LacunaConfig.MIRROR_ENABLED) || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        Projectile projectile = event.getProjectile();
        EntityType<?> type = projectile.getType();
        if (!type.is(ModTags.Entities.REFLECTABLE) || type.is(ModTags.Entities.UNREFLECTABLE)) {
            return;
        }
        Vec3 motion = projectile.getDeltaMovement();
        Vec3 facing = player.getLookAngle().multiply(1, 0, 1).normalize();
        if (motion.lengthSqr() < 1.0E-6 || motion.multiply(1, 0, 1).normalize().dot(facing) > 0.0) {
            return; // not coming from the front 180 degrees
        }
        ItemStack mirror = player.getUseItem();
        SafeRun.run(FEATURE, () -> {
            event.setCanceled(true);
            reflect(projectile, player);
            store(mirror, projectile);
            mirror.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        });
    }

    private static void reflect(Projectile projectile, Player player) {
        Entity shooter = projectile.getOwner();
        double speed = Math.max(0.6, projectile.getDeltaMovement().length());
        Vec3 dir;
        if (shooter != null && shooter.isAlive() && shooter.level() == projectile.level()) {
            dir = shooter.getEyePosition().subtract(projectile.position()).normalize();
        } else {
            dir = projectile.getDeltaMovement().reverse().normalize();
        }
        if (!(projectile instanceof ThrownTrident)) {
            projectile.setOwner(player);
            if (projectile instanceof AbstractArrow arrow) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
        } // a trident stays its thrower's: it can still be picked up and Loyalty brings it home, never lost to a mirror
        projectile.setDeltaMovement(dir.scale(speed));
        projectile.hasImpulse = true;
        if (projectile.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, projectile.getX(), projectile.getY(), projectile.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.5F);
        }
    }

    private static void store(ItemStack mirror, Projectile projectile) {
        List<StoredProjectile> list = new ArrayList<>(stored(mirror));
        if (list.size() >= Balance.MIRROR_MAX_STORED) {
            return;
        }
        double damage = projectile instanceof AbstractArrow arrow ? arrow.getBaseDamage() : 0.0;
        list.add(new StoredProjectile(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()), damage,
                projectile.getDeltaMovement().length(), 0));
        mirror.set(ModComponents.MIRROR_STORED.get(), List.copyOf(list));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(ModItems.DIVINE_GLASS_SHARD.get());
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.mirror_of_recursion.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.mirror_of_recursion.use").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("lacuna.mirror.stored", stored(stack).size(), Balance.MIRROR_MAX_STORED)
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
