package com.lacuna.mod.structure;

import com.lacuna.mod.registry.ModTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Bounded ore scan for the Sunken Bell: only already-loaded chunks, sections without any ore are skipped through
 * the palette ({@code maybeHas}), at most a (2r+1)^3 cube, nearest {@code max} positions returned.
 */
public final class OreScan {
    private OreScan() {}

    public static List<BlockPos> scan(ServerLevel level, BlockPos center, int radius, int max) {
        List<BlockPos> found = new ArrayList<>();
        int r2 = radius * radius;
        int minSection = SectionPos.blockToSectionCoord(Math.max(level.getMinBuildHeight(), center.getY() - radius));
        int maxSection = SectionPos.blockToSectionCoord(Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius));
        for (int cx = SectionPos.blockToSectionCoord(center.getX() - radius); cx <= SectionPos.blockToSectionCoord(center.getX() + radius); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(center.getZ() - radius); cz <= SectionPos.blockToSectionCoord(center.getZ() + radius); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue; // never load chunks for this
                }
                for (int sy = minSection; sy <= maxSection; sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(s -> s.is(ModTags.Blocks.C_ORES))) {
                        continue;
                    }
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                if (!section.getBlockState(x, y, z).is(ModTags.Blocks.C_ORES)) {
                                    continue;
                                }
                                BlockPos p = new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
                                if (p.distSqr(center) <= r2) {
                                    found.add(p);
                                }
                            }
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }
}
