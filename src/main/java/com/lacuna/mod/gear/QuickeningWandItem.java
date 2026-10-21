package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.Sfx;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
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

/**
 * Wand of Quickening: like the Pyrrhic Scepter it is paid for in health (2, refused when too weak, free in creative).
 * The dropped item under the crosshair (or, failing that, the item in the off hand, thrown out) wakes up as an
 * {@link AnimatedItemEntity} for 40 s, then falls asleep again as exactly the same stack. It wakes only when the spark
 * of life reaches it ({@link QuickeningBolt}): health, cooldown and wear are paid at the cast, the heart is given back
 * if the spark finds nothing left to wake.
 */
public class QuickeningWandItem extends Item {
    /** Client effect kind of the spark of life flying to the item. */
    public static final String BOLT_FX = "quickening_bolt";

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
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(wand);
        }

        Vec3 look = player.getViewVector(1.0F);
        ItemEntity target;
        boolean tossed = dropped == null;
        if (!tossed) {
            target = dropped;
            QuickeningBolt.freeze(target);
        } else {
            // the off-hand item is tossed out in front as a plain item first: the spark chases it and it wakes on the hit
            Vec3 from = player.position().add(look.x * 0.7, player.getEyeHeight() * 0.6, look.z * 0.7);
            target = new ItemEntity(server, from.x, from.y, from.z, offhand.copy(), look.x * 0.32, 0.28, look.z * 0.32);
            target.setThrower(player);
            QuickeningBolt.freeze(target);
            if (!server.addFreshEntity(target)) {
                return InteractionResultHolder.fail(wand);
            }
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }

        // the price, paid at the cast: one heart flows from the wielder into the spark (it flows back if the spark is lost)
        float paid = creative ? 0.0F : Balance.QUICKENING_HEALTH_COST;
        if (paid > 0.0F) {
            player.setHealth(player.getHealth() - paid);
        }
        Sfx.at(player, SoundEvents.PLAYER_HURT, 0.45F, 1.7F);
        // the spark of life flies from the wand to the item (client shader orb, client/gear/QuickeningFx); both sides
        // use the same flight time, so the item wakes on the very tick the orb bursts on it
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
        Vec3 tip = player.getEyePosition().add(look.scale(0.7)).add(right.scale(hand == InteractionHand.MAIN_HAND ? 0.35 : -0.35))
                .add(0.0, -0.3, 0.0);
        Vec3 heart = target.position().add(0.0, 0.25, 0.0);
        int flight = QuickeningBolt.flightTicks(tip.distanceTo(heart), tossed ? QuickeningBolt.TOSS_MIN_FLIGHT : QuickeningBolt.MIN_FLIGHT);
        LacunaFx.send(server, tip, FxPayload.line(BOLT_FX, tip, heart).withEntity(target.getId())
                .withParams(player.getId(), player.getRandom().nextFloat() * Mth.TWO_PI, flight));
        Sfx.at(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.6F);
        QuickeningBolt.launch(server, serverPlayer, target, flight, paid, tossed);
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
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty()
                && !QuickeningBolt.isFrozen(e))) {
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
        tooltip.add(Component.translatable("item.lacuna.quickening_wand.use5").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.lacuna.quickening_wand.use3", Balance.QUICKENING_MAX_ALIVE).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.quickening_wand.use4").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
