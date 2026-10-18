package com.lacuna.mod.bag;

import com.lacuna.mod.util.SafeRun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Bagging entities. The interact events fire before the entity's own reaction, so an empty bag wins over villager
 * trading, mounting horses and boats, sitting pets or opening chest minecarts. Armour stands answer the "specific"
 * (interact-at) variant first, everything else the plain one; once bagged the bag is full and the second is ignored.
 */
public final class BagEvents {
    private BagEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(BagEvents::onInteractAt);
        bus.addListener(BagEvents::onInteract);
    }

    private static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        InteractionResult result = handle(event.getEntity(), event.getItemStack(), event.getTarget());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = handle(event.getEntity(), event.getItemStack(), event.getTarget());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    @Nullable
    private static InteractionResult handle(Player player, ItemStack stack, Entity target) {
        if (!(stack.getItem() instanceof ComicallyLargeBagItem) || BagCapture.isFull(stack) || player.isSpectator()
                || !(target instanceof LivingEntity || target instanceof VehicleEntity) || SafeRun.isDisabled(BagCapture.FEATURE)) {
            return null; // item frames, paintings and the like keep their usual behaviour
        }
        if (player instanceof ServerPlayer serverPlayer && !player.level().isClientSide()) {
            return SafeRun.call(BagCapture.FEATURE, () -> BagCapture.captureEntity(serverPlayer, stack, target), InteractionResult.FAIL);
        }
        return InteractionResult.SUCCESS;
    }
}
