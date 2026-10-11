package com.lacuna.mod.township;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModVillagers;
import java.util.List;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.block.state.BlockState;

/** Guild membership, contracts and Guildmaster trades (spec 18.4). */
public final class Guilds {
    public static final int JOIN_COST = 20;

    /** item, min, max, reward min, reward max per guild. */
    private static final Object[][] SMITHS = {{"minecraft:iron_ingot", 12, 24, 6, 10}, {"lacuna:umbral_iron_ingot", 4, 8, 8, 14},
            {"minecraft:coal", 32, 64, 4, 8}};
    private static final Object[][] GEMCUTTERS = {{"lacuna:ruby", 4, 8, 8, 12}, {"minecraft:lapis_lazuli", 16, 32, 5, 8},
            {"lacuna:divine_glass_shard", 6, 12, 8, 12}};
    private static final Object[][] SCHOLARS = {{"minecraft:paper", 16, 32, 4, 8}, {"minecraft:book", 4, 8, 6, 10},
            {"lacuna:echo_fragment", 1, 3, 8, 14}};

    private Guilds() {}

    static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.TOWNSHIP_GUILDS);
    }

    public static GuildType guildOf(ServerPlayer player) {
        return GuildType.byName(player.getData(ModAttachments.GUILD));
    }

    static void join(ServerLevel level, Township t, ServerPlayer player, GuildType guild, ItemStack coins) {
        if (!enabled()) {
            return;
        }
        GuildType current = guildOf(player);
        if (current != null) {
            player.displayClientMessage(Component.translatable(current == guild ? "lacuna.guild.already" : "lacuna.guild.other",
                    Component.translatable("lacuna.guild." + current.getSerializedName())), true);
            return;
        }
        if (t.reputation(player.getUUID()) < 10) {
            player.displayClientMessage(Component.translatable("lacuna.guild.need_rep").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (coins.getCount() < JOIN_COST) {
            player.displayClientMessage(Component.translatable("lacuna.guild.need_coins", JOIN_COST), true);
            return;
        }
        coins.consume(JOIN_COST, player);
        player.setData(ModAttachments.GUILD, guild.getSerializedName());
        player.sendSystemMessage(Component.translatable("lacuna.guild.joined", Component.translatable("lacuna.guild." + guild.getSerializedName()))
                .withStyle(ChatFormatting.GOLD));
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    static void offerContract(ServerLevel level, Township t, ServerPlayer player, GuildType guild) {
        if (!enabled()) {
            return;
        }
        if (guildOf(player) != guild) {
            player.displayClientMessage(Component.translatable("lacuna.guild.info", Component.translatable("lacuna.guild." + guild.getSerializedName()),
                    JOIN_COST), false);
            return;
        }
        if (player.getInventory().hasAnyMatching(s -> s.is(ModItems.GUILD_CONTRACT.get()))) {
            player.displayClientMessage(Component.translatable("lacuna.guild.contract_active"), true);
            return;
        }
        Object[][] table = switch (guild) {
            case SMITHS -> SMITHS;
            case GEMCUTTERS -> GEMCUTTERS;
            case SCHOLARS -> SCHOLARS;
        };
        RandomSource r = level.getRandom();
        Object[] row = table[r.nextInt(table.length)];
        int count = (Integer) row[1] + r.nextInt((Integer) row[2] - (Integer) row[1] + 1);
        int reward = (Integer) row[3] + r.nextInt((Integer) row[4] - (Integer) row[3] + 1);
        ItemStack contract = new ItemStack(ModItems.GUILD_CONTRACT.get());
        contract.set(ModComponents.GUILD_CONTRACT.get(), new ContractData((String) row[0], count, reward, t.id.toString(), guild.getSerializedName()));
        if (!player.getInventory().add(contract)) {
            player.drop(contract, false);
        }
        player.displayClientMessage(Component.translatable("lacuna.guild.contract_given"), true);
    }

    static void handIn(ServerLevel level, Township t, ServerPlayer player, GuildType guild, ItemStack contract) {
        ContractData d = GuildContractItem.data(contract);
        ResourceLocation id = ResourceLocation.tryParse(d.itemId());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (d.isEmpty() || item == null || !d.guild().equals(guild.getSerializedName())) {
            player.displayClientMessage(Component.translatable("lacuna.guild.wrong_board"), true);
            return;
        }
        int have = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(item), d.count(), true);
        if (have < d.count()) {
            player.displayClientMessage(Component.translatable("lacuna.guild.missing", d.count() - have, item.getDescription()), true);
            return;
        }
        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(item), d.count(), false);
        contract.consume(1, player);
        ItemStack pay = new ItemStack(ModItems.TOWNSHIP_COIN.get(), d.rewardCoins());
        if (!player.getInventory().add(pay)) {
            player.drop(pay, false);
        }
        TownshipService.change(level, t, player, ReputationMath.Change.CONTRACT, 1);
        player.sendSystemMessage(Component.translatable("lacuna.guild.contract_done", d.rewardCoins()).withStyle(ChatFormatting.GREEN));
        level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** A Guildmaster's guild: stored on the villager, otherwise read from its job-site board. */
    public static GuildType guildOf(Villager villager) {
        GuildType stored = GuildType.byName(villager.getData(ModAttachments.VILLAGER_GUILD));
        if (stored != null) {
            return stored;
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
                case 2 -> sell(ModItems.UMBRAL_IRON_INGOT.get(), 1, 6, 8);
                case 3 -> sell(ModItems.REPAIR_KIT.get(), 1, 4, 8);
                case 4 -> sell(Items.IRON_INGOT, 4, 3, 12);
                case 5 -> sell(ModItems.REPAIR_KIT.get(), 3, 10, 4);
                case 6 -> sell(ModItems.UMBRAL_IRON_PICKAXE.get(), 1, 20, 2);
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
        return new MerchantOffer(new ItemCost(ModItems.TOWNSHIP_COIN.get(), coins), new ItemStack(item, count), uses, 5, 0.05F);
    }

    private static MerchantOffer book(net.minecraft.world.entity.Entity trader, RandomSource random) {
        var registry = trader.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return registry.getRandomElementOf(EnchantmentTags.TRADEABLE, random).map(holder -> {
            int lvl = 1 + random.nextInt(Math.max(1, holder.value().getMaxLevel()));
            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, lvl));
            return new MerchantOffer(new ItemCost(ModItems.TOWNSHIP_COIN.get(), 8 + lvl * 4), book, 3, 10, 0.05F);
        }).orElse(null);
    }

    static boolean boardIsGuild(BlockState state, GuildType guild) {
        return state.is(ModBlocks.GUILD_BOARD.get()) && state.getValue(GuildBoardBlock.GUILD) == guild;
    }
}
