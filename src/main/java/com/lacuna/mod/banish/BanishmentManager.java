package com.lacuna.mod.banish;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.CoordMath;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import com.lacuna.mod.util.Teleporter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Banishment to the Forgotten Land and timed return (spec 9.2). All entry points are server-side and wrapped in
 * {@link SafeRun}: on failure the feature disables itself and every banished online player is sent home.
 */
public final class BanishmentManager {
    public static final String FEATURE = "banishment";
    private static final int MAX_MISSING_CHECKS = 15;
    private static final int MELEE_DELAY = 5;
    private static final TicketType<ChunkPos> RETURN_TICKET = TicketType.create("lacuna_banish_return",
            Comparator.comparingLong(ChunkPos::toLong), 300);

    /** Volatile queue of melee banishments (applied a few ticks after the knockback). */
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(UUID target, ResourceKey<Level> dimension, long dueTick) {}

    private BanishmentManager() {}

    // ------------------------------------------------------------------------------------------ queries

    public static boolean enabled(MinecraftServer server) {
        return LacunaConfig.get(LacunaConfig.FL_ENABLED) && !SafeRun.isDisabled(FEATURE)
                && ModDimensions.forgottenLand(server) != null;
    }

    public static boolean canBanish(Entity target) {
        if (!target.isAlive() || target.level().dimension() != Level.OVERWORLD) {
            return false;
        }
        if (target.getType().is(ModTags.Entities.CANNOT_BE_BANISHED)) {
            return false;
        }
        if (target instanceof Player player) {
            MinecraftServer server = player.getServer();
            return !player.isSpectator() && server != null && server.isPvpAllowed()
                    && LacunaConfig.get(LacunaConfig.BANISH_ALLOW_PLAYERS);
        }
        return target instanceof LivingEntity;
    }

    public static Optional<BanishmentSavedData.Record> recordOf(Entity entity) {
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return Optional.empty();
        }
        BanishmentSavedData data = BanishmentSavedData.get(server);
        String id = entity.hasData(ModAttachments.BANISHMENT_ID) ? entity.getData(ModAttachments.BANISHMENT_ID) : "";
        if (!id.isEmpty()) {
            try {
                Optional<BanishmentSavedData.Record> r = data.get(UUID.fromString(id));
                if (r.isPresent()) {
                    return r;
                }
            } catch (IllegalArgumentException ignored) {
                // corrupt attachment: fall through to the uuid lookup
            }
        }
        return data.byEntity(entity.getUUID());
    }

    // ------------------------------------------------------------------------------------------ item entry points

    /** Totem right click: enter from the overworld, leave early from the Forgotten Land. */
    public static boolean useTotemSelf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        if (!enabled(server)) {
            player.displayClientMessage(Component.translatable("lacuna.message.disabled"), true);
            return false;
        }
        ResourceKey<Level> dim = player.level().dimension();
        if (dim == Level.OVERWORLD) {
            return SafeRun.call(FEATURE, () -> banish(player) != null, false, () -> rescueAll(server));
        }
        if (dim == ModDimensions.FORGOTTEN_LAND) {
            return SafeRun.call(FEATURE, () -> returnEntity(player) != null, false, () -> rescueAll(server));
        }
        player.displayClientMessage(Component.translatable("lacuna.totem.silent"), true);
        return false;
    }

    /** Totem melee hit: strong knockback now, banishment a few ticks later. */
    public static boolean queueBanish(ServerPlayer attacker, LivingEntity target) {
        MinecraftServer server = attacker.getServer();
        if (server == null || !enabled(server)) {
            return false;
        }
        if (!canBanish(target)) {
            attacker.displayClientMessage(Component.translatable("lacuna.totem.resists"), true);
            return false;
        }
        target.knockback(Balance.TOTEM_KNOCKBACK, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        PENDING.add(new Pending(target.getUUID(), target.level().dimension(), server.getTickCount() + MELEE_DELAY));
        return true;
    }

    // ------------------------------------------------------------------------------------------ core transfers

    /** Sends an overworld entity to the Forgotten Land with a drifting timer. Returns the moved entity or null. */
    @Nullable
    public static Entity banish(Entity entity) {
        MinecraftServer server = entity.getServer();
        if (server == null || !(entity.level() instanceof ServerLevel from) || from.dimension() != Level.OVERWORLD) {
            return null;
        }
        ServerLevel fl = ModDimensions.forgottenLand(server);
        if (fl == null) {
            return null;
        }
        double scale = LacunaConfig.get(LacunaConfig.FL_SCALE);
        BlockPos origin = entity.blockPosition();
        BlockPos target = new BlockPos(CoordMath.toForgotten(entity.getX(), scale), origin.getY(),
                CoordMath.toForgotten(entity.getZ(), scale));
        BlockPos safe = SafeSpot.find(fl, target, true);

        double min = LacunaConfig.driftMin();
        double max = LacunaConfig.driftMax();
        double drift = min + entity.getRandom().nextDouble() * (max - min);
        long now = server.overworld().getGameTime();
        long returnAt = now + Math.round(LacunaConfig.get(LacunaConfig.FL_TIMER_TICKS) * drift);

        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        effects(from, entity.position());
        Entity moved = Teleporter.teleport(entity, fl, Vec3.atBottomCenterOf(safe), entity.getYRot(), entity.getXRot());
        if (moved == null) {
            return null;
        }
        UUID id = UUID.randomUUID();
        moved.setData(ModAttachments.BANISHMENT_ID, id.toString());
        BanishmentSavedData.get(server).put(new BanishmentSavedData.Record(id, Level.OVERWORLD, origin, returnAt, drift,
                moved.getUUID(), moved instanceof Player, safe));
        effects(fl, moved.position());
        BanishFx.arrival(fl, moved); // the totem goes off for whoever lands, like a totem of undying
        if (moved instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("lacuna.banish.arrived"), false);
        }
        Lacuna.LOGGER.debug("Banished {} ({}) until game time {}", moved.getName().getString(), moved.getUUID(), returnAt);
        return moved;
    }

    /** Brings an entity back from the Forgotten Land to the translated overworld position. */
    @Nullable
    public static Entity returnEntity(Entity entity) {
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return null;
        }
        ServerLevel overworld = server.overworld();
        BanishmentSavedData data = BanishmentSavedData.get(server);
        Optional<BanishmentSavedData.Record> record = recordOf(entity);
        double scale = LacunaConfig.get(LacunaConfig.FL_SCALE);
        int y = record.map(r -> r.originPos.getY()).orElse(entity.getBlockY());
        BlockPos target = new BlockPos(CoordMath.toOverworld(entity.getX(), scale), y, CoordMath.toOverworld(entity.getZ(), scale));
        BlockPos safe = SafeSpot.find(overworld, target, record.isEmpty());

        if (entity.level() instanceof ServerLevel from) {
            effects(from, entity.position());
        }
        Entity moved = Teleporter.teleport(entity, overworld, Vec3.atBottomCenterOf(safe), entity.getYRot(), entity.getXRot());
        if (moved == null) {
            return null;
        }
        moved.removeData(ModAttachments.BANISHMENT_ID);
        record.ifPresent(r -> data.remove(r.id));
        effects(overworld, moved.position());
        if (moved instanceof ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
            player.setData(ModAttachments.ARRIVAL_PROTECTION, overworld.getGameTime() + Balance.ARRIVAL_PROTECTION_TICKS);
            player.displayClientMessage(Component.translatable("lacuna.banish.returned"), true);
        }
        return moved;
    }

    /** Both ends of every crossing: Lacuna's own particles in the totem's palette, the rift, the sounds ({@link BanishFx}). */
    private static void effects(ServerLevel level, Vec3 pos) {
        BanishFx.travel(level, pos);
    }

    /** SafeRun restore step: every online player inside the Forgotten Land goes home, without the timer logic. */
    public static void rescueAll(MinecraftServer server) {
        ServerLevel fl = ModDimensions.forgottenLand(server);
        if (fl == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(fl.players())) {
            try {
                BlockPos spawn = SafeSpot.find(server.overworld(), server.overworld().getSharedSpawnPos(), true);
                Teleporter.teleport(player, server.overworld(), Vec3.atBottomCenterOf(spawn), player.getYRot(), player.getXRot());
                player.removeData(ModAttachments.BANISHMENT_ID);
            } catch (RuntimeException e) {
                Lacuna.LOGGER.error("Could not rescue {} from the Forgotten Land", player.getGameProfile().getName(), e);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ events

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!LacunaConfig.get(LacunaConfig.FL_ENABLED) || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        SafeRun.run(FEATURE, () -> tick(server), () -> rescueAll(server));
    }

    private static void tick(MinecraftServer server) {
        processPending(server);
        if (server.getTickCount() % Balance.FL_CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel fl = ModDimensions.forgottenLand(server);
        if (fl == null) {
            return;
        }
        BanishmentSavedData data = BanishmentSavedData.get(server);
        long now = server.overworld().getGameTime();
        for (BanishmentSavedData.Record r : data.all()) {
            boolean due = now >= r.returnGameTime;
            if (r.isPlayer) {
                ServerPlayer player = server.getPlayerList().getPlayer(r.entityUuid);
                if (player == null) {
                    continue; // offline: the timer keeps running on game time, checked again on login
                }
                if (player.level() != fl) {
                    data.remove(r.id); // died, or moved out by an operator
                    player.removeData(ModAttachments.BANISHMENT_ID);
                    continue;
                }
                if (due) {
                    returnEntity(player);
                } else {
                    long left = (r.returnGameTime - now) / 20;
                    // where the walk home would end: 1 block here is FL_SCALE (12) blocks in the overworld
                    double scale = LacunaConfig.get(LacunaConfig.FL_SCALE);
                    player.displayClientMessage(Component.translatable("lacuna.banish.countdown",
                            String.format("%d:%02d", left / 60, left % 60), CoordMath.toOverworld(player.getX(), scale),
                            CoordMath.toOverworld(player.getZ(), scale)), true);
                }
                continue;
            }
            Entity entity = fl.getEntity(r.entityUuid);
            if (entity != null) {
                r.lastKnownPos = entity.blockPosition();
                r.missingChecks = 0;
                data.touch();
                if (due) {
                    returnEntity(entity);
                }
                continue;
            }
            if (server.overworld().getEntity(r.entityUuid) != null) {
                data.remove(r.id); // already back (command or other mod)
                continue;
            }
            if (due) {
                // Load the mob's last known chunk once (expiring ticket) and look again next check.
                fl.getChunkSource().addRegionTicket(RETURN_TICKET, new ChunkPos(r.lastKnownPos), 1, new ChunkPos(r.lastKnownPos));
                r.missingChecks++;
                data.touch();
                if (r.missingChecks > MAX_MISSING_CHECKS) {
                    data.remove(r.id); // the banished mob no longer exists
                }
            }
        }
    }

    private static void processPending(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long tick = server.getTickCount();
        Iterator<Pending> it = PENDING.iterator();
        List<Pending> ready = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            if (tick >= p.dueTick()) {
                ready.add(p);
                it.remove();
            }
        }
        for (Pending p : ready) {
            ServerLevel level = server.getLevel(p.dimension());
            Entity target = level == null ? null : level.getEntity(p.target());
            if (target != null && canBanish(target)) {
                banish(target);
            }
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            String id = player.hasData(ModAttachments.BANISHMENT_ID) ? player.getData(ModAttachments.BANISHMENT_ID) : "";
            if (id.isEmpty()) {
                return;
            }
            Optional<BanishmentSavedData.Record> record = recordOf(player);
            boolean inFl = ModDimensions.isForgottenLand(player.level());
            if (record.isEmpty() && !inFl) {
                player.removeData(ModAttachments.BANISHMENT_ID); // stale tag
            }
            // due players inside the land are returned by the next 20-tick check (never during login itself)
        });
    }

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !entity.hasData(ModAttachments.BANISHMENT_ID)
                || entity.getData(ModAttachments.BANISHMENT_ID).isEmpty()) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            recordOf(entity).ifPresent(r -> BanishmentSavedData.get(entity.getServer()).remove(r.id));
            entity.removeData(ModAttachments.BANISHMENT_ID);
        });
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasData(ModAttachments.ARRIVAL_PROTECTION)) {
            return;
        }
        long until = entity.getData(ModAttachments.ARRIVAL_PROTECTION);
        if (until != 0L && !entity.level().isClientSide()) {
            if (entity.level().getGameTime() < until) {
                event.setCanceled(true);
            } else {
                entity.removeData(ModAttachments.ARRIVAL_PROTECTION);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
