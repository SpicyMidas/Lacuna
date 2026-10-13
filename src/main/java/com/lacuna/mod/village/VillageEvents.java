package com.lacuna.mod.village;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.VillageLaws;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus hooks of the village civic system (spec 18.2-18.4, revision 2: vanilla villages). Reputation changes,
 * trade prices, the bell, and a server-tick driver that runs law enforcement every second and the daily routine
 * every 5 seconds for the villages players are standing in. All wrapped in SafeRun.
 */
public final class VillageEvents {
    private static final Map<UUID, Integer> TRADING_WITH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_HURT_PENALTY = new ConcurrentHashMap<>();
    static final int LAW_INTERVAL = 20;
    static final int DAILY_INTERVAL = 100;

    private VillageEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(VillageEvents::onTrade);
        bus.addListener(VillageEvents::onDeath);
        bus.addListener(VillageEvents::onAttack);
        bus.addListener(VillageEvents::onBreak);
        bus.addListener(VillageEvents::onRightClickBlock);
        bus.addListener(VillageEvents::onEntityInteract);
        bus.addListener(VillageEvents::onContainerOpen);
        bus.addListener(VillageEvents::onLogout);
        bus.addListener(VillageEvents::onServerTick);
        bus.addListener(VillageTrades::onVillagerTrades);
        bus.addListener(VillageTrades::onTrade);
        bus.addListener(VillagePools::onServerAboutToStart);
    }

    public static void clearVolatile() {
        TRADING_WITH.clear();
        LAST_HURT_PENALTY.clear();
        LawEnforcement.clearVolatile();
        VillageService.clearVolatile();
    }

    private static boolean active() {
        return VillageService.enabled() && !SafeRun.isDisabled(VillageService.FEATURE);
    }

    private static void run(Runnable r) {
        if (active()) {
            SafeRun.run(VillageService.FEATURE, r);
        }
    }

    static boolean lawsEnabled() {
        return LacunaConfig.get(LacunaConfig.VILLAGES_LAWS);
    }

    // ------------------------------------------------------------------------------------------ driver

    /** Villages with a player inside: laws every second, policy effects and the daily routine every 5 seconds. */
    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % LAW_INTERVAL != 0 || !active()) {
            return;
        }
        SafeRun.run(VillageService.FEATURE, () -> {
            boolean slow = tick % DAILY_INTERVAL == 0;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.players().isEmpty()) {
                    continue;
                }
                Set<VillageRecord> occupied = new LinkedHashSet<>();
                for (ServerPlayer p : level.players()) {
                    if (p.isAlive() && !p.isSpectator()) {
                        VillageService.atPlayer(p).ifPresent(occupied::add);
                    }
                }
                for (VillageRecord village : occupied) {
                    if (lawsEnabled()) {
                        LawEnforcement.tick(level, village);
                    }
                    if (slow) {
                        VillageDaily.policyEffects(level, village);
                        VillageDaily.tick(level, village);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------------ reputation

    private static void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getAbstractVillager().level() instanceof ServerLevel level) {
            run(() -> VillageService.at(level, event.getAbstractVillager().blockPosition())
                    .ifPresent(t -> VillageService.change(level, t, p, ReputationMath.Change.TRADE, 1)));
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        Entity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        run(() -> {
            CaravanEffect.onDeath(level, victim);
            if (!(event.getSource().getEntity() instanceof ServerPlayer p)) {
                return;
            }
            VillageService.at(level, victim.blockPosition()).ifPresent(t -> {
                if (victim instanceof Enemy) {
                    VillageService.change(level, t, p, ReputationMath.Change.HOSTILE_KILL, 1);
                } else if (victim instanceof Villager || victim instanceof IronGolem golem && !golem.isPlayerCreated()) {
                    VillageService.change(level, t, p, ReputationMath.Change.KILL_VILLAGER, 1);
                }
            });
        });
    }

    private static void onAttack(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (!(event.getEntity() instanceof ServerPlayer p) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(target instanceof Villager) && !(target instanceof IronGolem golem && !golem.isPlayerCreated())) {
            return;
        }
        run(() -> {
            long now = level.getGameTime();
            Long last = LAST_HURT_PENALTY.get(p.getUUID());
            if (last != null && now - last < 100) {
                return; // one penalty per 5 s of beating, not per swing
            }
            VillageService.at(level, target.blockPosition()).ifPresent(t -> {
                LAST_HURT_PENALTY.put(p.getUUID(), now);
                VillageService.change(level, t, p, ReputationMath.Change.HURT_VILLAGER, 1);
            });
        });
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        run(() -> VillageService.at(level, pos).ifPresent(t -> {
            if (t.isFounder(p.getUUID())) {
                return; // a founder may rebuild their own village
            }
            if (state.is(ModTags.Blocks.VILLAGE_PROTECTED)) {
                VillageService.change(level, t, p, ReputationMath.Change.BREAK_PROTECTED, 1);
            }
            boolean crop = state.getBlock() instanceof CropBlock;
            if (crop) {
                // the gardens rule: mature crops on the village farmland belong to the village unless Honored
                if (((CropBlock) state.getBlock()).isMaxAge(state) && level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                        && VillageService.tier(t, p.getUUID()) != ReputationMath.Tier.HONORED) {
                    VillageService.change(level, t, p, ReputationMath.Change.HARVEST, 1);
                }
            } else if (lawsEnabled() && t.hasLaw(VillageLaws.NO_BREAKING)) {
                VillageService.change(level, t, p, ReputationMath.Change.BREAK_INSIDE_LAW, 1);
            }
        }));
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BellBlock) {
            run(() -> VillageService.at(level, pos).ifPresent(t -> bell(level, t, p)));
        } else if (level.getBlockEntity(pos) instanceof Container && !p.isShiftKeyDown()) {
            run(() -> VillageService.at(level, pos).ifPresent(t -> {
                if (VillageService.tier(t, p.getUUID()) == ReputationMath.Tier.HONORED || t.isFounder(p.getUUID())) {
                    return;
                }
                boolean watched = !level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(12), Villager::isAlive).isEmpty();
                if (watched) {
                    VillageService.change(level, t, p, ReputationMath.Change.OPEN_CONTAINER, 1);
                }
            }));
        }
    }

    /** Ringing a village bell: +2 once per day at dawn, −5 after more than 3 rings a day, −3 outside dawn under law 4. */
    static void bell(ServerLevel level, VillageRecord t, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            return;
        }
        VillageRecord.Daily daily = t.daily(p.getUUID(), VillageService.day(level));
        daily.bellRings++;
        boolean law = lawsEnabled() && t.hasLaw(VillageLaws.BELL_AT_DAWN);
        switch (VillageLaws.bell(daily.bellRings, level.getDayTime(), law)) {
            case ANNOYED -> VillageService.change(level, t, p, ReputationMath.Change.BELL_ANNOYED, 1);
            case DAWN_BONUS -> VillageService.change(level, t, p, ReputationMath.Change.BELL_DAWN, 1);
            case LAW_BROKEN -> VillageService.change(level, t, p, ReputationMath.Change.BELL_LAW, 1);
            case NOTHING -> { }
        }
        VillageSavedData.get(level.getServer()).touch();
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getTarget() instanceof AbstractVillager villager)) {
            return;
        }
        if (villager instanceof Villager v && Guilds.enabled() && Guilds.isGuildmaster(v) && !p.isCreative()) {
            GuildType guild = SafeRun.call(VillageService.FEATURE, () -> Guilds.guildOf(v), null);
            if (guild != null && Guilds.guildOf(p) != guild) {
                p.displayClientMessage(Component.translatable("lacuna.guild.members_only",
                        Component.translatable("lacuna.guild." + guild.getSerializedName())), true);
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                return;
            }
        }
        TRADING_WITH.put(p.getUUID(), villager.getId());
    }

    /**
     * Fires after the villager reset its special prices and before the offers are sent: every villager inside a
     * village (vanilla professions and Lacuna's) gets the reputation x price index x policy factor here.
     */
    private static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getContainer() instanceof MerchantMenu menu)) {
            return;
        }
        Integer id = TRADING_WITH.remove(p.getUUID());
        if (id == null || !(p.level() instanceof ServerLevel level) || !(level.getEntity(id) instanceof Villager villager)) {
            return;
        }
        run(() -> VillageService.at(level, villager.blockPosition()).ifPresent(t -> adjustPrices(t, p.getUUID(), menu.getOffers())));
    }

    /** Applies the village price factor (and the coin value to coin trades) as special-price differences. */
    public static void adjustPrices(VillageRecord t, UUID player, Iterable<MerchantOffer> offers) {
        double factor = VillageService.priceFactor(t, player);
        for (MerchantOffer offer : offers) {
            int base = offer.getBaseCostA().getCount();
            double f = factor;
            if (VillageTrades.costsCoins(offer)) {
                f /= t.coinValue;          // a strong coin buys more
            } else if (VillageTrades.paysCoins(offer)) {
                f *= t.coinValue;          // ...and costs more ore to mint
            }
            int delta = (int) Math.round(base * (f - 1.0));
            if (delta != 0) {
                offer.addToSpecialPriceDiff(delta);
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRADING_WITH.remove(event.getEntity().getUUID());
        LAST_HURT_PENALTY.remove(event.getEntity().getUUID());
        VillageService.forget(event.getEntity().getUUID());
    }
}
