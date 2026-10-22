package com.lacuna.mod.client.village;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.VillageLaws;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModItems;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Builds the pages of each civic screen from the server's snapshot (see {@code village/CivicScreens}). */
final class CivicPages {
    record Built(Component title, List<CivicPage> pages, int defaultTab) {}

    /** Law index -> the reputation change its breach costs. */
    private static final ReputationMath.Change[] LAW_PENALTY = {ReputationMath.Change.WEAPON_LAW, ReputationMath.Change.CURFEW_LAW,
            ReputationMath.Change.BREAK_INSIDE_LAW, ReputationMath.Change.BELL_LAW};
    /** Customs that hold in every village, law or not. */
    private static final ReputationMath.Change[] CUSTOMS = {ReputationMath.Change.KILL_VILLAGER, ReputationMath.Change.HURT_VILLAGER,
            ReputationMath.Change.BREAK_PROTECTED, ReputationMath.Change.OPEN_CONTAINER, ReputationMath.Change.HARVEST};

    private CivicPages() {}

    static Built build(String kind, CompoundTag d) {
        return switch (kind) {
            case "ledger" -> new Built(Component.translatable("block.lacuna.village_ledger"),
                    List.of(overview(d), laws(d), council(d), today(d)), 0);
            case "council" -> new Built(Component.translatable("block.lacuna.council_table"), List.of(council(d), laws(d)), 0);
            case "court" -> new Built(Component.translatable("block.lacuna.judge_bench"), List.of(court(d)), 0);
            case "guild" -> {
                boolean member = d.getString("member").equals(d.getString("guild"));
                yield new Built(Component.translatable("lacuna.guild." + d.getString("guild")), List.of(membership(d), contracts(d)),
                        member ? 1 : 0);
            }
            case "mint" -> new Built(Component.translatable("block.lacuna.mint_press"), List.of(coin(d), rates(d)), 0);
            case "school" -> new Built(Component.translatable("block.lacuna.school_desk"), List.of(school(d)), 0);
            default -> new Built(Component.literal(kind), List.of(new CivicPage(Component.literal(kind), ItemStack.EMPTY)), 0);
        };
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("lacuna.civic." + key, args);
    }

    static Component villageName(CompoundTag d) {
        String type = d.getString("vtype");
        return Component.translatableWithFallback("lacuna.village.type." + type, "Village (" + type + ")")
                .append(" " + d.getString("vid"));
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static Component tier(int rep) {
        return Component.translatable("lacuna.village.tier." + ReputationMath.tier(rep).name().toLowerCase(Locale.ROOT));
    }

    private static Component policy(String policy) {
        return Component.translatable("lacuna.village.policy." + policy);
    }

    private static ItemStack stack(net.minecraft.world.level.ItemLike item) {
        return new ItemStack(item);
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.BARRIER);
    }

    private static Component days(long n) {
        return tr(n == 1 ? "day" : "days", n);
    }

    private static Component changeName(ReputationMath.Change c) {
        return tr("change." + c.name().toLowerCase(Locale.ROOT));
    }

    private static Component amount(ReputationMath.Change c) {
        return c.dailyCap > 0 ? tr("amount_capped", signed(c.amount), c.dailyCap) : tr("amount", signed(c.amount));
    }

    private static void reputationRow(CivicPage page, CompoundTag d) {
        int rep = d.getInt("rep");
        page.add(tr("rep", signed(rep), tier(rep))).color(CivicUi.tierColor(rep)).glyph(CivicUi.STAR)
                .detail(tr("rep.detail", fmt(d.getDouble("price"))))
                .bar((rep + 100) / 200.0F)
                .tip(tr("rep", signed(rep), tier(rep)), tr("rep.tip").withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------------------------------ ledger

    private static CivicPage overview(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.overview"), stack(ModBlocks.VILLAGE_LEDGER.get()));
        boolean founded = d.getBoolean("player_village");
        page.add(villageName(d)).icon(stack(Items.BELL))
                .detail(founded ? (d.getBoolean("founder") ? tr("village.founded_you") : tr("village.founded")) : tr("village.vanilla"));
        reputationRow(page, d);
        boolean stranger = d.getBoolean("stranger");
        page.add(stranger ? tr("status.stranger") : tr("status.citizen")).glyph(stranger ? CivicUi.CROSS : CivicUi.CHECK)
                .detail(stranger ? tr("status.stranger.detail", VillageLaws.STRANGER_BELOW) : tr("status.citizen.detail"));
        page.add(tr("villagers", d.getInt("villagers"))).glyph(CivicUi.PEOPLE)
                .detail(tr("villagers.detail", d.getInt("radius"), d.getInt("known")));
        page.add(tr("policy.now", policy(d.getString("policy")))).icon(stack(ModBlocks.COUNCIL_TABLE.get()))
                .detail(tr("policy.effect." + d.getString("policy")));
        page.add(tr("coin.value", fmt(d.getDouble("coin_value")))).icon(stack(ModItems.VILLAGE_COIN.get()))
                .detail(tr("coin.index", fmt(d.getDouble("price_index"))));
        return page;
    }

    private static CivicPage laws(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.laws"), stack(Items.WRITABLE_BOOK));
        int[] laws = d.getIntArray("laws");
        boolean[] inForce = new boolean[VillageLaws.COUNT];
        for (int l : laws) {
            if (l >= 0 && l < VillageLaws.COUNT) {
                inForce[l] = true;
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < VillageLaws.COUNT; i++) {
                if (inForce[i] != (pass == 0)) {
                    continue;
                }
                ReputationMath.Change c = LAW_PENALTY[i];
                page.add(Component.translatable("lacuna.village.law." + i)).glyph(inForce[i] ? CivicUi.SEAL : CivicUi.QUILL)
                        .dim(!inForce[i])
                        .detail(inForce[i] ? tr("law.penalty", amount(c)) : tr("law.not_here"))
                        .tip(Component.translatable("lacuna.village.law." + i),
                                tr("law." + i + ".tip").withStyle(ChatFormatting.GRAY),
                                tr("law.penalty", amount(c)).withStyle(inForce[i] ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
            }
        }
        for (ReputationMath.Change c : CUSTOMS) {
            page.add(changeName(c)).glyph(CivicUi.SCALES).detail(tr("custom"))
                    .right(Component.literal(signed(c.amount)), CivicUi.BAD)
                    .tip(changeName(c), tr("custom.tip").withStyle(ChatFormatting.GRAY), amount(c).copy().withStyle(ChatFormatting.RED));
        }
        return page;
    }

    private static CivicPage council(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.council"), stack(ModBlocks.COUNCIL_TABLE.get()));
        String now = d.getString("policy");
        long left = d.getLong("policy_left");
        page.add(tr("policy.now", policy(now))).icon(stack(Items.WHITE_BANNER))
                .detail(tr("policy.effect." + now))
                .right("none".equals(now) ? Component.empty() : days(left), CivicUi.INK_SOFT)
                .tip(tr("policy.now", policy(now)), tr("policy.effect." + now).withStyle(ChatFormatting.GRAY));
        if (!d.getBoolean("council_enabled")) {
            page.add(tr("council.disabled")).glyph(CivicUi.CROSS).dim(true);
            return page;
        }
        page.add(tr("council.next", days(d.getLong("council_in")))).glyph(CivicUi.CLOCK).detail(tr("council.term"));
        double avg = d.getDouble("week_avg");
        page.add(tr("council.mood", fmt(avg))).glyph(CivicUi.PEOPLE)
                .detail(tr("council.samples", d.getInt("samples")))
                .tip(tr("council.mood", fmt(avg)), tr("council.mood.tip").withStyle(ChatFormatting.GRAY));
        ListTag chances = d.getList("chances", Tag.TAG_COMPOUND);
        for (int i = 0; i < chances.size(); i++) {
            CompoundTag e = chances.getCompound(i);
            String p = e.getString("policy");
            double chance = e.getDouble("chance");
            page.add(policy(p)).glyph(CivicUi.SEAL).detail(tr("policy.effect." + p))
                    .right(Component.literal(Math.round(chance * 100) + "%"), CivicUi.INK)
                    .bar((float) chance)
                    .tip(tr("council.chance", policy(p), Math.round(chance * 100)), tr("policy.effect." + p).withStyle(ChatFormatting.GRAY));
        }
        return page;
    }

    private static CivicPage today(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.today"), stack(Items.CLOCK));
        page.empty(tr("today.empty"));
        double gain = d.getDouble("gain");
        ListTag list = d.getList("today", Tag.TAG_COMPOUND);
        boolean any = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int used = e.getInt("used");
            if (used == 0) {
                continue;
            }
            any = true;
            ReputationMath.Change c = ReputationMath.Change.valueOf(e.getString("change").toUpperCase(Locale.ROOT));
            int cap = e.getInt("cap");
            boolean gainKind = e.getInt("amount") > 0;
            Component value = Component.literal((gainKind ? "+" : "-") + used + (cap > 0 ? " / " + cap : ""));
            page.add(changeName(c)).glyph(gainKind ? CivicUi.CHECK : CivicUi.CROSS)
                    .detail(cap > 0 ? (used >= cap ? tr("today.capped") : tr("today.room", cap - used)) : tr("today.uncapped"))
                    .right(value, gainKind ? CivicUi.GOOD : CivicUi.BAD)
                    .bar(cap > 0 ? Math.min(1F, used / (float) cap) : -1);
        }
        if (any && gain > 1.0) {
            page.add(tr("today.festival", fmt(gain))).glyph(CivicUi.STAR).color(CivicUi.GOLD);
        }
        int bell = d.getInt("bell");
        if (any || bell > 0) {
            page.add(tr("today.bell", bell)).icon(stack(Items.BELL)).detail(tr("today.bell.detail"));
        }
        if (!any && bell == 0) {
            page.rows.clear();
        }
        return page;
    }

    // ------------------------------------------------------------------------------------------ court

    private static CivicPage court(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.court"), stack(ModBlocks.JUDGE_BENCH.get()));
        int rep = d.getInt("rep");
        int fine = d.getInt("fine");
        long wait = d.getLong("wait");
        int coins = d.getInt("coins");
        int perCoin = d.getInt("per_coin");
        reputationRow(page, d);
        if (fine > 0) {
            page.add(tr("court.fine", fine)).glyph(CivicUi.SCALES).color(CivicUi.BAD)
                    .detail(tr("court.fine.detail", perCoin))
                    .tip(tr("court.fine", fine), tr("court.fine.tip", perCoin).withStyle(ChatFormatting.GRAY));
        } else {
            page.add(tr("court.clear")).glyph(CivicUi.CHECK).color(CivicUi.GOOD).detail(tr("court.clear.detail"));
        }
        page.add(wait > 0 ? tr("court.wait", days(wait)) : tr("court.ready")).glyph(CivicUi.CLOCK)
                .detail(tr("court.cooldown", d.getInt("cooldown")));
        page.add(tr("court.purse", coins)).icon(stack(ModItems.VILLAGE_COIN.get()))
                .detail(fine > 0 && coins < fine ? tr("court.partial", coins * perCoin) : tr("court.purse.detail"));
        int pay = Math.min(fine, coins);
        int after = Math.min(0, rep + pay * perCoin);
        Component tip = fine <= 0 ? Component.translatable("lacuna.court.nothing_owed")
                : wait > 0 ? Component.translatable("lacuna.court.too_soon")
                : coins <= 0 ? tr("court.no_coins") : tr("court.pay.tip", pay, signed(after));
        page.button(pay > 0 ? tr("court.pay", pay) : tr("court.pay_none"), "pay", fine > 0 && wait == 0 && coins > 0, tip);
        return page;
    }

    // ------------------------------------------------------------------------------------------ guild board

    private static CivicPage membership(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.membership"), stack(ModBlocks.GUILD_BOARD.get()));
        String guild = d.getString("guild");
        String member = d.getString("member");
        Component name = Component.translatable("lacuna.guild." + guild);
        if (!d.getBoolean("enabled")) {
            page.add(tr("guild.disabled")).glyph(CivicUi.CROSS).dim(true);
            return page;
        }
        int rep = d.getInt("rep");
        int coins = d.getInt("coins");
        int cost = d.getInt("cost");
        int needRep = d.getInt("need_rep");
        if (member.equals(guild)) {
            page.add(tr("guild.member", name)).glyph(CivicUi.CHECK).color(CivicUi.GOOD).detail(tr("guild.member.detail"));
        } else if (!member.isEmpty()) {
            page.add(Component.translatable("lacuna.guild.other", Component.translatable("lacuna.guild." + member)))
                    .glyph(CivicUi.CROSS).color(CivicUi.BAD).detail(tr("guild.one_guild"));
        } else {
            page.add(tr("guild.not_member", name)).glyph(CivicUi.QUILL).detail(tr("guild.join_hint", cost));
        }
        boolean repOk = rep >= needRep;
        boolean coinsOk = coins >= cost;
        boolean free = member.isEmpty();
        if (!member.equals(guild)) {
            page.add(tr("guild.req.rep", needRep)).glyph(repOk ? CivicUi.CHECK : CivicUi.CROSS)
                    .right(Component.literal(signed(rep) + " / " + needRep), repOk ? CivicUi.GOOD : CivicUi.BAD);
            page.add(tr("guild.req.coins", cost)).icon(stack(ModItems.VILLAGE_COIN.get()))
                    .right(Component.literal(coins + " / " + cost), coinsOk ? CivicUi.GOOD : CivicUi.BAD);
            page.add(tr("guild.req.one")).glyph(free ? CivicUi.CHECK : CivicUi.CROSS);
        }
        page.add(tr("guild.perks")).icon(stack(Items.EMERALD)).detail(tr("guild.perks.detail"));
        if (!member.equals(guild)) {
            Component tip = !free ? tr("guild.one_guild") : !repOk ? Component.translatable("lacuna.guild.need_rep")
                    : !coinsOk ? Component.translatable("lacuna.guild.need_coins", cost) : tr("guild.join.tip", name, cost);
            page.button(tr("guild.join", cost), "join", free && repOk && coinsOk, tip);
        }
        return page;
    }

    private static CivicPage contracts(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.contracts"), stack(ModItems.GUILD_CONTRACT.get()));
        String guild = d.getString("guild");
        boolean member = d.getString("member").equals(guild) && d.getBoolean("enabled");
        boolean hasActive = d.contains("active");
        boolean ready = false;
        if (hasActive) {
            CompoundTag a = d.getCompound("active");
            Item item = item(a.getString("item"));
            int count = a.getInt("count");
            int have = a.getInt("have");
            boolean here = a.getString("guild").equals(guild);
            ready = here && have >= count;
            page.add(tr("contract.active", count, item.getDescription())).icon(stack(item))
                    .detail(here ? tr("contract.reward", a.getInt("reward"))
                            : tr("contract.elsewhere", Component.translatable("lacuna.guild." + a.getString("guild"))))
                    .right(Component.literal(Math.min(have, count) + "/" + count), ready ? CivicUi.GOOD : CivicUi.INK_SOFT)
                    .bar(count <= 0 ? 0 : Math.min(1F, have / (float) count))
                    .dim(!here);
        } else {
            page.add(member ? tr("contract.none") : tr("contract.members_only")).glyph(CivicUi.QUILL)
                    .detail(member ? tr("contract.none.detail") : tr("contract.members_only.detail"));
        }
        ListTag table = d.getList("contracts", Tag.TAG_COMPOUND);
        for (int i = 0; i < table.size(); i++) {
            CompoundTag e = table.getCompound(i);
            Item item = item(e.getString("item"));
            page.add(tr("contract.posting", e.getInt("min"), e.getInt("max"), item.getDescription())).icon(stack(item))
                    .detail(tr("contract.posting.detail"))
                    .right(tr("contract.coins", e.getInt("rmin"), e.getInt("rmax")), CivicUi.GOLD)
                    .dim(!member);
        }
        Component requestTip = !member ? Component.translatable("lacuna.guild.members_only", Component.translatable("lacuna.guild." + guild))
                : hasActive ? Component.translatable("lacuna.guild.contract_active") : tr("contract.request.tip");
        page.button(tr("contract.request"), "contract", member && !hasActive, requestTip);
        Component turnInTip = !hasActive ? tr("guild.no_contract") : ready ? tr("contract.turn_in.tip") : tr("contract.turn_in.not_ready");
        page.button(tr("contract.turn_in"), "turn_in", ready, turnInTip);
        return page;
    }

    // ------------------------------------------------------------------------------------------ mint press

    private static CivicPage coin(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.coin"), stack(ModItems.VILLAGE_COIN.get()));
        double value = d.getDouble("coin_value");
        page.add(tr("coin.value", fmt(value))).icon(stack(ModItems.VILLAGE_COIN.get()))
                .detail(value > 1.0 ? tr("coin.strong") : value < 1.0 ? tr("coin.weak") : tr("coin.par"))
                .bar((float) ((value - 0.6) / 0.8))
                .tip(tr("coin.value", fmt(value)), tr("coin.value.tip").withStyle(ChatFormatting.GRAY));
        int ore = d.getInt("ore");
        page.add(tr("coin.ore", ore)).icon(stack(Items.RAW_IRON)).detail(tr("coin.ore.detail", 16 - ore % 16))
                .bar((ore % 16) / 16F);
        page.add(tr("coin.recovery")).glyph(CivicUi.CLOCK).detail(tr("coin.recovery.detail"));
        page.add(tr("coin.index", fmt(d.getDouble("price_index")))).icon(stack(Items.CHEST)).detail(tr("coin.index.detail"));
        page.add(tr("coin.price", fmt(d.getDouble("price")))).glyph(CivicUi.STAR).detail(tr("coin.price.detail"));
        return page;
    }

    private static CivicPage rates(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.rates"), stack(Items.RAW_GOLD));
        ListTag rates = d.getList("rates", Tag.TAG_COMPOUND);
        for (int i = 0; i < rates.size(); i++) {
            CompoundTag e = rates.getCompound(i);
            Item item = item(e.getString("item"));
            boolean buy = e.getBoolean("buy");
            Component level = Component.translatable("merchant.level." + e.getInt("level"));
            Component title = buy ? tr("rate.mint", e.getInt("count"), item.getDescription())
                    : tr("rate.sell", e.getInt("coins"), e.getInt("count"), item.getDescription());
            page.add(title).icon(stack(item)).detail(tr("rate.level", level))
                    .right(tr(buy ? "rate.now_ore" : "rate.now_coins", e.getInt("now")), buy ? CivicUi.INK : CivicUi.GOLD)
                    .tip(title, tr("rate.level", level).withStyle(ChatFormatting.GRAY),
                            tr(buy ? "rate.now_ore.tip" : "rate.now_coins.tip", e.getInt("now")).withStyle(ChatFormatting.GOLD));
        }
        return page;
    }

    // ------------------------------------------------------------------------------------------ school desk

    private static CivicPage school(CompoundTag d) {
        CivicPage page = new CivicPage(tr("tab.school"), stack(ModBlocks.SCHOOL_DESK.get()));
        if (!d.getBoolean("enabled")) {
            page.add(tr("school.disabled")).glyph(CivicUi.CROSS).dim(true);
            return page;
        }
        page.add(tr("school.lesson", d.getInt("xp"))).icon(stack(Items.BOOK))
                .detail(tr("school.lesson.detail", d.getInt("range")));
        page.add(tr("school.desks", d.getInt("desks"))).icon(stack(Items.LECTERN)).detail(tr("school.desks.detail"));
        int working = d.getInt("working");
        int learning = d.getInt("learning");
        page.add(tr("school.learning", learning)).glyph(CivicUi.PEOPLE).detail(tr("school.learning.detail", working))
                .bar(working <= 0 ? 0 : learning / (float) working);
        page.add(tr("school.graduated", d.getInt("graduated"))).glyph(CivicUi.STAR)
                .detail(tr("school.graduated.detail", Component.translatable("merchant.level.3")));
        page.add(d.getBoolean("taught_today") ? tr("school.done") : tr("school.next")).glyph(CivicUi.CLOCK)
                .detail(tr("school.next.detail"));
        return page;
    }
}
