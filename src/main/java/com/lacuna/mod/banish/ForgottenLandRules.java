package com.lacuna.mod.banish;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.util.Teleporter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Rules inside the Forgotten Land (spec 9.1): you cannot leave except by timer, totem or death; operator commands
 * are never blocked; beds and respawn anchors do not work (and never explode).
 */
public final class ForgottenLandRules {
    /** Server tick on which an operator-level command was last executed (operator commands are never blocked). */
    private static long lastOpCommandTick = Long.MIN_VALUE;

    private ForgottenLandRules() {}

    public static void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (source.hasPermission(2)) {
            lastOpCommandTick = source.getServer().getTickCount();
        }
    }

    private static boolean operatorCommandRunning(MinecraftServer server) {
        return server != null && server.getTickCount() == lastOpCommandTick;
    }

    private static boolean sealed(Entity entity) {
        return LacunaConfig.get(LacunaConfig.FL_ENABLED) && !entity.level().isClientSide()
                && ModDimensions.isForgottenLand(entity.level()) && !Teleporter.isLacunaTransfer()
                && !operatorCommandRunning(entity.getServer());
    }

    public static void onTravel(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (sealed(entity)) {
            event.setCanceled(true);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("lacuna.fl.cannot_leave"), true);
            }
        }
    }

    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        if (sealed(event.getEntity())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("lacuna.fl.cannot_leave"), true);
        }
    }

    public static void onChorusFruit(EntityTeleportEvent.ChorusFruit event) {
        if (sealed(event.getEntity())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                player.displayClientMessage(Component.translatable("lacuna.fl.cannot_leave"), true);
            }
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!ModDimensions.isForgottenLand(player.level()) || !LacunaConfig.get(LacunaConfig.FL_ENABLED)) {
            return;
        }
        BlockState state = player.level().getBlockState(event.getPos());
        if (state.getBlock() instanceof BedBlock || state.getBlock() instanceof RespawnAnchorBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!player.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("lacuna.fl.no_rest"), true);
            }
        }
    }

    /** The Totem of Banishing (and other Lacuna relics) cannot be merged item+item at an anvil. */
    public static void onAnvil(AnvilUpdateEvent event) {
        if (!event.getLeft().isEmpty() && event.getLeft().is(event.getRight().getItem())
                && event.getLeft().getItem() instanceof NoAnvilMerge) {
            event.setCanceled(true);
        }
    }

    /** Marker for items whose copies must not be combined at an anvil. */
    public interface NoAnvilMerge {}
}
