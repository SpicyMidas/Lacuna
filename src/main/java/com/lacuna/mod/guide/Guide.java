package com.lacuna.mod.guide;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.Give;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The in-game guide, server side: the Lacuna Almanac handed to every player on their first join (config
 * {@code almanac.giveOnFirstJoin}; the flag lives in the player's persisted NeoForge data, so it survives death and
 * the End exit), and the {@code lacuna:used_relic} advancement trigger of the progression advancements. The book itself
 * is a client screen ({@code client/guide}) whose pages come from {@code assets/lacuna/almanac/entries.json}.
 */
public final class Guide {
    public static final String FEATURE = "almanac";
    /** Key in the player's {@link Player#PERSISTED_NBT_TAG} compound: set once the first Almanac was handed out. */
    static final String GIVEN_KEY = "lacuna_almanac_given";

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Lacuna.MODID);
    public static final DeferredHolder<CriterionTrigger<?>, UsedRelicTrigger> USED_RELIC = TRIGGERS.register("used_relic", UsedRelicTrigger::new);

    private Guide() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        TRIGGERS.register(modBus);
        forgeBus.addListener(Guide::onLogin);
    }

    /** Fires {@code lacuna:used_relic} for {@code stack}: call it where a relic's power took effect (server side). */
    public static void usedRelic(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer sp && !stack.isEmpty()) {
            SafeRun.run("guide_triggers", () -> USED_RELIC.get().trigger(sp, stack));
        }
    }

    /** As {@link #usedRelic(Player, ItemStack)}, for code that no longer holds the stack itself. */
    public static void usedRelic(Player player, ItemLike item) {
        usedRelic(player, new ItemStack(item));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !LacunaConfig.get(LacunaConfig.GIVE_ALMANAC_ON_FIRST_JOIN)) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            CompoundTag data = player.getPersistentData();
            CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
            if (persisted.getBoolean(GIVEN_KEY)) {
                return;
            }
            persisted.putBoolean(GIVEN_KEY, true);
            data.put(Player.PERSISTED_NBT_TAG, persisted);
            Give.toPlayer(player, new ItemStack(ModItems.ALMANAC.get()));
        });
    }
}
