package com.lacuna.mod.gear;

import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Wand of Quickening's spark of life in flight (server side). The flight time comes from {@link #flightTicks},
 * computed once at the cast and sent to every client with the bolt (client/gear/QuickeningFx), so the orb bursts on
 * the item on exactly the tick the server wakes it. Until then the item stays a plain dropped item, held in an
 * "about to wake" state: nobody can pick it up and it does not merge with its neighbours (never-pickup delay), it
 * glimmers pink, and the server follows that same entity (by id, as the client orb does) wherever it moves or is
 * tossed. If it is gone before the spark arrives (hopper, lava, void, portal...) the spark is lost and the heart paid
 * for it flows back; if the caster is gone, the item simply thaws back to normal.
 */
public final class QuickeningBolt {
    public static final String FEATURE = "quickening_bolt";
    /** Speed of the spark in blocks per tick (shared with the client orb). */
    public static final double SPEED = 1.1;
    public static final int MIN_FLIGHT = 3;
    public static final int MAX_FLIGHT = 18;
    /** A tossed off-hand item is chased at least this long, so it is seen flying out before it wakes. */
    public static final int TOSS_MIN_FLIGHT = 7;
    /** Persistent-data marker of a frozen item: one saved mid-flight (crash, unload) is thawed when it loads again. */
    private static final String FROZEN = "lacuna:quickening_frozen";
    private static final int MAX_ACTIVE = 64;
    private static final List<Pending> PENDING = new ArrayList<>();

    private QuickeningBolt() {}

    /** Flight time of the spark over {@code distance} blocks, at least {@code min} ticks (same rule on both sides). */
    public static int flightTicks(double distance, int min) {
        return Mth.clamp((int) Math.ceil(distance / SPEED), Math.max(1, min), MAX_FLIGHT);
    }

    public static int flightTicks(double distance) {
        return flightTicks(distance, MIN_FLIGHT);
    }

    /** True while a spark is on its way to this item (it must not be picked as a target again). */
    public static boolean isFrozen(ItemEntity item) {
        return item.getPersistentData().getBoolean(FROZEN);
    }

    /** Holds the item still as an item (no pickup, no merging) until the spark arrives. Call before adding it to a level. */
    public static void freeze(ItemEntity item) {
        item.setNeverPickUp();
        item.getPersistentData().putBoolean(FROZEN, true);
    }

    private static void thaw(ItemEntity item, boolean tossed) {
        item.getPersistentData().remove(FROZEN);
        if (tossed) {
            item.setPickUpDelay(40); // as if the player had just thrown it
        } else {
            item.setDefaultPickUpDelay();
        }
    }

    /** Sends the spark: the item wakes when it arrives, {@code flight} ticks from now. */
    public static void launch(ServerLevel level, ServerPlayer caster, ItemEntity item, int flight, float paid, boolean tossed) {
        if (PENDING.size() >= MAX_ACTIVE) {
            Pending oldest = PENDING.remove(0);
            oldest.cancel(level.getServer());
        }
        PENDING.add(new Pending(level.dimension(), caster.getUUID(), item.getUUID(), flight, paid, tossed));
    }

    // ------------------------------------------------------------------ hooks (game bus, see GearEvents)

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        SafeRun.run(FEATURE, () -> {
            Iterator<Pending> it = PENDING.iterator();
            while (it.hasNext()) {
                if (!it.next().tick(server)) {
                    it.remove();
                }
            }
        }, () -> thawAll(server));
    }

    /** Before the world is saved: every item still waiting goes back to normal. */
    public static void onServerStopping(ServerStoppingEvent event) {
        thawAll(event.getServer());
    }

    /** An item saved while frozen (crash, chunk unload mid-flight) is a normal item again when it loads. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && !event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item && isFrozen(item)) {
            thaw(item, false);
        }
    }

    private static void thawAll(MinecraftServer server) {
        List<Pending> all = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending p : all) {
            p.cancel(server);
        }
    }

    // ------------------------------------------------------------------ one spark

    private static final class Pending {
        final ResourceKey<Level> dimension;
        final UUID caster;
        final UUID item;
        final int flight;
        final float paid;
        final boolean tossed;
        int age;

        Pending(ResourceKey<Level> dimension, UUID caster, UUID item, int flight, float paid, boolean tossed) {
            this.dimension = dimension;
            this.caster = caster;
            this.item = item;
            this.flight = flight;
            this.paid = paid;
            this.tossed = tossed;
        }

        ItemEntity item(MinecraftServer server) {
            ServerLevel level = server.getLevel(dimension);
            return level != null && level.getEntity(item) instanceof ItemEntity e && e.isAlive() && !e.getItem().isEmpty() ? e : null;
        }

        void cancel(MinecraftServer server) {
            ItemEntity e = item(server);
            if (e != null) {
                thaw(e, tossed);
            }
        }

        /** False when done (woken, lost or cancelled). */
        boolean tick(MinecraftServer server) {
            ServerLevel level = server.getLevel(dimension);
            ItemEntity target = item(server);
            ServerPlayer player = server.getPlayerList().getPlayer(caster);
            if (target == null || level == null) {
                lost(player);
                return false;
            }
            if (player == null || !player.isAlive() || player.level() != level) {
                thaw(target, tossed); // the caster left or died: the spark fades and the item is just an item again
                return false;
            }
            age++;
            if (age < flight) {
                shimmer(level, target);
                return true;
            }
            wake(level, player, target);
            return false;
        }

        /** The item quivers with borrowed life while the spark is on its way. */
        private void shimmer(ServerLevel level, ItemEntity target) {
            Vec3 c = target.position().add(0.0, 0.25, 0.0);
            level.sendParticles(AnimatedItemEntity.PINK_DUST, c.x, c.y, c.z, 2, 0.18, 0.15, 0.18, 0.0);
            if (age % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.15, 0.12, 0.15, 0.01);
            }
        }

        /** The spark arrives: exactly that stack wakes where the item now is, carrying its motion. */
        private void wake(ServerLevel level, ServerPlayer player, ItemEntity target) {
            float faceYaw = (float) Math.toDegrees(Math.atan2(target.getX() - player.getX(), player.getZ() - target.getZ()));
            Vec3 motion = target.getDeltaMovement();
            AnimatedItemEntity animated = AnimatedItemEntity.spawn(level, target.getItem().copy(), player, target.position(),
                    new Vec3(motion.x, Math.max(0.0, motion.y), motion.z), tossed ? player.getYRot() : faceYaw);
            if (animated == null) {
                thaw(target, tossed);
                lost(player);
                return;
            }
            target.getPersistentData().remove(FROZEN);
            target.discard();
            AnimatedItemEntity.enforceCap(level, player, animated);
            player.displayClientMessage(Component.translatable("lacuna.quickening.awake", animated.getMainHandItem().getHoverName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }

        /** Nothing left to wake: the heart paid for it flows back to the caster. */
        private void lost(ServerPlayer player) {
            if (player == null || !player.isAlive()) {
                return;
            }
            if (paid > 0.0F) {
                player.heal(paid);
            }
            Sfx.at(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.6F);
            player.displayClientMessage(Component.translatable("lacuna.quickening.lost").withStyle(ChatFormatting.GRAY), true);
        }
    }
}
