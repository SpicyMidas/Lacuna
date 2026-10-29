package com.lacuna.mod.village;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModVillagers;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

/** Guild membership, contracts and Guildmaster trades (spec 18.4). */
public final class Guilds {
    public static final int JOIN_COST = 20;

    /** item, min, max, reward min, reward max per guild. */
    private static final Object[][] SMITHS = {{"minecraft:iron_ingot", 12, 24, 6, 10}, {"lacuna:umbral_steel_ingot", 4, 8, 8, 14},
            {"minecraft:coal", 32, 64, 4, 8}};
    private static final Object[][] GEMCUTTERS = {{"lacuna:ruby", 4, 8, 8, 12}, {"minecraft:lapis_lazuli", 16, 32, 5, 8},
            {"lacuna:divine_glass_shard", 2, 4, 10, 14}};
    private static final Object[][] SCHOLARS = {{"minecraft:paper", 16, 32, 4, 8}, {"minecraft:book", 4, 8, 6, 10},
            {"lacuna:echo_fragment", 1, 3, 8, 14}};

    private Guilds() {}

    static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.VILLAGES_GUILDS);
    }

    public static GuildType guildOf(ServerPlayer player) {
        return GuildType.byName(player.getData(ModAttachments.GUILD));
    }

    /** The contracts a guild posts: item, min, max, reward min, reward max (one is drawn at random per request). */
    static Object[][] contractTable(GuildType guild) {
        return switch (guild) {
            case SMITHS -> SMITHS;
            case GEMCUTTERS -> GEMCUTTERS;
            case SCHOLARS -> SCHOLARS;
        };
    }

    private static Component guildName(GuildType guild) {
        return Component.translatable("lacuna.guild." + guild.getSerializedName());
    }

    /** Joins the board's guild for {@link #JOIN_COST} coins from the inventory (reputation 10+, one guild per person). */
    static CivicScreens.Result join(ServerLevel level, VillageRecord t, ServerPlayer player, GuildType guild) {
        if (!enabled()) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.civic.guild.disabled"));
        }
        GuildType current = guildOf(player);
        if (current != null) {
            return CivicScreens.Result.fail(Component.translatable(current == guild ? "lacuna.guild.already" : "lacuna.guild.other",
                    guildName(current)));
        }
        if (t.reputation(player.getUUID()) < 10) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.guild.need_rep"));
        }
        if (CivicScreens.coins(player) < JOIN_COST) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.guild.need_coins", JOIN_COST));
        }
        CivicScreens.takeCoins(player, JOIN_COST);
        player.setData(ModAttachments.GUILD, guild.getSerializedName());
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
        return CivicScreens.Result.ok(Component.translatable("lacuna.guild.joined", guildName(guild)));
    }

    /** Members of the board's guild without a contract get a random one from the guild's table. */
    static CivicScreens.Result offerContract(ServerLevel level, VillageRecord t, ServerPlayer player, GuildType guild) {
        if (!enabled()) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.civic.guild.disabled"));
        }
        if (guildOf(player) != guild) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.guild.members_only", guildName(guild)));
        }
        if (activeContract(player) != null) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.guild.contract_active"));
        }
        Object[][] table = contractTable(guild);
        RandomSource r = level.getRandom();
        Object[] row = table[r.nextInt(table.length)];
        int count = (Integer) row[1] + r.nextInt((Integer) row[2] - (Integer) row[1] + 1);
        int reward = (Integer) row[3] + r.nextInt((Integer) row[4] - (Integer) row[3] + 1);
        ItemStack contract = new ItemStack(ModItems.GUILD_CONTRACT.get());
        contract.set(ModComponents.GUILD_CONTRACT.get(), new ContractData((String) row[0], count, reward, t.id.toString(), guild.getSerializedName()));
        com.lacuna.mod.util.Give.toPlayer(player, contract);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        return CivicScreens.Result.ok(Component.translatable("lacuna.guild.contract_given"));
    }

    /** The guild contract the player carries (the held one first, then the inventory), or null. */
    static ItemStack activeContract(ServerPlayer player) {
        if (player.getMainHandItem().is(ModItems.GUILD_CONTRACT.get())) {
            return player.getMainHandItem();
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.GUILD_CONTRACT.get())) {
                return inv.getItem(i);
            }
        }
        return null;
    }

    static Item contractItem(ContractData d) {
        ResourceLocation id = ResourceLocation.tryParse(d.itemId());
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /** Hands in the player's contract at the board of its guild: takes the items, pays the coins, +3 reputation. */
    static CivicScreens.Result handIn(ServerLevel level, VillageRecord t, ServerPlayer player, GuildType guild) {
        ItemStack contract = activeContract(player);
        if (contract == null) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.civic.guild.no_contract"));
        }
        ContractData d = GuildContractItem.data(contract);
        Item item = contractItem(d);
        if (d.isEmpty() || item == null || !d.guild().equals(guild.getSerializedName())) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.guild.wrong_board"));
        }
        int have = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(item), d.count(), true);
        if (have < d.count()) {
            return CivicScreens.Result.fail(Component.translatable("lacuna.guild.missing", d.count() - have, item.getDescription()));
        }
        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(item), d.count(), false);
        contract.consume(1, player);
        ItemStack pay = new ItemStack(ModItems.VILLAGE_COIN.get(), d.rewardCoins());
        com.lacuna.mod.util.Give.toPlayer(player, pay);
        VillageService.change(level, t, player, ReputationMath.Change.CONTRACT, 1);
        level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return CivicScreens.Result.ok(Component.translatable("lacuna.guild.contract_done", d.rewardCoins()));
    }

    /** A Guildmaster's guild: stored on the villager, otherwise read from its job-site board. */
    public static GuildType guildOf(Villager villager) {
        GuildType stored = GuildType.byName(villager.getData(ModAttachments.VILLAGER_GUILD));
        if (stored != null) {
            return stored;
        }
        // hasMemoryValue first: Brain#getMemory throws for memories the brain never registered
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) {
            return null;
        }
        GuildType found = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(gp -> gp.dimension() == villager.level().dimension())
                .map(GlobalPos::pos)
                .map(p -> villager.level().getBlockState(p))
                .filter(s -> s.is(ModBlocks.GUILD_BOARD.get()))
                .map(s -> s.getValue(GuildBoardBlock.GUILD))
                .orElse(null);
        if (found != null) {
            villager.setData(ModAttachments.VILLAGER_GUILD, found.getSerializedName());
        }
        return found;
    }

    public static boolean isGuildmaster(Villager villager) {
        return villager.getVillagerData().getProfession() == ModVillagers.GUILDMASTER.get();
    }

    // ------------------------------------------------------------------------------------------ trades

    /** Two listings per level that pick the offer of the villager's guild when its trades are generated. */
    public static List<VillagerTrades.ItemListing> listings(int tier) {
        return List.of((trader, random) -> offer(trader, random, tier, 0), (trader, random) -> offer(trader, random, tier, 1));
    }

    private static MerchantOffer offer(net.minecraft.world.entity.Entity trader, RandomSource random, int tier, int slot) {
        if (!(trader instanceof Villager villager)) {
            return null;
        }
        GuildType guild = guildOf(villager);
        if (guild == null) {
            return null;
        }
        return switch (guild) {
            case SMITHS -> switch (tier * 2 + slot) {
                case 2 -> sell(ModItems.UMBRAL_STEEL_INGOT.get(), 1, 6, 8);
                case 3 -> sell(ModItems.REPAIR_KIT.get(), 1, 4, 8);
                case 4 -> sell(Items.IRON_INGOT, 4, 3, 12);
                case 5 -> sell(ModItems.REPAIR_KIT.get(), 3, 10, 4);
                case 6 -> sell(ModItems.UMBRAL_STEEL_PICKAXE.get(), 1, 20, 2);
                default -> null;
            };
            case GEMCUTTERS -> switch (tier * 2 + slot) {
                case 2 -> sell(ModItems.RUBY.get(), 1, 3, 12);
                case 3 -> sell(Items.LAPIS_LAZULI, 8, 2, 12);
                case 4 -> sell(ModItems.DIVINE_GLASS_SHARD.get(), 1, 5, 2); // rare
                case 5 -> sell(ModItems.ZENITHITE_SHARD.get(), 1, 6, 4);
                case 6 -> sell(Items.AMETHYST_SHARD, 4, 3, 8);
                default -> null;
            };
            case SCHOLARS -> switch (tier * 2 + slot) {
                case 2 -> sell(ModItems.CHRONAL_SHARD.get(), 1, 8, 3);
                case 3 -> sell(Items.PAPER, 12, 1, 12);
                case 4 -> sell(Items.ECHO_SHARD, 1, 10, 3);
                case 5 -> book(trader, random);
                case 6 -> book(trader, random);
                default -> null;
            };
        };
    }

    private static MerchantOffer sell(ItemLike item, int count, int coins, int uses) {
        return new MerchantOffer(new ItemCost(ModItems.VILLAGE_COIN.get(), coins), new ItemStack(item, count), uses, 5, 0.05F);
    }

    private static MerchantOffer book(net.minecraft.world.entity.Entity trader, RandomSource random) {
        var registry = trader.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return registry.getRandomElementOf(EnchantmentTags.TRADEABLE, random).map(holder -> {
            int lvl = 1 + random.nextInt(Math.max(1, holder.value().getMaxLevel()));
            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, lvl));
            return new MerchantOffer(new ItemCost(ModItems.VILLAGE_COIN.get(), 8 + lvl * 4), book, 3, 10, 0.05F);
        }).orElse(null);
    }
}
