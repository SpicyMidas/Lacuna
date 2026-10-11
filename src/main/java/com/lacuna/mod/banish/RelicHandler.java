package com.lacuna.mod.banish;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.CoordMath;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Relics (spec 9.3): a player dying in the Forgotten Land has their drops scattered in the overworld at the
 * translated death position, as glowing, durable "Unreadable Relic" items that only the owner can pick up for
 * 10 minutes. Fallback: if anything fails, the drops stay in the Forgotten Land (vanilla behaviour).
 */
public final class RelicHandler {
    public static final String FEATURE = "relics";

    private RelicHandler() {}

    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ModDimensions.isForgottenLand(player.level())) {
            return;
        }
        if (!LacunaConfig.get(LacunaConfig.RELICS_ENABLED) || SafeRun.isDisabled(FEATURE) || event.getDrops().isEmpty()) {
            return;
        }
        ServerLevel fl = (ServerLevel) player.level();
        if (fl.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        boolean moved = SafeRun.call(FEATURE, () -> scatter(player, event.getDrops()), false);
        if (moved) {
            event.setCanceled(true);
        }
    }

    private static boolean scatter(ServerPlayer player, java.util.Collection<ItemEntity> drops) {
        ServerLevel overworld = player.server.overworld();
        double scale = LacunaConfig.get(LacunaConfig.FL_SCALE);
        BlockPos target = new BlockPos(CoordMath.toOverworld(player.getX(), scale), player.getBlockY(),
                CoordMath.toOverworld(player.getZ(), scale));
        BlockPos spot = SafeSpot.find(overworld, target, true);
        if (!overworld.hasChunkAt(spot)) {
            return false; // fallback: normal drops in the Forgotten Land
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity drop : drops) {
            if (!drop.getItem().isEmpty()) {
                stacks.add(drop.getItem().copy());
            }
        }
        long ownerUntil = overworld.getGameTime() + Balance.RELIC_OWNER_ONLY_TICKS;
        UUID owner = player.getUUID();
        for (ItemStack stack : stacks) {
            double dx = (overworld.random.nextDouble() - 0.5) * 2.0;
            double dz = (overworld.random.nextDouble() - 0.5) * 2.0;
            ItemEntity relic = new ItemEntity(overworld, spot.getX() + 0.5 + dx, spot.getY() + 0.2, spot.getZ() + 0.5 + dz, stack);
            relic.setInvulnerable(true);
            relic.lifespan = Balance.RELIC_LIFETIME_TICKS;
            relic.setGlowingTag(true);
            relic.setCustomName(Component.translatable("lacuna.relic.name"));
            relic.setCustomNameVisible(true);
            relic.setPickUpDelay(40);
            relic.setData(ModAttachments.RELIC_OWNER, owner.toString());
            relic.setData(ModAttachments.RELIC_OWNER_UNTIL, ownerUntil);
            overworld.addFreshEntity(relic);
        }
        player.sendSystemMessage(Component.translatable("lacuna.relic.scattered",
                CoordMath.coarse(spot.getX(), Balance.RELIC_COARSE_STEP), CoordMath.coarse(spot.getZ(), Balance.RELIC_COARSE_STEP)));
        return true;
    }

    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity item = event.getItemEntity();
        if (!item.hasData(ModAttachments.RELIC_OWNER)) {
            return;
        }
        String owner = item.getData(ModAttachments.RELIC_OWNER);
        long until = item.getData(ModAttachments.RELIC_OWNER_UNTIL);
        if (!owner.isEmpty() && item.level().getGameTime() < until && !owner.equals(event.getPlayer().getUUID().toString())) {
            event.setCanPickup(TriState.FALSE);
        }
    }
}
