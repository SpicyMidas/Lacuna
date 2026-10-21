package com.lacuna.mod.eclipse;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModEffects;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import com.lacuna.mod.util.Teleporter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * Shadow Walk (spec 11.2): the Shadow Walk effect temporarily switches a player to SPECTATOR (a shadow that passes
 * through walls, unseen and untargetable) and back when the effect ends - whether it comes from the Eclipser (which
 * grants it only once its dash has finished moving the player, {@link EclipseDash}) or from /effect.
 * The previous mode lives in a serializable attachment, so it is restored on expiry, removal (milk), death,
 * logout/login, dimension change and server stop. An operator who changed the game mode meanwhile always wins.
 * After restoring, a player stuck in blocks is moved to the nearest safe spot within 16 blocks, else the dash origin.
 * While shadow walking, a click possesses a creature or player ({@link Possession}); the walk clock stands still
 * meanwhile ({@link #suspend}/{@link #resume}).
 */
public final class ShadowWalk {
    public static final String FEATURE = "shadow_walk";
    private static final String FX_FEATURE = "shadow_walk_fx";
    /** True while {@link #suspend}/{@link #resume} swap the effect themselves (not a walk starting or ending). */
    private static boolean swapping;

    private ShadowWalk() {}

    public static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.SHADOW_WALK_ENABLED) && !SafeRun.isDisabled(FEATURE);
    }

    public static boolean isActive(ServerPlayer player) {
        return player.hasData(ModAttachments.SHADOW_WALK) && player.getData(ModAttachments.SHADOW_WALK).active();
    }

    /** Eclipser entry point: the walk starts where the dash ended and is carried by the effect from here on. */
    public static void begin(ServerPlayer player, int ticks, BlockPos origin) {
        SafeRun.run(FEATURE, () -> {
            if (!engage(player, player.serverLevel().getGameTime() + ticks, origin)) {
                return;
            }
            player.addEffect(new MobEffectInstance(ModEffects.SHADOW_WALK, ticks, 0, false, false, true));
        }, () -> restore(player));
    }

    /**
     * Switches {@code player} into the shadow spectator state until game time {@code until} (or extends the current
     * walk). Returns false when the player already is a spectator by an operator's choice, which is left alone.
     */
    private static boolean engage(ServerPlayer player, long until, BlockPos origin) {
        if (isActive(player)) {
            ShadowWalkState old = player.getData(ModAttachments.SHADOW_WALK);
            player.setData(ModAttachments.SHADOW_WALK, new ShadowWalkState(old.previousMode(), Math.max(until, old.until()), old.origin()));
        } else {
            GameType current = player.gameMode.getGameModeForPlayer();
            if (current == GameType.SPECTATOR) {
                return false;
            }
            player.setData(ModAttachments.SHADOW_WALK, new ShadowWalkState(current.getName(), until, origin.asLong()));
            player.setGameMode(GameType.SPECTATOR);
        }
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0),
                m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
        return true;
    }

    /** Restores the remembered game mode (if we are still the ones who set SPECTATOR) and frees a stuck player. */
    public static void restore(ServerPlayer player) {
        Possession.onWalkEnding(player);
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
        endBurst(player);
    }

    /**
     * Shadow Possession stops the walk clock: the walk (and its effect) last until {@link #resume}. Returns the ticks
     * that were left (-1 for an endless walk).
     */
    static int suspend(ServerPlayer player) {
        ShadowWalkState state = player.getData(ModAttachments.SHADOW_WALK);
        long now = player.serverLevel().getGameTime();
        int left = state.until() == Long.MAX_VALUE ? -1 : (int) Mth.clamp(state.until() - now, 0L, Integer.MAX_VALUE);
        player.setData(ModAttachments.SHADOW_WALK, new ShadowWalkState(state.previousMode(), Long.MAX_VALUE, state.origin()));
        swapping = true;
        try {
            player.addEffect(new MobEffectInstance(ModEffects.SHADOW_WALK, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        } finally {
            swapping = false;
        }
        return left;
    }

    /** The walk clock runs again after a possession, with {@code left} ticks (at least a moment; -1 = endless). */
    static void resume(ServerPlayer player, int left) {
        if (!isActive(player)) {
            return;
        }
        int ticks = left < 0 ? -1 : Math.max(left, Balance.POSSESS_RESUME_MIN_TICKS);
        ShadowWalkState state = player.getData(ModAttachments.SHADOW_WALK);
        long until = ticks < 0 ? Long.MAX_VALUE : player.serverLevel().getGameTime() + ticks;
        player.setData(ModAttachments.SHADOW_WALK, new ShadowWalkState(state.previousMode(), until, state.origin()));
        swapping = true;
        try {
            player.removeEffect(ModEffects.SHADOW_WALK);
            player.addEffect(new MobEffectInstance(ModEffects.SHADOW_WALK, ticks < 0 ? MobEffectInstance.INFINITE_DURATION : ticks, 0, false, false, true));
        } finally {
            swapping = false;
        }
    }

    /** The shadows scatter when Shadow Walk ends (purely cosmetic, never allowed to break the restore). */
    private static void endBurst(ServerPlayer player) {
        SafeRun.run(FX_FEATURE, () -> {
            ServerLevel level = player.serverLevel();
            Vec3 pos = player.position();
            LacunaFx.send(level, pos, FxPayload.at("shadow_walk_end", pos).withEntity(player.getId()));
            level.playSound(null, pos.x, pos.y + 1.0, pos.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 0.7F);
            level.playSound(null, pos.x, pos.y + 1.0, pos.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.6F);
        });
    }

    /** Soft shadow motes drift around a shadow walker; sent to everyone but the walker (it would fog their view). */
    private static void motes(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.getGameTime() % 2 != 0) {
            return;
        }
        Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
        for (ServerPlayer other : level.players()) {
            if (other != player && other.distanceToSqr(c) < 48 * 48) {
                level.sendParticles(other, ModParticles.SHADOW_MOTE.get(), false, c.x, c.y, c.z, 2, 0.35, 0.6, 0.35, 0.01);
                if (level.random.nextInt(3) == 0) {
                    level.sendParticles(other, ModParticles.CRIMSON_SPARK.get(), false, c.x, c.y, c.z, 1, 0.3, 0.5, 0.3, 0.02);
                }
            }
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
        } else if (!SafeRun.isDisabled(FX_FEATURE)) {
            SafeRun.run(FX_FEATURE, () -> motes(player));
        }
    }

    /**
     * The effect itself carries the walk: however it is given (the Eclipser, /effect, another mod), a player who gains
     * Shadow Walk becomes a shadow spectator for exactly its duration (infinite effects last until removed).
     */
    public static void onEffectAdded(MobEffectEvent.Added event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (swapping || !(event.getEntity() instanceof ServerPlayer player) || effect == null
                || !effect.getEffect().is(ModEffects.SHADOW_WALK.getKey()) || !enabled()) {
            return;
        }
        long until = effect.isInfiniteDuration() ? Long.MAX_VALUE : player.serverLevel().getGameTime() + effect.getDuration();
        SafeRun.run(FEATURE, () -> engage(player, until, player.blockPosition()), () -> restore(player));
    }

    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().is(ModEffects.SHADOW_WALK.getKey())) {
            restoreQuietly(player);
        }
    }

    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (!swapping && event.getEntity() instanceof ServerPlayer player && event.getEffect().is(ModEffects.SHADOW_WALK.getKey())) {
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
