package com.lacuna.mod.township;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.util.SafeRun;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the township id. The first server tick after load registers the township (lazy and idempotent); afterwards
 * it drives law enforcement every 20 ticks and the daily township routine every 100 ticks — only while loaded.
 */
public class TownshipLedgerBlockEntity extends BlockEntity {
    @Nullable
    private UUID townshipId;

    public TownshipLedgerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWNSHIP_LEDGER.get(), pos, state);
    }

    public Optional<Township> township(ServerLevel level) {
        if (!TownshipService.enabled()) {
            return Optional.empty();
        }
        TownshipSavedData data = TownshipSavedData.get(level.getServer());
        if (townshipId == null) {
            townshipId = UUID.randomUUID();
            setChanged();
        }
        long seed = level.getSeed() ^ worldPosition.asLong() * 0x5DEECE66DL;
        return Optional.of(data.register(townshipId, level.dimension(), worldPosition, seed));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TownshipLedgerBlockEntity be) {
        if (!(level instanceof ServerLevel server) || SafeRun.isDisabled(TownshipService.FEATURE)) {
            return;
        }
        long time = level.getGameTime() + pos.asLong();
        if (time % 20 != 0 && be.townshipId != null) {
            return;
        }
        SafeRun.run(TownshipService.FEATURE, () -> be.township(server).ifPresent(t -> {
            LawEnforcement.tick(server, t);
            if (time % Balance.TOWNSHIP_LEDGER_INTERVAL == 0) {
                TownshipDaily.policyEffects(server, t);
                TownshipDaily.tick(server, t);
            }
        }));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (townshipId != null) {
            tag.putUUID("township", townshipId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        townshipId = tag.hasUUID("township") ? tag.getUUID("township") : null;
    }
}
