package com.lacuna.mod.village;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.CouncilMath;
import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server half of the civic block screens. A right click on a civic block sends the player a snapshot of the block's
 * screen; the screen's buttons come back as {@link CivicNet.Action}s, which are checked here (the block is still there
 * and of the right kind, the player can reach it, it belongs to a village) and then run through the very rules the
 * blocks always used ({@link Guilds}, {@link JudgeBenchBlock#pay}). Every action is answered with a fresh snapshot
 * that carries its outcome; successes also show a short action-bar line.
 */
public final class CivicScreens {
    /** The screens, one per civic block. */
    public enum Kind {
        LEDGER, GUILD, COURT, COUNCIL, MINT, SCHOOL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Outcome of a screen action, shown inside the screen. */
    public record Result(Component message, boolean ok) {
        static final Result NONE = new Result(Component.empty(), true);

        static Result ok(Component message) {
            return new Result(message, true);
        }

        static Result fail(Component message) {
            return new Result(message, false);
        }
    }

    private static final int ACTION_COOLDOWN_TICKS = 4;
    private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();
    /** An open screen asks for a fresh snapshot every second; anything faster is dropped. */
    private static final int REFRESH_COOLDOWN_TICKS = 10;
    private static final Map<UUID, Long> LAST_REFRESH = new ConcurrentHashMap<>();

    private CivicScreens() {}

    static Kind kindOf(BlockState state) {
        if (state.is(ModBlocks.VILLAGE_LEDGER.get())) {
            return Kind.LEDGER;
        }
        if (state.is(ModBlocks.GUILD_BOARD.get())) {
            return Kind.GUILD;
        }
        if (state.is(ModBlocks.JUDGE_BENCH.get())) {
            return Kind.COURT;
        }
        if (state.is(ModBlocks.COUNCIL_TABLE.get())) {
            return Kind.COUNCIL;
        }
        if (state.is(ModBlocks.MINT_PRESS.get())) {
            return Kind.MINT;
        }
        if (state.is(ModBlocks.SCHOOL_DESK.get())) {
            return Kind.SCHOOL;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ opening and actions

    /** A right click on a civic block: opens its screen (a ledger outside every village founds one first). */
    static void open(ServerLevel level, ServerPlayer player, BlockPos pos) {
        SafeRun.run(VillageService.FEATURE, () -> {
            BlockState state = level.getBlockState(pos);
            Kind kind = kindOf(state);
            if (kind == null) {
                return;
            }
            Optional<VillageRecord> t = kind == Kind.LEDGER ? VillageService.foundPlayerVillage(level, pos, null)
                    : VillageService.atOrTell(level, pos, player);
            t.ifPresent(r -> send(level, player, pos, state, kind, r, Result.NONE, true));
        });
    }

    static void onAction(ServerPlayer player, CivicNet.Action action) {
        SafeRun.run(VillageService.FEATURE, () -> {
            ServerLevel level = player.serverLevel();
            BlockPos pos = action.pos();
            if (player.isSpectator() || !player.isAlive() || !level.isLoaded(pos) || !player.canInteractWithBlock(pos, 1.0)) {
                return;
            }
            long now = level.getGameTime();
            Long last = LAST_ACTION.get(player.getUUID());
            boolean refresh = "refresh".equals(action.action());
            if (!refresh && last != null && now - last < ACTION_COOLDOWN_TICKS && now >= last) {
                return;
            }
            if (refresh) {
                Long lastRefresh = LAST_REFRESH.get(player.getUUID());
                if (lastRefresh != null && now - lastRefresh < REFRESH_COOLDOWN_TICKS && now >= lastRefresh) {
                    return;
                }
                LAST_REFRESH.put(player.getUUID(), now);
            }
            BlockState state = level.getBlockState(pos);
            Kind kind = kindOf(state);
            if (kind == null) {
                return;
            }
            Optional<VillageRecord> found = VillageService.at(level, pos);
            if (found.isEmpty()) {
                return;
            }
            VillageRecord t = found.get();
            if (!refresh) {
                LAST_ACTION.put(player.getUUID(), now);
            }
            Result result = switch (kind) {
                case GUILD -> {
                    GuildType guild = state.getValue(GuildBoardBlock.GUILD);
                    yield switch (action.action()) {
                        case "join" -> Guilds.join(level, t, player, guild);
                        case "contract" -> Guilds.offerContract(level, t, player, guild);
                        case "turn_in" -> Guilds.handIn(level, t, player, guild);
                        default -> Result.NONE;
                    };
                }
                case COURT -> "pay".equals(action.action()) ? JudgeBenchBlock.pay(level, t, player, pos) : Result.NONE;
                default -> Result.NONE;
            };
            if (result.ok() && result != Result.NONE) {
                player.displayClientMessage(result.message().copy().withStyle(ChatFormatting.GOLD), true);
            }
            send(level, player, pos, state, kind, t, result, false);
        });
    }

    public static void forget(UUID player) {
        LAST_ACTION.remove(player);
        LAST_REFRESH.remove(player);
    }

    private static void send(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, Kind kind, VillageRecord t,
                             Result result, boolean open) {
        CompoundTag data = snapshot(level, player, state, kind, t);
        PacketDistributor.sendToPlayer(player, new CivicNet.Snapshot(pos.immutable(), kind.id(), data, result.message(), result.ok(), open));
    }

    // ------------------------------------------------------------------------------------------ coins

    /** Village coins anywhere in the player's inventory. */
    static int coins(ServerPlayer player) {
        return ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(ModItems.VILLAGE_COIN.get()), -1, true);
    }

    /** Takes coins from the inventory (creative players pay nothing, as with a consumed held stack). */
    static void takeCoins(ServerPlayer player, int count) {
        if (!player.hasInfiniteMaterials()) {
            ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(ModItems.VILLAGE_COIN.get()), count, false);
        }
    }

    // ------------------------------------------------------------------------------------------ snapshots

    private static CompoundTag snapshot(ServerLevel level, ServerPlayer player, BlockState state, Kind kind, VillageRecord t) {
        UUID id = player.getUUID();
        long today = VillageService.day(level);
        int rep = t.reputation(id);
        CompoundTag tag = new CompoundTag();
        tag.putString("vtype", t.type);
        tag.putString("vid", t.shortId());
        tag.putBoolean("player_village", t.kind == VillageRecord.Kind.PLAYER);
        tag.putBoolean("founder", t.isFounder(id));
        tag.putInt("rep", rep);
        tag.putInt("coins", coins(player));
        tag.putDouble("price", VillageService.priceFactor(t, id));
        switch (kind) {
            case LEDGER -> {
                laws(tag, t, id);
                council(tag, t, today);
                coin(tag, t);
                tag.putInt("radius", t.radius);
                tag.putInt("villagers", level.getEntitiesOfClass(Villager.class, new AABB(t.center).inflate(t.radius, 24, t.radius),
                        v -> v.isAlive() && t.contains(v.blockPosition())).size());
                tag.putInt("known", t.reputations().size());
                tag.putDouble("gain", VillageService.gainMultiplier(t));
                ListTag record = new ListTag();
                VillageRecord.Daily daily = t.daily.get(id);
                for (ReputationMath.Change c : ReputationMath.Change.values()) {
                    int used = daily != null && daily.day == today ? daily.used.getOrDefault(c, 0) : 0;
                    CompoundTag e = new CompoundTag();
                    e.putString("change", c.name().toLowerCase(Locale.ROOT));
                    e.putInt("amount", c.amount);
                    e.putInt("cap", c.dailyCap);
                    e.putInt("used", used);
                    record.add(e);
                }
                tag.put("today", record);
                tag.putInt("bell", daily != null && daily.day == today ? daily.bellRings : 0);
            }
            case COUNCIL -> {
                laws(tag, t, id);
                council(tag, t, today);
            }
            case COURT -> {
                tag.putInt("fine", ReputationMath.coinsForFine(rep, Integer.MAX_VALUE));
                tag.putLong("wait", JudgeBenchBlock.pardonWait(level, t, player));
                tag.putInt("cooldown", JudgeBenchBlock.PARDON_COOLDOWN_DAYS);
                tag.putInt("per_coin", ReputationMath.Change.FINE_PAID.amount);
            }
            case GUILD -> guild(tag, player, state.getValue(GuildBoardBlock.GUILD));
            case MINT -> {
                coin(tag, t);
                tag.putInt("ore", t.oreDelivered);
                double factor = VillageService.priceFactor(t, id);
                ListTag rates = new ListTag();
                for (VillageTrades.MintRate rate : VillageTrades.coinerRates()) {
                    Item item = rate.item().asItem();
                    // the same adjustment as VillageEvents#adjustPrices: minting costs more ore when the coin is strong
                    int base = rate.buy() ? rate.count() : rate.coins();
                    double f = rate.buy() ? factor * t.coinValue : factor / t.coinValue;
                    int now = Math.max(1, Math.min(64, base + (int) Math.round(base * (f - 1.0))));
                    CompoundTag e = new CompoundTag();
                    e.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
                    e.putInt("level", rate.level());
                    e.putBoolean("buy", rate.buy());
                    e.putInt("count", rate.count());
                    e.putInt("coins", rate.coins());
                    e.putInt("now", now);
                    rates.add(e);
                }
                tag.put("rates", rates);
            }
            case SCHOOL -> {
                int[] census = Schools.census(level, t);
                tag.putInt("desks", census[0]);
                tag.putInt("learning", census[1]);
                tag.putInt("graduated", census[2]);
                tag.putInt("working", census[3]);
                tag.putInt("xp", Schools.xpPerLesson());
                tag.putInt("range", Schools.range());
                tag.putBoolean("enabled", LacunaConfig.get(LacunaConfig.VILLAGES_GUILDS));
                tag.putBoolean("taught_today", t.lastDailyDay >= today);
            }
        }
        return tag;
    }

    private static void laws(CompoundTag tag, VillageRecord t, UUID id) {
        tag.putIntArray("laws", t.laws.clone());
        tag.putBoolean("stranger", VillageService.isStranger(t, id));
    }

    private static void council(CompoundTag tag, VillageRecord t, long today) {
        tag.putString("policy", t.policy);
        tag.putLong("policy_left", Math.max(0, t.policyEndDay - today));
        tag.putLong("council_in", t.nextCouncilDay < 0 ? CouncilMath.TERM_DAYS : Math.max(0, t.nextCouncilDay - today));
        double avg = t.weekRepSamples > 0 ? t.weekRepSum / t.weekRepSamples : 0.0;
        tag.putDouble("week_avg", avg);
        tag.putInt("samples", t.weekRepSamples);
        double[] w = CouncilMath.weights(avg);
        double total = 0;
        for (double v : w) {
            total += v;
        }
        ListTag chances = new ListTag();
        for (int i = 0; i < CouncilMath.POLICIES.length; i++) {
            CompoundTag e = new CompoundTag();
            e.putString("policy", CouncilMath.POLICIES[i]);
            e.putDouble("chance", w[i] / total);
            chances.add(e);
        }
        tag.put("chances", chances);
        tag.putBoolean("council_enabled", LacunaConfig.get(LacunaConfig.VILLAGES_COUNCIL));
    }

    private static void coin(CompoundTag tag, VillageRecord t) {
        tag.putDouble("coin_value", t.coinValue);
        tag.putDouble("price_index", t.priceIndex);
    }

    private static void guild(CompoundTag tag, ServerPlayer player, GuildType guild) {
        tag.putString("guild", guild.getSerializedName());
        GuildType member = Guilds.guildOf(player);
        tag.putString("member", member == null ? "" : member.getSerializedName());
        tag.putBoolean("enabled", Guilds.enabled());
        tag.putInt("cost", Guilds.JOIN_COST);
        tag.putInt("need_rep", 10);
        ListTag table = new ListTag();
        for (Object[] row : Guilds.contractTable(guild)) {
            CompoundTag e = new CompoundTag();
            e.putString("item", (String) row[0]);
            e.putInt("min", (Integer) row[1]);
            e.putInt("max", (Integer) row[2]);
            e.putInt("rmin", (Integer) row[3]);
            e.putInt("rmax", (Integer) row[4]);
            table.add(e);
        }
        tag.put("contracts", table);
        ItemStack contract = Guilds.activeContract(player);
        if (contract != null) {
            ContractData d = GuildContractItem.data(contract);
            Item item = Guilds.contractItem(d);
            CompoundTag a = new CompoundTag();
            a.putString("item", d.itemId());
            a.putInt("count", d.count());
            a.putInt("reward", d.rewardCoins());
            a.putString("guild", d.guild());
            a.putInt("have", item == null ? 0 : ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(item), -1, true));
            tag.put("active", a);
        }
    }
}
