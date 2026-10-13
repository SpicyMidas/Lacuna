package com.lacuna.mod.village;

import com.lacuna.mod.logic.VillageBounds;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * Every village record, in the overworld's data storage, with a transient spatial index (128-block cells per
 * dimension) so that "which village is this position in" is a hash lookup plus a few distance checks.
 */
public final class VillageSavedData extends SavedData {
    public static final String NAME = "lacuna_villages";
    private final Map<String, VillageRecord> byKey = new LinkedHashMap<>();
    private final Map<UUID, VillageRecord> byId = new HashMap<>();
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<VillageRecord>>> index = new HashMap<>();

    public static VillageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageSavedData::new, VillageSavedData::load), NAME);
    }

    public Optional<VillageRecord> byKey(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public Optional<VillageRecord> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Collection<VillageRecord> all() {
        return Collections.unmodifiableCollection(byKey.values());
    }

    public int size() {
        return byKey.size();
    }

    /** Adds a record unless one with the same key exists (idempotent); returns the stored record. */
    public VillageRecord add(VillageRecord record) {
        VillageRecord existing = byKey.get(record.key);
        if (existing != null) {
            return existing;
        }
        put(record);
        setDirty();
        return record;
    }

    private void put(VillageRecord record) {
        byKey.put(record.key, record);
        byId.put(record.id, record);
        var cells = index.computeIfAbsent(record.dimension, d -> new Long2ObjectOpenHashMap<>());
        for (long cell : VillageBounds.cells(record.center.getX(), record.center.getZ(), record.radius)) {
            cells.computeIfAbsent(cell, c -> new ArrayList<>(2)).add(record);
        }
    }

    /** The record containing {@code pos} (nearest centre if several overlap). */
    public Optional<VillageRecord> at(ResourceKey<Level> dimension, BlockPos pos) {
        var cells = index.get(dimension);
        if (cells == null) {
            return Optional.empty();
        }
        List<VillageRecord> candidates = cells.get(VillageBounds.cell(pos.getX(), pos.getZ()));
        if (candidates == null) {
            return Optional.empty();
        }
        VillageRecord best = null;
        double bestDist = Double.MAX_VALUE;
        for (VillageRecord t : candidates) {
            if (t.contains(pos)) {
                double d = t.horizontalDistance(pos);
                if (d < bestDist) {
                    bestDist = d;
                    best = t;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public boolean remove(String key) {
        VillageRecord record = byKey.remove(key);
        if (record == null) {
            return false;
        }
        byId.remove(record.id);
        var cells = index.get(record.dimension);
        if (cells != null) {
            for (long cell : VillageBounds.cells(record.center.getX(), record.center.getZ(), record.radius)) {
                List<VillageRecord> list = cells.get(cell);
                if (list != null) {
                    list.remove(record);
                    if (list.isEmpty()) {
                        cells.remove(cell);
                    }
                }
            }
        }
        setDirty();
        return true;
    }

    public void touch() {
        setDirty();
    }

    private static VillageSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageSavedData data = new VillageSavedData();
        ListTag list = tag.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            VillageRecord.load(list.getCompound(i)).ifPresent(r -> {
                if (!data.byKey.containsKey(r.key)) {
                    data.put(r);
                }
            });
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        byKey.values().forEach(t -> list.add(t.save()));
        tag.put("villages", list);
        return tag;
    }
}
