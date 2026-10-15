package com.lacuna.mod.banish;

import com.lacuna.mod.Balance;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Forgotten Portal: eight {@link ForgottenPortalFrameBlock}s, two per side with the corners empty, around a 2x2
 * opening on one level (the End portal's ring, one size smaller). The Totem of Banishing lights a complete ring like
 * flint and steel lights a nether portal. Whatever stands in the portal long enough (a player 2 s, any other creature
 * a moment) is sent through with the banishment system: banished from the overworld (timer, 1:12 coordinates, safe
 * spot), returned from the Forgotten Land. Travel runs at the end of the server tick, never inside a block callback.
 */
public final class ForgottenPortals {
    public static final String FEATURE = "forgotten_portal";
    /** Ticks spent standing in a portal, by entity (volatile: a charge never survives a restart). */
    private static final Map<UUID, Charge> CHARGING = new HashMap<>();

    private static final class Charge {
        ResourceKey<Level> dimension;
        long lastTick = Long.MIN_VALUE;
        int ticks;
        boolean ready;
    }

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

    /** From {@link ForgottenPortalBlock#entityInside}: one more tick standing in a portal. */
    static void inside(ServerLevel level, Entity entity) {
        if (SafeRun.isDisabled(FEATURE) || !mayTravel(entity)
                || (level.dimension() != Level.OVERWORLD && !ModDimensions.isForgottenLand(level))
                || !BanishmentManager.enabled(level.getServer())) {
            return;
        }
        SafeRun.run(FEATURE, () -> charge(level, entity));
    }

    private static void charge(ServerLevel level, Entity entity) {
        if (entity.isOnPortalCooldown()) {
            entity.setPortalCooldown(); // just arrived: no bouncing back until it has stepped out (vanilla portal rule)
            return;
        }
        long now = level.getServer().getTickCount();
        Charge c = CHARGING.computeIfAbsent(entity.getUUID(), id -> new Charge());
        if (c.lastTick == now) {
            return; // standing across several portal blocks
        }
        if (c.lastTick != now - 1 || c.dimension != level.dimension()) {
            c.ticks = 0;
        }
        c.dimension = level.dimension();
        c.lastTick = now;
        c.ticks++;
        int needed = entity instanceof Player ? Balance.PORTAL_PLAYER_TICKS : Balance.PORTAL_MOB_TICKS;
        chargeEffects(level, entity, c.ticks, needed);
        c.ready = c.ticks >= needed;
    }

    /** Players: a swirl of motes closing in and speeding up, a portal whoosh and a rising diminished arpeggio. */
    private static void chargeEffects(ServerLevel level, Entity entity, int ticks, int needed) {
        float progress = Math.min(1.0F, ticks / (float) needed);
        if (!(entity instanceof Player)) {
            if (ticks % 3 == 1) {
                level.sendParticles(ModParticles.SHADOW_MOTE.get(), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5,
                        entity.getZ(), 3, entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0.02);
            }
            return;
        }
        int motes = 1 + Math.round(progress * 4.0F);
        double radius = 1.0 - 0.45 * progress;
        double height = entity.getBbHeight();
        for (int i = 0; i < motes; i++) {
            double a = ticks * (0.25 + 0.35 * progress) + i * Mth.TWO_PI / motes;
            double y = entity.getY() + 0.1 + ((ticks * 0.045 + i / (double) motes) % 1.0) * height;
            // count 0: the offsets are the motion, here tangent to the circle (the swirl) and slightly upward
            level.sendParticles(i % 2 == 0 ? ModParticles.LIGHT_MOTE.get() : ModParticles.SHADOW_MOTE.get(),
                    entity.getX() + Math.cos(a) * radius, y, entity.getZ() + Math.sin(a) * radius, 0,
                    -Math.sin(a), 0.15, Math.cos(a), 0.05 + 0.1 * progress);
        }
        if (ticks == 1) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS,
                    0.25F, 1.6F);
        }
        if (ticks % 8 == 1) {
            int step = ticks / 8;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.PLAYERS, 0.3F + 0.08F * step, (float) Math.pow(2.0, (3 * step - 6) / 12.0));
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (CHARGING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        Map<UUID, ResourceKey<Level>> ready = new HashMap<>();
        Iterator<Map.Entry<UUID, Charge>> it = CHARGING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Charge> e = it.next();
            Charge c = e.getValue();
            if (c.lastTick < now) {
                it.remove(); // stepped out: the charge is lost
            } else if (c.ready) {
                it.remove();
                ready.put(e.getKey(), c.dimension);
            }
        }
        ready.forEach((id, dimension) -> {
            ServerLevel level = server.getLevel(dimension);
            Entity entity = level == null ? null : level.getEntity(id);
            if (entity != null) {
                send(level, entity);
            }
        });
    }

    private static void send(ServerLevel level, Entity entity) {
        MinecraftServer server = level.getServer();
        if (!BanishmentManager.enabled(server) || !mayTravel(entity) || entity.isOnPortalCooldown()) {
            return;
        }
        Entity moved = null;
        if (level.dimension() == Level.OVERWORLD) {
            if (entity instanceof Player || BanishmentManager.canBanish(entity)) {
                moved = SafeRun.call(BanishmentManager.FEATURE, () -> BanishmentManager.banish(entity), null,
                        () -> BanishmentManager.rescueAll(server));
            }
        } else if (ModDimensions.isForgottenLand(level)) {
            moved = SafeRun.call(BanishmentManager.FEATURE, () -> BanishmentManager.returnEntity(entity), null,
                    () -> BanishmentManager.rescueAll(server));
        }
        if (moved != null) {
            moved.setPortalCooldown();
            if (moved instanceof ServerPlayer player) {
                TotemOfBanishingItem.toll(player);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CHARGING.clear();
    }
}
