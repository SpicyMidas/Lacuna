package com.lacuna.mod.village;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/**
 * Schools and libraries (spec 18.4): once a day at dawn, up to 40 working villagers of the village within 24 blocks
 * of a school desk or lectern gain 10 XP, never beyond the level-3 threshold, so natural levelling (on their next
 * trade) stops at level 3. Desks are found with a palette-filtered scan of loaded chunks only.
 */
public final class Schools {
    private static final int XP = 10;
    private static final int RANGE = 24;

    private Schools() {}

    public static int run(ServerLevel level, VillageRecord t) {
        List<BlockPos> desks = desks(level, t);
        if (desks.isEmpty()) {
            return 0;
        }
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(t.center).inflate(t.radius, 24, t.radius),
                v -> v.isAlive() && !v.isBaby());
        villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(t.center.getX(), t.center.getY(), t.center.getZ())));
        int taught = 0;
        int cap = VillagerData.getMinXpPerLevel(3);
        for (Villager v : villagers.subList(0, Math.min(villagers.size(), Balance.VILLAGE_MAX_VILLAGERS))) {
            VillagerProfession prof = v.getVillagerData().getProfession();
            if (prof == VillagerProfession.NONE || prof == VillagerProfession.NITWIT || v.getVillagerData().getLevel() >= 3
                    || v.getVillagerXp() >= cap) {
                continue;
            }
            boolean nearDesk = desks.stream().anyMatch(d -> d.distSqr(v.blockPosition()) <= RANGE * RANGE);
            if (nearDesk) {
                v.setVillagerXp(Math.min(cap, v.getVillagerXp() + XP));
                taught++;
            }
        }
        return taught;
    }

    private static boolean isDesk(BlockState s) {
        return s.is(ModBlocks.SCHOOL_DESK.get()) || s.is(Blocks.LECTERN);
    }

    static List<BlockPos> desks(ServerLevel level, VillageRecord t) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = t.center;
        int minSec = SectionPos.blockToSectionCoord(Math.max(level.getMinBuildHeight(), c.getY() - 16));
        int maxSec = SectionPos.blockToSectionCoord(Math.min(level.getMaxBuildHeight() - 1, c.getY() + 24));
        int r = t.radius;
        for (int cx = SectionPos.blockToSectionCoord(c.getX() - r); cx <= SectionPos.blockToSectionCoord(c.getX() + r); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(c.getZ() - r); cz <= SectionPos.blockToSectionCoord(c.getZ() + r); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int sy = minSec; sy <= maxSec; sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(Schools::isDesk)) {
                        continue;
                    }
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                if (isDesk(section.getBlockState(x, y, z)) && out.size() < 64) {
                                    out.add(new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
                                }
                            }
                        }
                    }
                }
            }
        }
        return out;
    }
}
