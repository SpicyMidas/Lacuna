package com.lacuna.mod.star;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Starcaller Trumpet: blown like a goat horn, it calls a small falling star down on whatever is under the crosshair
 * (up to {@link Starfall#RANGE} blocks). A creature in the sights is locked on: the star follows it as it falls. A
 * block makes the star land right there. Nothing in reach: the call fails, with no cooldown and no wear.
 * 32 uses, 30 s cooldown, repaired at an anvil with Star Fragments (vanilla repair material, +8 uses each).
 */
public class StarcallerTrumpetItem extends Item {
    public StarcallerTrumpetItem(Properties properties) {
        super(properties);
    }

    /** What the call aims at: a creature to follow, or a fixed point. */
    public record Aim(Vec3 point, @Nullable LivingEntity target) {}

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return InteractionResultHolder.pass(stack);
        }
        Aim aim = aim(level, player);
        if (aim == null) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("lacuna.starcaller.no_target"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server) {
            boolean called = SafeRun.call(Starfall.FEATURE, () -> FallingStarEntity.call(server, player, aim), false);
            if (!called) {
                player.displayClientMessage(Component.translatable("lacuna.starcaller.too_far"), true);
                return InteractionResultHolder.fail(stack);
            }
            player.getCooldowns().addCooldown(this, Starfall.COOLDOWN);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            player.awardStat(Stats.ITEM_USED.get(this));
            level.gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), GameEvent.Context.of(player));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * The creature or block under the crosshair within {@link Starfall#RANGE}, or null when the call reaches nothing
     * (open sky). Creatures are picked with a little leniency so distant ones can be locked on.
     */
    @Nullable
    public static Aim aim(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(Starfall.RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        double best = eye.distanceToSqr(limit);
        LivingEntity target = null;
        AABB sweep = new AABB(eye, limit).inflate(1.5);
        for (Entity e : level.getEntities(player, sweep, e -> e instanceof LivingEntity living && living.isAlive()
                && !e.isSpectator() && e.isPickable() && !player.isPassengerOfSameVehicle(e))) {
            AABB box = e.getBoundingBox().inflate(e.getPickRadius() + Starfall.PICK_LENIENCY);
            var hit = box.clip(eye, limit);
            if (box.contains(eye)) {
                continue; // standing inside the creature's box: it is not what we look at
            }
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < best) {
                    best = d;
                    target = (LivingEntity) e;
                }
            }
        }
        if (target != null) {
            return new Aim(target.position(), target);
        }
        if (block.getType() == HitResult.Type.MISS) {
            return null;
        }
        return new Aim(block.getLocation(), null);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Starfall.USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(ModItems.STAR_FRAGMENT.get());
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.starcaller_trumpet.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.starcaller_trumpet.use", (int) Starfall.RANGE).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.lacuna.starcaller_trumpet.lock").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.starcaller_trumpet.blast", (int) Starfall.BLAST_DAMAGE)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.starcaller_trumpet.crater").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lacuna.starcaller_trumpet.cooldown", Starfall.COOLDOWN / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lacuna.starcaller_trumpet.repair").withStyle(ChatFormatting.DARK_GRAY));
    }
}
