package com.lacuna.mod.township;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.TownshipLaws;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
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

/** Game-bus hooks that change township reputation and apply its effects (spec 18.2). All wrapped in SafeRun. */
public final class TownshipEvents {
    private static final Map<UUID, Integer> TRADING_WITH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_HURT_PENALTY = new ConcurrentHashMap<>();

    private TownshipEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(TownshipEvents::onTrade);
        bus.addListener(TownshipEvents::onDeath);
        bus.addListener(TownshipEvents::onAttack);
        bus.addListener(TownshipEvents::onBreak);
        bus.addListener(TownshipEvents::onRightClickBlock);
        bus.addListener(TownshipEvents::onEntityInteract);
        bus.addListener(TownshipEvents::onContainerOpen);
        bus.addListener(TownshipEvents::onLogout);
        bus.addListener(TownshipTrades::onVillagerTrades);
        bus.addListener(TownshipTrades::onTrade);
    }

    public static void clearVolatile() {
        TRADING_WITH.clear();
        LAST_HURT_PENALTY.clear();
        LawEnforcement.clearVolatile();
    }

    private static void run(Runnable r) {
        if (TownshipService.enabled() && !com.lacuna.mod.util.SafeRun.isDisabled(TownshipService.FEATURE)) {
            SafeRun.run(TownshipService.FEATURE, r);
        }
    }

    private static void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getAbstractVillager().level() instanceof ServerLevel level) {
            run(() -> TownshipService.at(level, event.getAbstractVillager().blockPosition())
                    .ifPresent(t -> TownshipService.change(level, t, p, ReputationMath.Change.TRADE, 1)));
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        Entity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        run(() -> {
            com.lacuna.mod.township.CaravanEffect.onDeath(level, victim);
            if (!(event.getSource().getEntity() instanceof ServerPlayer p)) {
                return;
            }
            TownshipService.at(level, victim.blockPosition()).ifPresent(t -> {
                if (victim instanceof Enemy && t.horizontalDistance(victim.blockPosition()) <= 32) {
                    TownshipService.change(level, t, p, ReputationMath.Change.HOSTILE_KILL, 1);
                } else if (victim instanceof Villager || victim instanceof IronGolem golem && !golem.isPlayerCreated()) {
                    TownshipService.change(level, t, p, ReputationMath.Change.KILL_VILLAGER, 1);
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
            TownshipService.at(level, target.blockPosition()).ifPresent(t -> {
                LAST_HURT_PENALTY.put(p.getUUID(), now);
                TownshipService.change(level, t, p, ReputationMath.Change.HURT_VILLAGER, 1);
            });
        });
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        run(() -> TownshipService.at(level, pos).ifPresent(t -> {
            if (state.is(ModTags.Blocks.TOWNSHIP_PROTECTED)) {
                TownshipService.change(level, t, p, ReputationMath.Change.BREAK_PROTECTED, 1);
            }
            if (t.hasLaw(TownshipLaws.NO_BREAKING) && t.insideWalls(pos)) {
                TownshipService.change(level, t, p, ReputationMath.Change.BREAK_INSIDE_LAW, 1);
            }
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                    && TownshipService.tier(t, p.getUUID()) != ReputationMath.Tier.HONORED) {
                TownshipService.change(level, t, p, ReputationMath.Change.HARVEST, 1);
            }
        }));
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BellBlock) {
            run(() -> TownshipService.at(level, pos).ifPresent(t -> bell(level, t, p)));
        } else if (level.getBlockEntity(pos) instanceof Container && !p.isShiftKeyDown()) {
            run(() -> TownshipService.at(level, pos).ifPresent(t -> {
                if (TownshipService.tier(t, p.getUUID()) == ReputationMath.Tier.HONORED) {
                    return;
                }
                boolean watched = !level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(12), Villager::isAlive).isEmpty();
                if (watched && t.insideWalls(pos)) {
                    TownshipService.change(level, t, p, ReputationMath.Change.OPEN_CONTAINER, 1);
                }
            }));
        }
    }

    /** Ringing a township bell: +2 once per day at dawn, −5 after more than 3 rings a day, −3 outside dawn under law 4. */
    static void bell(ServerLevel level, Township t, ServerPlayer p) {
        Township.Daily daily = t.daily(p.getUUID(), TownshipService.day(level));
        daily.bellRings++;
        boolean dawn = TownshipLaws.isDawn(level.getDayTime());
        if (daily.bellRings > 3) {
            TownshipService.change(level, t, p, ReputationMath.Change.BELL_ANNOYED, 1);
        } else if (dawn) {
            TownshipService.change(level, t, p, ReputationMath.Change.BELL_DAWN, 1);
        } else if (t.hasLaw(TownshipLaws.BELL_AT_DAWN)) {
            TownshipService.change(level, t, p, ReputationMath.Change.BELL_LAW, 1);
        }
        TownshipSavedData.get(level.getServer()).touch();
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getTarget() instanceof AbstractVillager villager) {
            TRADING_WITH.put(p.getUUID(), villager.getId());
        }
    }

    /** Fires after the villager reset its special prices and before the offers are sent: adjust them here. */
    private static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getContainer() instanceof MerchantMenu menu)) {
            return;
        }
        Integer id = TRADING_WITH.remove(p.getUUID());
        if (id == null || !(p.level() instanceof ServerLevel level) || !(level.getEntity(id) instanceof Villager villager)) {
            return;
        }
        run(() -> TownshipService.at(level, villager.blockPosition()).ifPresent(t -> {
            double factor = TownshipService.priceFactor(t, p.getUUID());
            for (MerchantOffer offer : menu.getOffers()) {
                int base = offer.getBaseCostA().getCount();
                double f = factor;
                if (TownshipTrades.costsCoins(offer)) {
                    f /= t.coinValue;          // a strong coin buys more
                } else if (TownshipTrades.paysCoins(offer)) {
                    f *= t.coinValue;          // ...and costs more ore to mint
                }
                int delta = (int) Math.round(base * (f - 1.0));
                if (delta != 0) {
                    offer.addToSpecialPriceDiff(delta);
                }
            }
        }));
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRADING_WITH.remove(event.getEntity().getUUID());
        LAST_HURT_PENALTY.remove(event.getEntity().getUUID());
    }
}
