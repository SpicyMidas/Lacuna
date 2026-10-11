package com.lacuna.mod.eclipse;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModEffects;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import com.lacuna.mod.util.Teleporter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Shadow Walk (spec 11.2): the player is temporarily switched to SPECTATOR and switched back when the effect ends.
 * The previous mode lives in a serializable attachment, so it is restored on expiry, removal (milk), death,
 * logout/login, dimension change and server stop. An operator who changed the game mode meanwhile always wins.
 * After restoring, a player stuck in blocks is moved to the nearest safe spot within 16 blocks, else the dash origin.
 */
public final class ShadowWalk {
    public static final String FEATURE = "shadow_walk";

    private ShadowWalk() {}

    public static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.SHADOW_WALK_ENABLED) && !SafeRun.isDisabled(FEATURE);
    }

    public static boolean isActive(ServerPlayer player) {
        return player.hasData(ModAttachments.SHADOW_WALK) && player.getData(ModAttachments.SHADOW_WALK).active();
    }

    public static void begin(ServerPlayer player, int ticks, BlockPos origin) {
        SafeRun.run(FEATURE, () -> {
            GameType current = player.gameMode.getGameModeForPlayer();
            long until = player.serverLevel().getGameTime() + ticks;
            if (isActive(player)) {
                ShadowWalkState old = player.getData(ModAttachments.SHADOW_WALK);
                player.setData(ModAttachments.SHADOW_WALK, new ShadowWalkState(old.previousMode(), Math.max(until, old.until()), old.origin()));
            } else {
                if (current == GameType.SPECTATOR) {
                    return; // already a spectator (operator choice): nothing to do
                }
                player.setData(ModAttachments.SHADOW_WALK, new ShadowWalkState(current.getName(), until, origin.asLong()));
                player.setGameMode(GameType.SPECTATOR);
            }
            player.addEffect(new MobEffectInstance(ModEffects.SHADOW_WALK, ticks, 0, false, false, true));
            for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0),
                    m -> m.getTarget() == player)) {
                mob.setTarget(null);
            }
        }, () -> restore(player));
    }

    /** Restores the remembered game mode (if we are still the ones who set SPECTATOR) and frees a stuck player. */
    public static void restore(ServerPlayer player) {
        if (!player.hasData(ModAttachments.SHADOW_WALK)) {
            return;
        }
        ShadowWalkState state = player.getData(ModAttachments.SHADOW_WALK);
        player.removeData(ModAttachments.SHADOW_WALK);
        if (!state.active()) {
            return;
        }
        if (player.hasEffect(ModEffects.SHADOW_WALK)) {
            player.removeEffect(ModEffects.SHADOW_WALK);
        }
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            return; // an operator changed the mode meanwhile: never override it
        }
        GameType previous = GameType.byName(state.previousMode(), GameType.SURVIVAL);
        player.setGameMode(previous);
        ServerLevel level = player.serverLevel();
        if (!level.noCollision(player, player.getBoundingBox())) {
            BlockPos here = player.blockPosition();
            BlockPos target = SafeSpot.findNear(level, here).orElseGet(() -> SafeSpot.find(level, BlockPos.of(state.origin()), false));
            Teleporter.teleport(player, level, Vec3.atBottomCenterOf(target), player.getYRot(), player.getXRot());
        }
    }

    private static void restoreQuietly(ServerPlayer player) {
        try {
            restore(player);
        } catch (RuntimeException e) {
            Lacuna.LOGGER.error("Could not restore the game mode of {} after Shadow Walk", player.getGameProfile().getName(), e);
            try {
                player.setGameMode(GameType.SURVIVAL);
            } catch (RuntimeException ignored) {
                // last resort failed; nothing else is safe to do here
            }
        }
    }

    // ------------------------------------------------------------------------------------------ events

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(ModAttachments.SHADOW_WALK)) {
            return;
        }
        ShadowWalkState state = player.getData(ModAttachments.SHADOW_WALK);
        if (!state.active() || player.serverLevel().getGameTime() >= state.until() || !enabled()) {
            restoreQuietly(player);
        }
    }

    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().is(ModEffects.SHADOW_WALK.getKey())) {
            restoreQuietly(player);
        }
    }

    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEffect().is(ModEffects.SHADOW_WALK.getKey())) {
            restoreQuietly(player);
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreQuietly(player);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreQuietly(player);
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreQuietly(player);
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreQuietly(player);
        }
    }

    /** Spectator teleports to players in other dimensions are not allowed while shadow walking. */
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isActive(player) && !Teleporter.isLacunaTransfer()) {
            event.setCanceled(true);
        }
    }

    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && isActive(player)) {
            event.setCanceled(true);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            restoreQuietly(player);
        }
    }
}
