package com.lacuna.mod.banish;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent banishment records (spec 9.2), stored in the overworld's data storage so they survive logout,
 * crash and restart. Keyed by banishment id; the same id is stored on the entity in an attachment.
 */
public final class BanishmentSavedData extends SavedData {
    public static final String NAME = "lacuna_banishments";

    public static final class Record {
        public final UUID id;
        public final ResourceKey<Level> originDimension;
        public final BlockPos originPos;
        public final long returnGameTime;
        public final double driftFactor;
        public final UUID entityUuid;
        public final boolean isPlayer;
        public BlockPos lastKnownPos;
        public int missingChecks;

        public Record(UUID id, ResourceKey<Level> originDimension, BlockPos originPos, long returnGameTime, double driftFactor,
                      UUID entityUuid, boolean isPlayer, BlockPos lastKnownPos) {
            this.id = id;
            this.originDimension = originDimension;
            this.originPos = originPos;
            this.returnGameTime = returnGameTime;
            this.driftFactor = driftFactor;
            this.entityUuid = entityUuid;
            this.isPlayer = isPlayer;
            this.lastKnownPos = lastKnownPos;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("origin_dimension", originDimension.location().toString());
            tag.putLong("origin_pos", originPos.asLong());
            tag.putLong("return_time", returnGameTime);
            tag.putDouble("drift", driftFactor);
            tag.putUUID("entity", entityUuid);
            tag.putBoolean("player", isPlayer);
            tag.putLong("last_pos", lastKnownPos.asLong());
            tag.putInt("missing", missingChecks);
            return tag;
        }

        static Optional<Record> load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.hasUUID("entity")) {
                return Optional.empty();
            }
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("origin_dimension"));
            if (dim == null) {
                dim = Level.OVERWORLD.location();
            }
            Record r = new Record(tag.getUUID("id"), ResourceKey.create(Registries.DIMENSION, dim),
                    BlockPos.of(tag.getLong("origin_pos")), tag.getLong("return_time"), tag.getDouble("drift"),
                    tag.getUUID("entity"), tag.getBoolean("player"), BlockPos.of(tag.getLong("last_pos")));
            r.missingChecks = tag.getInt("missing");
            return Optional.of(r);
        }
    }

    private final Map<UUID, Record> records = new LinkedHashMap<>();

    public static SavedData.Factory<BanishmentSavedData> factory() {
        return new SavedData.Factory<>(BanishmentSavedData::new, BanishmentSavedData::load);
    }

    public static BanishmentSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static BanishmentSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        BanishmentSavedData data = new BanishmentSavedData();
        ListTag list = tag.getList("records", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Record.load(list.getCompound(i)).ifPresent(r -> data.records.put(r.id, r));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Record r : records.values()) {
            list.add(r.save());
        }
        tag.put("records", list);
        return tag;
    }

    public Optional<Record> get(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    public Optional<Record> byEntity(UUID entityUuid) {
        for (Record r : records.values()) {
            if (r.entityUuid.equals(entityUuid)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public Collection<Record> all() {
        return java.util.List.copyOf(records.values());
    }

    public void put(Record record) {
        // one record per entity: a new banishment replaces a stale one
        records.values().removeIf(r -> r.entityUuid.equals(record.entityUuid));
        records.put(record.id, record);
        setDirty();
    }

    public void remove(UUID id) {
        if (records.remove(id) != null) {
            setDirty();
        }
    }

    public void touch() {
        setDirty();
    }
}
