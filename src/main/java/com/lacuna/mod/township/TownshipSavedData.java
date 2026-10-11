package com.lacuna.mod.township;

import com.lacuna.mod.Balance;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** All township records, in the overworld's data storage. */
public final class TownshipSavedData extends SavedData {
    public static final String NAME = "lacuna_townships";
    private final Map<UUID, Township> townships = new LinkedHashMap<>();

    public static TownshipSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TownshipSavedData::new, TownshipSavedData::load), NAME);
    }

    public Optional<Township> get(UUID id) {
        return Optional.ofNullable(townships.get(id));
    }

    public Collection<Township> all() {
        return townships.values();
    }

    /** Lazily registers the township of a ledger (idempotent: an existing id is returned untouched). */
    public Township register(UUID id, ResourceKey<Level> dimension, BlockPos ledger, long seed) {
        Township t = townships.get(id);
        if (t == null) {
            t = new Township(id, dimension, ledger, Balance.TOWNSHIP_RADIUS, seed);
            townships.put(id, t);
            setDirty();
        }
        return t;
    }

    /** The township whose radius contains {@code pos} (nearest centre if several overlap). */
    public Optional<Township> at(ResourceKey<Level> dimension, BlockPos pos) {
        Township best = null;
        double bestDist = Double.MAX_VALUE;
        for (Township t : townships.values()) {
            if (t.contains(dimension, pos)) {
                double d = t.horizontalDistance(pos);
                if (d < bestDist) {
                    bestDist = d;
                    best = t;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public void remove(UUID id) {
        if (townships.remove(id) != null) {
            setDirty();
        }
    }

    public void touch() {
        setDirty();
    }

    private static TownshipSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        TownshipSavedData data = new TownshipSavedData();
        ListTag list = tag.getList("townships", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.hasUUID("id")) {
                Township township = Township.load(t);
                data.townships.put(township.id, township);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        townships.values().forEach(t -> list.add(t.save()));
        tag.put("townships", list);
        return tag;
    }
}
