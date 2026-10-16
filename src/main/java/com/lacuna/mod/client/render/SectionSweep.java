package com.lacuna.mod.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * Progressive, budgeted scan of the loaded chunk sections inside a sphere, nearest sections first. Sections whose
 * palette cannot contain a match are skipped for the price of a palette check, so a sweep for a rare block is cheap.
 * Shared by the Divine Glass X-ray window and the Mirror Glass reflections; client thread only.
 */
public final class SectionSweep {
    private final long[] sections;
    private final Vec3 center;
    private final double radiusSq;
    private final Predicate<BlockState> match;
    private final List<BlockPos> found = new ArrayList<>();
    private int index;

    public SectionSweep(ClientLevel level, Vec3 center, int radius, Predicate<BlockState> match) {
        this.center = center;
        this.radiusSq = (double) radius * radius;
        this.match = match;
        int cx = SectionPos.blockToSectionCoord(center.x);
        int cy = SectionPos.blockToSectionCoord(center.y);
        int cz = SectionPos.blockToSectionCoord(center.z);
        int r = (radius >> 4) + 1;
        int minY = Math.max(cy - r, level.getMinSection());
        int maxY = Math.min(cy + r, level.getMaxSection() - 1);
        List<long[]> list = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    double d = sectionDistanceSq(x, y, z);
                    if (d <= radiusSq) {
                        list.add(new long[] {SectionPos.asLong(x, y, z), (long) d});
                    }
                }
            }
        }
        list.sort(Comparator.comparingLong(a -> a[1]));
        this.sections = new long[list.size()];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = list.get(i)[0];
        }
    }

    /** Squared distance from the centre to the closest point of the section. */
    private double sectionDistanceSq(int sx, int sy, int sz) {
        double dx = axis(center.x, sx << 4);
        double dy = axis(center.y, sy << 4);
        double dz = axis(center.z, sz << 4);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axis(double c, int min) {
        return c < min ? min - c : c > min + 16 ? c - (min + 16) : 0.0;
    }

    public Vec3 center() {
        return center;
    }

    public boolean done() {
        return index >= sections.length;
    }

    /** Scans sections until the budget (block reads) runs out; returns the budget left. */
    public int run(ClientLevel level, int budget) {
        while (index < sections.length && budget > 0) {
            long packed = sections[index++];
            int sx = SectionPos.x(packed);
            int sy = SectionPos.y(packed);
            int sz = SectionPos.z(packed);
            budget -= 16;
            LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, false);
            if (chunk == null) {
                continue;
            }
            int idx = level.getSectionIndexFromSectionY(sy);
            LevelChunkSection[] all = chunk.getSections();
            if (idx < 0 || idx >= all.length) {
                continue;
            }
            LevelChunkSection section = all[idx];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(match)) {
                continue;
            }
            budget -= 4096;
            int bx = sx << 4;
            int by = sy << 4;
            int bz = sz << 4;
            for (int y = 0; y < 16; y++) {
                double dy = by + y + 0.5 - center.y;
                for (int z = 0; z < 16; z++) {
                    double dz = bz + z + 0.5 - center.z;
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir() || !match.test(state)) {
                            continue;
                        }
                        double dx = bx + x + 0.5 - center.x;
                        if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                            found.add(new BlockPos(bx + x, by + y, bz + z));
                        }
                    }
                }
            }
        }
        return budget;
    }

    /** The matches found, nearest first, at most {@code max}. */
    public List<BlockPos> result(int max) {
        found.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(center)));
        return List.copyOf(found.size() > max ? found.subList(0, max) : found);
    }
}
