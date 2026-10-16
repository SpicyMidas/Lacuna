package com.lacuna.mod.banish;

import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Forgotten Portal: eight {@link ForgottenPortalFrameBlock}s, two per side with the corners empty, around a 2x2
 * opening on one level (the End portal's ring, one size smaller). The Totem of Banishing lights a complete ring like
 * flint and steel lights a nether portal. Like the End portal, whatever touches the portal plane crosses at once
 * (players and every creature that may be banished) with the banishment system: banished from the overworld (timer,
 * 1:12 coordinates, safe spot), returned from the Forgotten Land. The transfer runs at the end of the server tick in
 * which the portal was touched, never inside a block callback; the vanilla portal cooldown keeps the new arrival from
 * bouncing straight back. A crossing player is sent {@link #CROSSING_FX} first, which the client turns into a
 * full-screen fade through the swirling void (client/portal/PortalTransition).
 */
public final class ForgottenPortals {
    public static final String FEATURE = "forgotten_portal";
    /**
     * FxPayload kind sent to a crossing player right before the transfer; {@code p0} = 1 (crossing now) or -1 (the
     * transfer failed: lift the void again).
     */
    public static final String CROSSING_FX = "forgotten_crossing";
    /** Entities that touched a portal during this tick, sent through at its end (in the order they touched). */
    private static final Map<UUID, Entry> ENTERED = new LinkedHashMap<>();

    private record Entry(ResourceKey<Level> dimension, BlockPos portal) {}

    enum Outcome { OPENED, ALREADY_OPEN, BLOCKED, INCOMPLETE }

    private ForgottenPortals() {}

    public static boolean isFrame(BlockState state) {
        return state.getBlock() instanceof ForgottenPortalFrameBlock;
    }

    public static boolean isPortal(BlockState state) {
        return state.getBlock() instanceof ForgottenPortalBlock;
    }

    /** True when a portal block touches pos on a horizontal side. */
    public static boolean bordersPortal(LevelAccessor level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (isPortal(level.getBlockState(pos.relative(d)))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ the ring

    /** The 2x2 opening whose north-west cell is {@code min}. */
    static List<BlockPos> opening(BlockPos min) {
        return List.of(min, min.east(), min.south(), min.south().east());
    }

    /** The eight frames around that opening: two on each side, corners left empty. */
    static List<BlockPos> ring(BlockPos min) {
        return List.of(min.north(), min.east().north(), min.south(2), min.east().south(2),
                min.west(), min.south().west(), min.east(2), min.south().east(2));
    }

    /** North-west opening cells of every ring the frame at {@code frame} could belong to (4 sides x 2 places). */
    static List<BlockPos> candidates(BlockPos frame) {
        List<BlockPos> out = new ArrayList<>(8);
        for (int s = 0; s >= -1; s--) {
            out.add(frame.offset(s, 0, 1)); // the frame is on the north side of the opening
            out.add(frame.offset(s, 0, -2)); // south side
            out.add(frame.offset(1, 0, s)); // west side
            out.add(frame.offset(-2, 0, s)); // east side
        }
        return out;
    }

    private static boolean all(Level level, List<BlockPos> positions, Predicate<BlockState> test) {
        for (BlockPos p : positions) {
            if (!test.test(level.getBlockState(p))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isClear(BlockState state) {
        return state.isAir() || state.canBeReplaced() || isPortal(state);
    }

    /** Lights the first complete ring through the frame at {@code framePos} whose opening is clear. */
    static Outcome tryOpen(ServerLevel level, BlockPos framePos) {
        Outcome outcome = Outcome.INCOMPLETE;
        for (BlockPos min : candidates(framePos)) {
            if (!all(level, ring(min), ForgottenPortals::isFrame)) {
                continue;
            }
            List<BlockPos> cells = opening(min);
            if (all(level, cells, ForgottenPortals::isPortal)) {
                restoreSurface(level, min);
                outcome = Outcome.ALREADY_OPEN;
            } else if (!all(level, cells, ForgottenPortals::isClear)) {
                outcome = outcome == Outcome.ALREADY_OPEN ? outcome : Outcome.BLOCKED;
            } else {
                open(level, min);
                return Outcome.OPENED;
            }
        }
        return outcome;
    }

    /**
     * Portals lit before the surface had a block entity draw nothing: creates the missing ones on the portal cells
     * around {@code pos} (enough for a whole 2x2 opening) and sends them to clients, which then draw the surface.
     */
    static void restoreSurface(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (isPortal(state) && level.getChunkAt(p).getBlockEntity(p, LevelChunk.EntityCreationType.CHECK) == null
                    && level.getBlockEntity(p) instanceof ForgottenPortalBlockEntity) { // created by this lookup
                level.sendBlockUpdated(p, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void open(ServerLevel level, BlockPos min) {
        // portal first: each new block only checks the neighbour that changed, so the order is safe
        BlockState portal = ModBlocks.FORGOTTEN_PORTAL.get().defaultBlockState();
        for (BlockPos p : opening(min)) {
            level.setBlock(p, portal, Block.UPDATE_ALL);
        }
        for (BlockPos p : ring(min)) {
            BlockState frame = level.getBlockState(p);
            if (isFrame(frame) && !frame.getValue(ForgottenPortalFrameBlock.ACTIVE)) {
                level.setBlock(p, frame.setValue(ForgottenPortalFrameBlock.ACTIVE, true), Block.UPDATE_ALL);
            }
        }
        Vec3 c = new Vec3(min.getX() + 1.0, min.getY() + 0.75, min.getZ() + 1.0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.45F, 1.3F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(ModParticles.LIGHT_MOTE.get(), c.x, c.y, c.z, 36, 0.7, 0.15, 0.7, 0.03);
        level.sendParticles(ModParticles.SHADOW_MOTE.get(), c.x, c.y, c.z, 18, 0.6, 0.1, 0.6, 0.02);
        LacunaFx.send(level, c, FxPayload.at("rift", new Vec3(c.x, min.getY(), c.z)).withColor(0xFFB8A8F0));
    }

    /**
     * The Totem of Banishing used on a frame (overworld or Forgotten Land only). Returns true when a portal opened:
     * the caller then spends the use, starts the cooldown and tolls the bell. Otherwise an action-bar hint says why.
     */
    public static boolean openWithTotem(ServerPlayer player, ServerLevel level, BlockPos framePos) {
        if (!BanishmentManager.enabled(level.getServer()) || SafeRun.isDisabled(FEATURE)) {
            player.displayClientMessage(Component.translatable("lacuna.message.disabled"), true);
            return false;
        }
        if (level.dimension() != Level.OVERWORLD && !ModDimensions.isForgottenLand(level)) {
            player.displayClientMessage(Component.translatable("lacuna.totem.silent"), true);
            return false;
        }
        Outcome outcome = SafeRun.call(FEATURE, () -> tryOpen(level, framePos), Outcome.INCOMPLETE);
        String hint = switch (outcome) {
            case OPENED -> null;
            case ALREADY_OPEN -> "lacuna.portal.already_open";
            case BLOCKED -> "lacuna.portal.blocked";
            case INCOMPLETE -> "lacuna.portal.incomplete";
        };
        if (hint != null) {
            player.displayClientMessage(Component.translatable(hint), true);
        }
        return outcome == Outcome.OPENED;
    }

    // ------------------------------------------------------------------------------------------ travel

    /** Players, and living creatures that may be banished; never riders, mounts carrying riders or spectators. */
    private static boolean mayTravel(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.canUsePortal(false) || entity.isVehicle()) {
            return false;
        }
        if (entity instanceof Player player) {
            return !player.isSpectator(); // walking in is the player's own choice, like crossing with the totem
        }
        return !entity.getType().is(ModTags.Entities.CANNOT_BE_BANISHED);
    }

    /**
     * From {@link ForgottenPortalBlock#entityInside}: the entity touched the portal plane at {@code pos}. Like the End
     * portal it crosses at once, at the end of this server tick, unless it has only just arrived through one.
     */
    static void entered(ServerLevel level, Entity entity, BlockPos pos) {
        if (SafeRun.isDisabled(FEATURE) || !mayTravel(entity)
                || (level.dimension() != Level.OVERWORLD && !ModDimensions.isForgottenLand(level))
                || !BanishmentManager.enabled(level.getServer())) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            if (entity.isOnPortalCooldown()) {
                entity.setPortalCooldown(); // just arrived: no bouncing back until it has stepped out (vanilla portal rule)
                return;
            }
            ENTERED.putIfAbsent(entity.getUUID(), new Entry(level.dimension(), pos.immutable()));
        });
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (ENTERED.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<Map.Entry<UUID, Entry>> due = new ArrayList<>(ENTERED.entrySet());
        ENTERED.clear();
        for (Map.Entry<UUID, Entry> e : due) {
            ServerLevel level = server.getLevel(e.getValue().dimension());
            Entity entity = level == null ? null : level.getEntity(e.getKey());
            if (entity != null && entity.isAlive() && entity.level() == level) {
                SafeRun.run(FEATURE, () -> send(level, entity, e.getValue().portal()));
            }
        }
    }

    private static void send(ServerLevel level, Entity entity, BlockPos portal) {
        MinecraftServer server = level.getServer();
        if (!BanishmentManager.enabled(server) || !mayTravel(entity) || entity.isOnPortalCooldown()) {
            return;
        }
        boolean outbound = level.dimension() == Level.OVERWORLD;
        if ((!outbound && !ModDimensions.isForgottenLand(level))
                || (outbound && !(entity instanceof Player) && !BanishmentManager.canBanish(entity))) {
            return;
        }
        entryBurst(level, entity, portal);
        ServerPlayer traveller = entity instanceof ServerPlayer player ? player : null;
        if (traveller != null) {
            // right before the transfer: the client fades into the void, holds it while the far side loads, fades out
            LacunaFx.sendTo(traveller, FxPayload.at(CROSSING_FX, entity.position()).withParams(1.0F, 0.0F, 0.0F));
        }
        Entity moved = SafeRun.call(BanishmentManager.FEATURE,
                () -> outbound ? BanishmentManager.banish(entity) : BanishmentManager.returnEntity(entity), null,
                () -> BanishmentManager.rescueAll(server));
        if (moved == null) {
            entity.setPortalCooldown(); // no retry every tick: it tries again once it has stepped out and back in
            if (traveller != null && !traveller.hasDisconnected()) {
                LacunaFx.sendTo(traveller, FxPayload.at(CROSSING_FX, entity.position()).withParams(-1.0F, 0.0F, 0.0F));
            }
            return;
        }
        moved.setPortalCooldown();
        if (moved instanceof ServerPlayer player) {
            TotemOfBanishingItem.toll(player);
        }
    }

    /** A short burst where the traveller breaks the surface: pale motes splashing out, shadow motes rising. */
    private static void entryBurst(ServerLevel level, Entity entity, BlockPos portal) {
        double y = portal.getY() + 0.78;
        double w = Math.max(0.3, entity.getBbWidth() * 0.6);
        level.sendParticles(ModParticles.LIGHT_MOTE.get(), entity.getX(), y, entity.getZ(), 18, w, 0.04, w, 0.07);
        level.sendParticles(ModParticles.SHADOW_MOTE.get(), entity.getX(), y + 0.25, entity.getZ(), 12, w * 0.8,
                entity.getBbHeight() * 0.3, w * 0.8, 0.03);
        level.sendParticles(ModParticles.MIST.get(), entity.getX(), y + 0.1, entity.getZ(), 3, w, 0.05, w, 0.01);
        level.playSound(null, entity.getX(), y, entity.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6F, 0.5F);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ENTERED.clear();
    }
}
