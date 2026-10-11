package com.lacuna.mod.merchant;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.util.SafeRun;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * World-tick spawner for the Last Merchant (like the vanilla wandering trader spawner): every
 * {@code lastMerchantIntervalTicks} (default 3 days) a 25 % chance, by day, near a random Overworld player; the
 * merchant despawns after 3 days. The next attempt time is saved so restarts keep the rhythm.
 */
public final class MerchantSpawner {
    private MerchantSpawner() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0 || !LacunaConfig.get(LacunaConfig.MERCHANT_ENABLED)
                || SafeRun.isDisabled("last_merchant_spawner")) {
            return;
        }
        SafeRun.run("last_merchant_spawner", () -> tick(event.getServer().overworld()));
    }

    private static void tick(ServerLevel level) {
        Data data = Data.get(level);
        long now = level.getGameTime();
        if (data.nextAttempt == 0L) {
            data.schedule(now + interval());
            return;
        }
        if (now < data.nextAttempt) {
            return;
        }
        data.schedule(now + interval());
        if (!level.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING) || !level.isDay()
                || level.getRandom().nextFloat() >= Balance.MERCHANT_SPAWN_CHANCE) {
            return;
        }
        List<ServerPlayer> players = level.getPlayers(p -> !p.isSpectator() && p.isAlive());
        if (!players.isEmpty()) {
            spawnNear(level, players.get(level.getRandom().nextInt(players.size())));
        }
    }

    private static int interval() {
        return LacunaConfig.get(LacunaConfig.MERCHANT_SPAWN_INTERVAL);
    }

    /** Tries ten spots 16-48 blocks from the player on dry, solid ground. */
    public static LastMerchant spawnNear(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 10; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            int dist = 16 + r.nextInt(33);
            int x = player.getBlockX() + (int) (Math.cos(angle) * dist);
            int z = player.getBlockZ() + (int) (Math.sin(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!level.getBlockState(pos.below()).isSolid() || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            LastMerchant merchant = ModEntities.LAST_MERCHANT.get().spawn(level, pos, MobSpawnType.EVENT);
            if (merchant != null) {
                merchant.setDespawnDelay(Balance.MERCHANT_LIFETIME);
                merchant.setPriceIndex(SafeRun.call("townships", () -> com.lacuna.mod.township.CaravanEffect.indexNear(level, pos), 1.0));
                merchant.setWanderTarget(player.blockPosition());
                merchant.restrictTo(player.blockPosition(), 16);
                return merchant;
            }
        }
        return null;
    }

    static final class Data extends SavedData {
        static final String NAME = "lacuna_last_merchant";
        long nextAttempt;

        static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), NAME);
        }

        void schedule(long time) {
            nextAttempt = time;
            setDirty();
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data d = new Data();
            d.nextAttempt = tag.getLong("next_attempt");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putLong("next_attempt", nextAttempt);
            return tag;
        }
    }
}
