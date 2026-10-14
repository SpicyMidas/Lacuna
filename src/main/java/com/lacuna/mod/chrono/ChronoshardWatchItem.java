package com.lacuna.mod.chrono;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import com.lacuna.mod.util.Teleporter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Chronoshard Pocket Watch (spec M6): records {position, view, health, food} every 2400 ticks into a ring of 3;
 * right click rewinds to the newest snapshot that is >= 400 ticks old and taken in the current dimension.
 * Chronal Shards inserted from the other hand shorten the cooldown (6000 - 600 per shard, max 5).
 */
public class ChronoshardWatchItem extends Item {
    public static final String FEATURE = "pocket_watch";

    public ChronoshardWatchItem(Properties properties) {
        super(properties);
    }

    public static List<WatchSnapshot> snapshots(ItemStack stack) {
        return stack.getOrDefault(ModComponents.WATCH_SNAPSHOTS.get(), List.of());
    }

    public static int shards(ItemStack stack) {
        return stack.getOrDefault(ModComponents.CHRONAL_SHARDS.get(), 0);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player) || !LacunaConfig.get(LacunaConfig.WATCH_ENABLED)) {
            return;
        }
        List<WatchSnapshot> list = snapshots(stack);
        long now = level.getGameTime();
        if (!list.isEmpty() && now - list.get(list.size() - 1).gameTime() < Balance.WATCH_SNAPSHOT_INTERVAL) {
            return;
        }
        List<WatchSnapshot> next = new ArrayList<>(list);
        next.add(new WatchSnapshot(level.dimension(), player.getX(), player.getY(), player.getZ(), player.getYRot(),
                player.getXRot(), player.getHealth(), player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(), now));
        while (next.size() > Balance.WATCH_RING_SIZE) {
            next.remove(0);
        }
        stack.set(ModComponents.WATCH_SNAPSHOTS.get(), List.copyOf(next));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.WATCH_ENABLED)) {
            return InteractionResultHolder.fail(stack);
        }
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (other.is(ModItems.CHRONAL_SHARD.get())) {
            if (level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            int shards = shards(stack);
            if (shards >= Balance.WATCH_MAX_SHARDS) {
                player.displayClientMessage(Component.translatable("lacuna.watch.full"), true);
                return InteractionResultHolder.fail(stack);
            }
            stack.set(ModComponents.CHRONAL_SHARDS.get(), shards + 1);
            other.consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
            player.displayClientMessage(Component.translatable("lacuna.watch.inserted", shards + 1, Balance.WATCH_MAX_SHARDS), true);
            return InteractionResultHolder.success(stack);
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        boolean ok = SafeRun.call(FEATURE, () -> rewind(serverPlayer, stack, hand), false);
        return ok ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }

    private boolean rewind(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        List<WatchSnapshot> list = snapshots(stack);
        long now = player.serverLevel().getGameTime();
        int index = -1;
        for (int i = list.size() - 1; i >= 0; i--) {
            WatchSnapshot s = list.get(i);
            if (now - s.gameTime() >= Balance.WATCH_MIN_AGE) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            player.displayClientMessage(Component.translatable("lacuna.watch.too_fresh"), true);
            return false;
        }
        WatchSnapshot snap = list.get(index);
        if (snap.dimension() != player.level().dimension()) {
            player.displayClientMessage(Component.translatable("lacuna.watch.other_dimension"), true);
            return false; // never rewinds across dimensions (incl. the Forgotten Land boundary)
        }
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        BlockPos target = BlockPos.containing(snap.x(), snap.y(), snap.z());
        SafeSpot.loadChunk(level, target);
        Vec3 dest = SafeSpot.isSafe(level, target) ? new Vec3(snap.x(), snap.y(), snap.z())
                : Vec3.atBottomCenterOf(SafeSpot.find(level, target, false));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 40, 0.4, 0.8, 0.4, 0.05);
        Teleporter.teleport(player, level, dest, snap.yRot(), snap.xRot());
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1, dest.z, 40, 0.4, 0.8, 0.4, 0.05);
        com.lacuna.mod.fx.LacunaFx.send(level, dest, com.lacuna.mod.network.FxPayload.line("rewind", dest, from));
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);

        player.setHealth(Math.max(player.getHealth(), Math.min(player.getMaxHealth(), snap.health())));
        player.getFoodData().setFoodLevel(Math.max(0, Math.min(20, snap.food())));
        player.getFoodData().setSaturation(Math.max(0.0F, Math.min(snap.food(), snap.saturation())));
        player.clearFire();
        clearRecentHarmful(player, now);

        stack.set(ModComponents.WATCH_SNAPSHOTS.get(), List.copyOf(list.subList(0, index)));
        int cooldown = Balance.WATCH_BASE_COOLDOWN - Balance.WATCH_COOLDOWN_PER_SHARD * Math.min(Balance.WATCH_MAX_SHARDS, shards(stack));
        player.getCooldowns().addCooldown(this, cooldown);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return true;
    }

    private static void clearRecentHarmful(ServerPlayer player, long now) {
        HarmfulEffectLog log = player.hasData(ModAttachments.HARMFUL_EFFECTS) ? player.getData(ModAttachments.HARMFUL_EFFECTS)
                : HarmfulEffectLog.EMPTY;
        for (MobEffectInstance instance : List.copyOf(player.getActiveEffects())) {
            Holder<MobEffect> effect = instance.getEffect();
            if (effect.value().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }
            String id = effect.unwrapKey().map(k -> k.location().toString()).orElse("");
            Long applied = log.applied().get(id);
            if (applied != null && now - applied <= Balance.WATCH_HARMFUL_WINDOW) {
                player.removeEffect(effect);
            }
        }
    }

    /** Remembers when harmful effects were applied (MobEffectEvent.Added on players). */
    public static void recordHarmful(ServerPlayer player, Holder<MobEffect> effect) {
        if (effect.value().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        ResourceLocation id = effect.unwrapKey().map(k -> k.location()).orElse(null);
        if (id == null) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        HarmfulEffectLog log = player.hasData(ModAttachments.HARMFUL_EFFECTS) ? player.getData(ModAttachments.HARMFUL_EFFECTS)
                : HarmfulEffectLog.EMPTY;
        player.setData(ModAttachments.HARMFUL_EFFECTS, log.with(id.toString(), now, now, Balance.WATCH_HARMFUL_WINDOW));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(com.lacuna.mod.registry.ModItems.UMBRAL_STEEL_INGOT.get());
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
        tooltip.add(Component.translatable("item.lacuna.chronoshard_pocket_watch.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("lacuna.watch.snapshots", snapshots(stack).size(), Balance.WATCH_RING_SIZE)
                .withStyle(ChatFormatting.DARK_AQUA));
        int shards = shards(stack);
        tooltip.add(Component.translatable("lacuna.watch.shards", shards, Balance.WATCH_MAX_SHARDS).withStyle(ChatFormatting.LIGHT_PURPLE));
        int cooldownSeconds = (Balance.WATCH_BASE_COOLDOWN - Balance.WATCH_COOLDOWN_PER_SHARD * shards) / 20;
        tooltip.add(Component.translatable("lacuna.tooltip.cooldown", cooldownSeconds).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
