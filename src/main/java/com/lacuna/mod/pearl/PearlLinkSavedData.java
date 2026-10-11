package com.lacuna.mod.pearl;

import java.util.HashMap;
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

/** link_id -> anchor location (spec M5), persisted in the overworld data storage. */
public final class PearlLinkSavedData extends SavedData {
    public static final String NAME = "lacuna_pearl_links";

    public record Anchor(ResourceKey<Level> dimension, BlockPos pos) {}

    private final Map<UUID, Anchor> anchors = new HashMap<>();

    public static PearlLinkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PearlLinkSavedData::new, PearlLinkSavedData::load), NAME);
    }

    private static PearlLinkSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PearlLinkSavedData data = new PearlLinkSavedData();
        ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("dimension"));
            if (!e.hasUUID("id") || dim == null) {
                continue;
            }
            data.anchors.put(e.getUUID("id"), new Anchor(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("pos"))));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        anchors.forEach((id, a) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", id);
            e.putString("dimension", a.dimension().location().toString());
            e.putLong("pos", a.pos().asLong());
            list.add(e);
        });
        tag.put("links", list);
        return tag;
    }

    public Optional<Anchor> get(UUID id) {
        return Optional.ofNullable(anchors.get(id));
    }

    public void put(UUID id, Anchor anchor) {
        anchors.put(id, anchor);
        setDirty();
    }

    public void remove(UUID id) {
        if (anchors.remove(id) != null) {
            setDirty();
        }
    }

    public int size() {
        return anchors.size();
    }
}
