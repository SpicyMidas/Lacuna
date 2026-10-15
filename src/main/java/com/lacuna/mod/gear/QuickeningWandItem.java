package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.Sfx;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Wand of Quickening: like the Pyrrhic Scepter it is paid for in health (2, refused when too weak, free in creative).
 * The dropped item under the crosshair (or, failing that, the item in the off hand, thrown out) wakes up as an
 * {@link AnimatedItemEntity} for 40 s, then falls asleep again as exactly the same stack.
 */
public class QuickeningWandItem extends Item {
    private static final DustParticleOptions LIFE_THREAD = new DustParticleOptions(new Vector3f(1.0F, 0.42F, 0.78F), 0.8F);

    public QuickeningWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return InteractionResultHolder.pass(wand);
        }
        boolean creative = player.getAbilities().instabuild;
        if (player.getHealth() <= Balance.QUICKENING_MIN_HEALTH && !creative) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("lacuna.quickening.too_weak").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(wand);
        }
        ItemEntity dropped = pickDroppedItem(level, player);
        ItemStack offhand = hand == InteractionHand.MAIN_HAND ? player.getOffhandItem() : ItemStack.EMPTY;
        if (dropped == null && offhand.isEmpty()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("lacuna.quickening.no_target").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(wand);
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(wand);
        }

        Vec3 look = player.getViewVector(1.0F);
        AnimatedItemEntity animated;
        if (dropped != null) {
            // it wakes up facing whoever woke it
            float faceYaw = (float) Math.toDegrees(Math.atan2(dropped.getX() - player.getX(), player.getZ() - dropped.getZ()));
            animated = AnimatedItemEntity.spawn(server, dropped.getItem().copy(), player, dropped.position(), Vec3.ZERO, faceYaw);
            if (animated == null) {
                return InteractionResultHolder.fail(wand);
            }
            dropped.discard();
        } else {
            // the off-hand item is tossed out in front and lands on its new legs
            Vec3 from = player.position().add(look.x * 0.7, player.getEyeHeight() * 0.6, look.z * 0.7);
            Vec3 toss = new Vec3(look.x * 0.32, 0.28, look.z * 0.32);
            animated = AnimatedItemEntity.spawn(server, offhand.copy(), player, from, toss, player.getYRot());
            if (animated == null) {
                return InteractionResultHolder.fail(wand);
            }
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }

        // the price: one heart, flowing from the wielder into the item along a thread of light
        if (!creative) {
            player.setHealth(player.getHealth() - Balance.QUICKENING_HEALTH_COST);
        }
        Sfx.at(player, SoundEvents.PLAYER_HURT, 0.45F, 1.7F);
        Vec3 chest = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
        Vec3 heart = animated.position().add(0.0, 0.4, 0.0);
        int steps = Math.max(4, (int) (chest.distanceTo(heart) * 3.0));
        for (int i = 1; i < steps; i++) {
            Vec3 p = chest.lerp(heart, i / (double) steps);
            server.sendParticles(LIFE_THREAD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        AnimatedItemEntity.enforceCap(server, player, animated);
        player.displayClientMessage(Component.translatable("lacuna.quickening.awake", animated.getMainHandItem().getHoverName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.getCooldowns().addCooldown(this, Balance.QUICKENING_COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
        wand.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.success(wand);
    }

    /** The dropped item nearest the crosshair along the view (generous radius), not behind a wall. */
    @Nullable
    public static ItemEntity pickDroppedItem(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double range = Balance.QUICKENING_RANGE;
        BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double reach = wall.getType() == HitResult.Type.MISS ? range : eye.distanceTo(wall.getLocation()) + 0.6;
        double radius = Balance.QUICKENING_PICK_RADIUS;
        AABB box = new AABB(eye, eye.add(look.scale(reach))).inflate(radius + 0.5);
        ItemEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty())) {
            Vec3 rel = item.position().add(0.0, 0.15, 0.0).subtract(eye);
            double along = rel.dot(look);
            if (along < 0.0 || along > reach) {
                continue;
            }
            double off = rel.subtract(look.scale(along)).length();
            if (off > radius) {
                continue;
            }
            double score = along + off * 3.0;
            if (score < bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return best;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(ModItems.SILK_DUST.get());
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
    public int getEnchantmentValue(ItemStack stack) {
        return 10;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.quickening_wand.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.quickening_wand.use", Balance.ANIMATED_LIFETIME / 20)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.lacuna.quickening_wand.use2").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.lacuna.quickening_wand.use3", Balance.QUICKENING_MAX_ALIVE).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.quickening_wand.use4").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
