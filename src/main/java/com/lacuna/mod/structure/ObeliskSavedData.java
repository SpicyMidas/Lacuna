package com.lacuna.mod.structure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Obelisk rewards: which player already got a reward from which obelisk, and each obelisk's cooldown. */
public final class ObeliskSavedData extends SavedData {
    public static final String NAME = "lacuna_obelisks";
    private final Set<String> claimed = new HashSet<>();
    private final Map<String, Long> cooldownUntil = new HashMap<>();

    public static ObeliskSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ObeliskSavedData::new, ObeliskSavedData::load), NAME);
    }

    static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "|" + pos.asLong();
    }

    public boolean claimed(UUID player, String obelisk) {
        return claimed.contains(player + "|" + obelisk);
    }

    public long cooldownUntil(String obelisk) {
        return cooldownUntil.getOrDefault(obelisk, 0L);
    }

    public void reward(UUID player, String obelisk, long until) {
        claimed.add(player + "|" + obelisk);
        cooldownUntil.put(obelisk, until);
        setDirty();
    }

    private static ObeliskSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ObeliskSavedData data = new ObeliskSavedData();
        ListTag list = tag.getList("claimed", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.claimed.add(list.getString(i));
        }
        CompoundTag cd = tag.getCompound("cooldowns");
        for (String k : cd.getAllKeys()) {
            data.cooldownUntil.put(k, cd.getLong(k));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        claimed.forEach(s -> list.add(StringTag.valueOf(s)));
        tag.put("claimed", list);
        CompoundTag cd = new CompoundTag();
        cooldownUntil.forEach(cd::putLong);
        tag.put("cooldowns", cd);
        return tag;
    }
}
