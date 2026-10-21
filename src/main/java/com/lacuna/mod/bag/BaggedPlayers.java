package com.lacuna.mod.bag;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import com.lacuna.mod.util.Sfx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Players in the Comically Large Bag. A player cannot leave the world the way a cow does, so "in the bag" is a state:
 * <ul>
 *   <li>The target rides the bagger as an invisible passenger shrunk to a speck ({@link EntityEvent.Size}), so it is
 *   carried smoothly, keeps its chunks loaded around the bagger, can't be clicked, shot, pushed or hurt (only
 *   {@code /kill} and the void get through) and can't move, attack, break, place, interact or use items. Its own
 *   sneak dismount is refused (and counts as a struggle). Its client watches from the bagger's hip through the
 *   inside of the sack ({@code client/bag/BagPlayerClient}).</li>
 *   <li>The bag in the bagger's inventory holds only a token ({@link BagContents#PLAYER}); the carry itself lives here
 *   and is checked every tick. It ends when the time runs out (struggling shortens it), when the bagger empties the
 *   bag on a block, drops or stashes it, dies, logs out or changes dimension, when the target logs out, is
 *   teleported or changes game mode, and on server stop. The target then pops out on a free spot next to the bagger
 *   with no fall damage, and nobody can bag them again for a while.</li>
 *   <li>Nothing persists: a crash mid-carry leaves a marker in the player's data that undoes the state on their next
 *   login, and a token whose carry is over empties itself.</li>
 * </ul>
 */
public final class BaggedPlayers {
    public static final String FEATURE = "comically_large_bag_players";
    /** Set while bagged; found on login only after a crash (every normal exit removes it). */
    private static final String MARKER = "lacuna_bagged";
    /** A speck: not pickable, not shootable, never in a wall. */
    private static final EntityDimensions SPECK = EntityDimensions.fixed(0.01F, 0.01F);

    private static final Map<UUID, Carry> BY_TARGET = new HashMap<>();
    /** Target UUID → server tick from which it can be bagged again. */
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();

    private enum End {
        /** time is up or struggled out */
        FREE,
        /** the bagger emptied the bag on a block */
        LET_OUT,
        /** anything else that ends the carry with both players still around */
        DROPPED,
        /** the target is dead or gone: nothing to put anywhere */
        GONE
    }

    private static final class Carry {
        final UUID target;
        final UUID bagger;
        final String name;
        final long session;
        final int stacks;
        long until;
        int lastStruggle = -100;
        int lastSneak = -100;
        int shownSeconds = -1;

        Carry(UUID target, UUID bagger, String name, long session, int stacks, long until) {
            this.target = target;
            this.bagger = bagger;
            this.name = name;
            this.session = session;
            this.stacks = stacks;
            this.until = until;
        }
    }

    private BaggedPlayers() {}

    public static void register(IEventBus bus) {
        bus.addListener(BaggedPlayers::onServerTick);
        bus.addListener(BaggedPlayers::onMount);
        bus.addListener(BaggedPlayers::onSize);
        bus.addListener(EventPriority.HIGH, BaggedPlayers::onIncomingDamage);
        bus.addListener(EventPriority.HIGH, BaggedPlayers::onFall);
        bus.addListener(BaggedPlayers::onChangeTarget);
        bus.addListener(EventPriority.HIGH, BaggedPlayers::onProjectileImpact);
        bus.addListener(EventPriority.HIGH, BaggedPlayers::onAttack);
        bus.addListener(EventPriority.HIGH, (PlayerInteractEvent.RightClickBlock e) -> refuseInteraction(e.getEntity(), e));
        bus.addListener(EventPriority.HIGH, (PlayerInteractEvent.RightClickItem e) -> refuseInteraction(e.getEntity(), e));
        bus.addListener(EventPriority.HIGH, (PlayerInteractEvent.LeftClickBlock e) -> refuseInteraction(e.getEntity(), e));
        bus.addListener(EventPriority.HIGH, (PlayerInteractEvent.EntityInteract e) -> refuseInteraction(e.getEntity(), e));
        bus.addListener(EventPriority.HIGH, (PlayerInteractEvent.EntityInteractSpecific e) -> refuseInteraction(e.getEntity(), e));
        bus.addListener(EventPriority.HIGH, (BlockEvent.BreakEvent e) -> refuseInteraction(e.getPlayer(), e));
        bus.addListener(EventPriority.HIGH, (BlockEvent.EntityPlaceEvent e) -> refuseInteraction(e.getEntity(), e));
        bus.addListener(EventPriority.HIGH, (PlayerEvent.BreakSpeed e) -> refuseInteraction(e.getEntity(), e));
        bus.addListener(EventPriority.HIGH, (LivingEntityUseItemEvent.Start e) -> refuseInteraction(e.getEntity(), e));
        bus.addListener(BaggedPlayers::onStartTracking);
        bus.addListener(BaggedPlayers::onLogin);
        bus.addListener(BaggedPlayers::onLogout);
        bus.addListener(EventPriority.LOWEST, BaggedPlayers::onDeath);
        bus.addListener(BaggedPlayers::onTravel);
        bus.addListener(BaggedPlayers::onTeleport);
        bus.addListener(BaggedPlayers::onServerStopping);
        bus.addListener((ServerStoppedEvent e) -> {
            BY_TARGET.clear();
            COOLDOWN.clear();
        });
    }

    // ================================================================================================ queries

    public static boolean isBagged(Player player) {
        return !player.level().isClientSide() && BY_TARGET.containsKey(player.getUUID());
    }

    private static boolean isCarrying(Player player) {
        for (Carry c : BY_TARGET.values()) {
            if (c.bagger.equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Carry bySession(long session) {
        for (Carry c : BY_TARGET.values()) {
            if (c.session == session) {
                return c;
            }
        }
        return null;
    }

    private static int now(MinecraftServer server) {
        return server.getTickCount();
    }

    private static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ================================================================================================ capture

    /** Right click on a player with an empty bag. */
    public static InteractionResult capture(ServerPlayer bagger, ItemStack bag, ServerPlayer target) {
        if (BagCapture.isFull(bag) || target == bagger || target.level() != bagger.level()) {
            return InteractionResult.PASS;
        }
        String refusal = refusal(bagger, target);
        if (refusal != null) {
            if ("lacuna.bag.player_cooldown".equals(refusal)) {
                bagger.displayClientMessage(Component.translatable(refusal, target.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
                Sfx.at(bagger, SoundEvents.BUNDLE_REMOVE_ONE, 0.7F, 0.5F);
                return InteractionResult.FAIL;
            }
            return BagCapture.refuse(bagger, refusal);
        }
        if (target.isSleeping()) {
            target.stopSleepInBed(true, true);
        }
        if (target.isFallFlying()) {
            target.stopFallFlying();
        }
        target.stopUsingItem();
        if (target.containerMenu != target.inventoryMenu) {
            target.closeContainer();
        }
        Vec3 at = target.position();
        if (!target.startRiding(bagger, true)) {
            return BagCapture.refuse(bagger, "lacuna.bag.slippery");
        }
        MinecraftServer server = bagger.server;
        int stacks = 0;
        Inventory inventory = target.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!inventory.getItem(i).isEmpty()) {
                stacks++;
            }
        }
        long session = bagger.getRandom().nextLong() | 1L;
        Carry carry = new Carry(target.getUUID(), bagger.getUUID(), target.getGameProfile().getName(), session, stacks,
                gameTime(server) + Balance.BAG_PLAYER_TICKS);
        BY_TARGET.put(carry.target, carry);
        writeToken(bag, carry);
        target.getPersistentData().putBoolean(MARKER, true);
        target.refreshDimensions();
        target.setInvisible(true);
        target.resetFallDistance();
        // the passenger change goes out now, ahead of the tracker's own copy, so both clients agree at once
        bagger.serverLevel().getChunkSource().broadcastAndSend(bagger, new ClientboundSetPassengersPacket(bagger));
        sendState(target, carry, true);
        ServerLevel level = bagger.serverLevel();
        level.gameEvent(bagger, GameEvent.ENTITY_INTERACT, at);
        BagCapture.thwump(level, at, 0.6F, 1.8F, true);
        bagger.displayClientMessage(Component.translatable("lacuna.bag.bagged", target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static String refusal(ServerPlayer bagger, ServerPlayer target) {
        if (SafeRun.isDisabled(FEATURE)) {
            return "lacuna.bag.no_players";
        }
        if (!bagger.getAbilities().mayBuild || !bagger.serverLevel().mayInteract(bagger, target.blockPosition())) {
            return "lacuna.bag.protected";
        }
        if (!target.isAlive()) {
            return "lacuna.bag.nothing";
        }
        if (target.isCreative() || target.isSpectator() || target.isInvulnerable() || target.getAbilities().invulnerable) {
            return "lacuna.bag.player_immune";
        }
        if (!bagger.canHarmPlayer(target)) {
            return "lacuna.bag.no_pvp";
        }
        if (isCarrying(bagger) || BY_TARGET.containsKey(bagger.getUUID())) {
            return "lacuna.bag.hands_full";
        }
        if (BY_TARGET.containsKey(target.getUUID()) || isCarrying(target)) {
            return "lacuna.bag.player_busy";
        }
        if (target.isVehicle()) {
            return "lacuna.bag.ridden";
        }
        Integer free = COOLDOWN.get(target.getUUID());
        if (free != null && now(bagger.server) < free) {
            return "lacuna.bag.player_cooldown";
        }
        return null;
    }

    // ================================================================================================ token

    private static void writeToken(ItemStack bag, Carry carry) {
        CompoundTag data = new CompoundTag();
        data.putUUID("uuid", carry.target);
        data.putString("name", carry.name);
        data.putLong("until", carry.until);
        data.putInt("stacks", carry.stacks);
        data.putLong("session", carry.session);
        bag.set(ModComponents.BAG_CONTENTS.get(), new BagContents(BagContents.PLAYER, data));
    }

    private static boolean isToken(ItemStack stack, long session) {
        if (!stack.is(ModItems.COMICALLY_LARGE_BAG.get())) {
            return false;
        }
        BagContents contents = BagCapture.contents(stack);
        return contents != null && contents.isPlayer() && contents.data().getLong("session") == session;
    }

    /** The bag holding this carry, wherever it sits in the bagger's inventory or on their cursor; EMPTY if let go. */
    private static ItemStack findToken(ServerPlayer bagger, long session) {
        Inventory inventory = bagger.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isToken(stack, session)) {
                return stack;
            }
        }
        ItemStack carried = bagger.containerMenu.getCarried();
        return isToken(carried, session) ? carried : ItemStack.EMPTY;
    }

    /** A token whose carry is over (released, or lost to a crash) turns back into an empty bag; true if it did. */
    public static boolean checkToken(ItemStack stack) {
        BagContents contents = BagCapture.contents(stack);
        if (contents != null && contents.isPlayer() && bySession(contents.data().getLong("session")) == null) {
            stack.remove(ModComponents.BAG_CONTENTS.get());
            return true;
        }
        return false;
    }

    /** Right click with the bag on a block: the carried player tumbles out there (or next to the bagger). */
    public static InteractionResult releaseWithBag(ServerPlayer bagger, ItemStack bag, UseOnContext context) {
        BagContents contents = BagCapture.contents(bag);
        Carry carry = contents == null ? null : bySession(contents.data().getLong("session"));
        if (carry == null || !carry.bagger.equals(bagger.getUUID())) {
            bag.remove(ModComponents.BAG_CONTENTS.get());
            bagger.displayClientMessage(Component.translatable("lacuna.bag.player_gone").withStyle(ChatFormatting.YELLOW), true);
            Sfx.at(bagger, SoundEvents.BUNDLE_DROP_CONTENTS, 0.7F, 0.8F);
            return InteractionResult.SUCCESS;
        }
        Vec3 spot = Vec3.atBottomCenterOf(new BlockPlaceContext(context).getClickedPos());
        release(carry, bagger.server.getPlayerList().getPlayer(carry.target), bagger, spot, End.LET_OUT);
        return InteractionResult.SUCCESS;
    }

    // ================================================================================================ struggle

    /** Jump / attack / sneak inside the bag: a little less time to go, and the bag wriggles for everyone to see. */
    public static void struggle(ServerPlayer target) {
        Carry carry = BY_TARGET.get(target.getUUID());
        if (carry == null || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        int tick = now(target.server);
        if (tick - carry.lastStruggle < Balance.BAG_STRUGGLE_INTERVAL) {
            return;
        }
        carry.lastStruggle = tick;
        carry.until -= Balance.BAG_STRUGGLE_TICKS;
        ServerPlayer bagger = target.server.getPlayerList().getPlayer(carry.bagger);
        if (bagger == null) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            ItemStack token = findToken(bagger, carry.session);
            if (!token.isEmpty()) {
                writeToken(token, carry);
            }
            Vec3 at = hip(bagger);
            ServerLevel level = bagger.serverLevel();
            level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.01);
            Sfx.varied(bagger, SoundEvents.WOOL_STEP, 0.7F, 0.8F, 0.3F);
            if (bagger.getRandom().nextInt(3) == 0) {
                Sfx.varied(bagger, SoundEvents.BUNDLE_INSERT, 0.5F, 1.3F, 0.3F);
            }
        });
    }

    /** Where the sack hangs: at the bagger's right hip. */
    private static Vec3 hip(Player bagger) {
        float yaw = bagger.yBodyRot * Mth.DEG_TO_RAD;
        return bagger.position().add(-Mth.cos(yaw) * 0.45, bagger.getBbHeight() * 0.45, -Mth.sin(yaw) * 0.45);
    }

    // ================================================================================================ tick

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!BY_TARGET.isEmpty()) {
            SafeRun.run(FEATURE, () -> tick(event.getServer()), () -> releaseAll(event.getServer()));
        }
    }

    private static void tick(MinecraftServer server) {
        long time = gameTime(server);
        for (Carry carry : List.copyOf(BY_TARGET.values())) {
            ServerPlayer target = server.getPlayerList().getPlayer(carry.target);
            ServerPlayer bagger = server.getPlayerList().getPlayer(carry.bagger);
            End end = check(carry, target, bagger, time);
            if (end != null) {
                release(carry, target, bagger, null, end);
                continue;
            }
            // in the bag: nothing burns, freezes, drowns or keeps a fall going, and nobody sees a thing
            target.setInvisible(true);
            target.clearFire();
            target.setTicksFrozen(0);
            target.setAirSupply(target.getMaxAirSupply());
            target.resetFallDistance();
            target.setSprinting(false);
            if (target.isUsingItem()) {
                target.stopUsingItem();
            }
            if (target.containerMenu != target.inventoryMenu) {
                target.closeContainer();
            }
            int seconds = (int) Math.max(0L, (carry.until - time + 19L) / 20L);
            if (seconds != carry.shownSeconds) {
                carry.shownSeconds = seconds;
                target.displayClientMessage(Component.translatable("lacuna.bag.in_bag", bagger.getDisplayName(), seconds)
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    @Nullable
    private static End check(Carry carry, @Nullable ServerPlayer target, @Nullable ServerPlayer bagger, long time) {
        if (target == null || target.isRemoved() || !target.isAlive() || target.hasDisconnected()) {
            return End.GONE;
        }
        if (bagger == null || bagger.isRemoved() || !bagger.isAlive() || bagger.hasDisconnected() || bagger.level() != target.level()
                || target.getVehicle() != bagger || target.isSpectator() || target.isCreative()
                || findToken(bagger, carry.session).isEmpty()) {
            return End.DROPPED;
        }
        return time >= carry.until ? End.FREE : null;
    }

    // ================================================================================================ release

    private static void release(Carry carry, @Nullable ServerPlayer target, @Nullable ServerPlayer bagger, @Nullable Vec3 preferred, End end) {
        BY_TARGET.remove(carry.target);
        MinecraftServer server = target != null ? target.server : bagger != null ? bagger.server : null;
        if (server != null) {
            COOLDOWN.put(carry.target, now(server) + Balance.BAG_PLAYER_COOLDOWN_TICKS);
            COOLDOWN.values().removeIf(free -> free < now(server));
        }
        if (bagger != null) {
            ItemStack token = findToken(bagger, carry.session);
            if (!token.isEmpty()) {
                token.remove(ModComponents.BAG_CONTENTS.get());
            }
        }
        if (target == null) {
            return;
        }
        target.getPersistentData().remove(MARKER);
        Entity vehicle = target.getVehicle();
        if (vehicle != null) {
            target.stopRiding();
        }
        // the dismount reaches the clients before the teleport below, or the target's client would stay on top
        Entity carrier = vehicle != null ? vehicle : bagger;
        if (carrier != null && carrier.level() instanceof ServerLevel carrierLevel && !carrier.isRemoved()) {
            carrierLevel.getChunkSource().broadcastAndSend(carrier, new ClientboundSetPassengersPacket(carrier));
        }
        target.refreshDimensions();
        target.setInvisible(target.hasEffect(MobEffects.INVISIBILITY));
        target.resetFallDistance();
        sendState(target, carry, false);
        if (end == End.GONE || target.isRemoved() || !target.isAlive()) {
            return;
        }
        ServerLevel level = target.serverLevel();
        Player from = bagger != null && bagger.level() == level ? bagger : null;
        Vec3 base = from != null ? from.position() : target.position();
        Vec3 spot = popSpot(level, from, target, base, preferred);
        float yaw = spot.distanceToSqr(base) > 0.01 ? (float) (Mth.atan2(base.z - spot.z, base.x - spot.x) * Mth.RAD_TO_DEG) - 90.0F : target.getYRot();
        target.teleportTo(level, spot.x, spot.y, spot.z, yaw, 0.0F);
        target.resetFallDistance();
        target.setDeltaMovement(0.0, 0.25, 0.0); // a little hop out of the bag
        target.hurtMarked = true;
        level.gameEvent(target, GameEvent.ENTITY_PLACE, spot);
        BagCapture.thwump(level, spot, 0.6F, 1.8F, false);
        Component name = target.getDisplayName();
        String key = end == End.LET_OUT ? "lacuna.bag.player_let_out" : "lacuna.bag.player_free";
        if (bagger != null && !bagger.hasDisconnected()) {
            bagger.displayClientMessage(Component.translatable(key, name).withStyle(ChatFormatting.GOLD), true);
        }
        target.displayClientMessage(Component.translatable("lacuna.bag.you_free").withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * A free, unharmful, standing-sized spot with ground (or water) at most three blocks below: the clicked spot, the
     * bagger's sides, back and front, then the bagger's own spot, each in plain sight of the bagger. Failing all that,
     * the nearest safe block around, and as a last resort right where the bagger stands.
     */
    private static Vec3 popSpot(ServerLevel level, @Nullable Player bagger, ServerPlayer target, Vec3 base, @Nullable Vec3 preferred) {
        List<Vec3> spots = new ArrayList<>();
        if (preferred != null && preferred.distanceToSqr(base) < 36.0) {
            spots.add(preferred);
        }
        float yaw = (bagger != null ? bagger.getYRot() : target.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        for (Vec3 step : List.of(right, right.reverse(), forward.reverse(), forward)) {
            spots.add(base.add(step.scale(0.9)));
        }
        spots.add(base);
        EntityDimensions dims = EntityType.PLAYER.getDimensions();
        Vec3 eye = base.add(0.0, 1.0, 0.0);
        Entity viewer = bagger != null ? bagger : target;
        for (Vec3 spot : spots) {
            for (double lift : new double[] {0.0, 0.5, 1.0}) {
                Vec3 at = spot.add(0.0, lift, 0.0);
                AABB box = dims.makeBoundingBox(at);
                if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(box) || harmful(level, box)) {
                    continue;
                }
                AABB below = new AABB(box.minX, box.minY - 3.0, box.minZ, box.maxX, box.minY, box.maxZ);
                if ((level.noCollision(below) && !level.containsAnyLiquid(below)) || harmful(level, below)) {
                    continue;
                }
                if (at.distanceToSqr(base) > 0.01 && level.clip(new ClipContext(eye, at.add(0.0, 1.0, 0.0), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, viewer)).getType() != HitResult.Type.MISS) {
                    continue;
                }
                return at;
            }
        }
        return SafeSpot.findNear(level, BlockPos.containing(base)).map(Vec3::atBottomCenterOf).orElse(base);
    }

    private static boolean harmful(ServerLevel level, AABB box) {
        return level.getBlockStates(box).anyMatch(s -> s.getFluidState().is(FluidTags.LAVA) || s.is(BlockTags.FIRE)
                || s.is(BlockTags.CAMPFIRES) || s.is(net.minecraft.world.level.block.Blocks.CACTUS)
                || s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                || s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW));
    }

    /** Crash shield restore: whatever went wrong, nobody stays stuck in a bag. */
    public static void releaseAll() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            releaseAll(server);
        } else {
            BY_TARGET.clear();
        }
    }

    private static void releaseAll(MinecraftServer server) {
        Iterator<Carry> it = List.copyOf(BY_TARGET.values()).iterator();
        while (it.hasNext()) {
            Carry carry = it.next();
            try {
                release(carry, server.getPlayerList().getPlayer(carry.target), server.getPlayerList().getPlayer(carry.bagger), null, End.DROPPED);
            } catch (RuntimeException e) {
                BY_TARGET.remove(carry.target);
                com.lacuna.mod.Lacuna.LOGGER.error("[Lacuna] bag: could not let {} out cleanly", carry.name, e);
            }
        }
        BY_TARGET.clear();
    }

    /** Ends whichever carry this player is part of (as either side), with the target popping out. */
    private static void releaseInvolving(ServerPlayer player) {
        for (Carry carry : List.copyOf(BY_TARGET.values())) {
            if (carry.target.equals(player.getUUID()) || carry.bagger.equals(player.getUUID())) {
                MinecraftServer server = player.server;
                SafeRun.run(FEATURE, () -> release(carry, server.getPlayerList().getPlayer(carry.target),
                        server.getPlayerList().getPlayer(carry.bagger), null, End.DROPPED), () -> releaseAll(server));
            }
        }
    }

    private static void sendState(ServerPlayer target, Carry carry, boolean active) {
        int left = (int) Mth.clamp(carry.until - gameTime(target.server), 0L, Integer.MAX_VALUE);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new BagPlayerNet.State(carry.target, carry.bagger, active, left));
    }

    // ================================================================================================ events

    /** The target's own dismounts (sneak, a stray /tp) are refused while the bagger is still there to hold on. */
    private static void onMount(EntityMountEvent event) {
        if (event.isMounting() || event.getLevel().isClientSide() || !(event.getEntityMounting() instanceof ServerPlayer target)) {
            return;
        }
        Carry carry = BY_TARGET.get(target.getUUID());
        if (carry == null) {
            return;
        }
        boolean holdOn = event.getEntityBeingMounted() instanceof ServerPlayer bagger && bagger.getUUID().equals(carry.bagger)
                && bagger.isAlive() && !bagger.isRemoved() && !bagger.hasDisconnected() && bagger.level() == target.level()
                && target.isAlive() && !target.isRemoved() && !target.hasDisconnected() && !target.isSpectator();
        if (!holdOn) {
            return; // let it go through: the next tick sees the carry is broken and lets the player out properly
        }
        event.setCanceled(true);
        if (target.isShiftKeyDown()) {
            int tick = now(target.server);
            if (tick - carry.lastSneak > 2) {
                struggle(target);
            }
            carry.lastSneak = tick;
        }
    }

    private static void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player && entity.level() != null && !entity.level().isClientSide() && BY_TARGET.containsKey(entity.getUUID())) {
            event.setNewSize(SPECK);
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isBagged(player) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isBagged(player)) {
            event.setCanceled(true);
        }
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && isBagged(player)) {
            event.setCanceled(true);
        }
    }

    /** Arrows and the like fly straight through someone who is in a bag. */
    private static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof ServerPlayer player && isBagged(player)) {
            event.setCanceled(true);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isBagged(player)
                || event.getTarget() instanceof ServerPlayer target && isBagged(target)) {
            event.setCanceled(true);
        }
    }

    private static void refuseInteraction(@Nullable Entity entity, ICancellableEvent event) {
        if (entity instanceof ServerPlayer player && isBagged(player)) {
            event.setCanceled(true);
            if (event instanceof PlayerInteractEvent.RightClickItem e) {
                e.setCancellationResult(InteractionResult.FAIL);
            } else if (event instanceof PlayerInteractEvent.RightClickBlock e) {
                e.setCancellationResult(InteractionResult.FAIL);
            } else if (event instanceof PlayerInteractEvent.EntityInteract e) {
                e.setCancellationResult(InteractionResult.FAIL);
            } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific e) {
                e.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            Carry carry = BY_TARGET.get(target.getUUID());
            if (carry != null) {
                int left = (int) Mth.clamp(carry.until - gameTime(target.server), 0L, Integer.MAX_VALUE);
                PacketDistributor.sendToPlayer(viewer, new BagPlayerNet.State(carry.target, carry.bagger, true, left));
            }
        }
    }

    /** A marker on login means the server went down mid-carry: undo the bagged state so nobody stays invisible. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().getBoolean(MARKER) && !BY_TARGET.containsKey(player.getUUID())) {
            SafeRun.run(FEATURE, () -> {
                player.getPersistentData().remove(MARKER);
                player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
                player.refreshDimensions();
                player.resetFallDistance();
                ServerLevel level = player.serverLevel();
                SafeSpot.findNear(level, player.blockPosition()).ifPresent(pos -> {
                    if (!pos.equals(player.blockPosition())) {
                        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
                    }
                });
            });
        }
    }

    /** Fires before the player is saved and removed, so the target is saved standing on the ground, visible. */
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !BY_TARGET.isEmpty()) {
            releaseInvolving(player);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !BY_TARGET.isEmpty()) {
            releaseInvolving(player);
        }
    }

    /** Either side about to change dimension: the target pops out here first, next to the bagger. */
    private static void onTravel(EntityTravelToDimensionEvent event) {
        // ender pearls travel "to" the same dimension too: those carry the bag along, unless it is the target travelling
        if (event.getEntity() instanceof ServerPlayer player && !BY_TARGET.isEmpty()
                && (isBagged(player) || event.getDimension() != player.level().dimension())) {
            releaseInvolving(player);
        }
    }

    /** A target teleported by a command, a pearl or chorus fruit leaves the bag first. The bagger's teleports carry it along. */
    private static void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isBagged(player)) {
            Carry carry = BY_TARGET.get(player.getUUID());
            SafeRun.run(FEATURE, () -> release(carry, player, player.server.getPlayerList().getPlayer(carry.bagger), null, End.DROPPED));
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        if (!BY_TARGET.isEmpty()) {
            releaseAll(event.getServer());
        }
    }
}
