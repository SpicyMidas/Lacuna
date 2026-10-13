package com.lacuna.mod.village;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Operator debug command {@code /lacuna village} (permission 2, under the /lacuna tree):
 * <ul>
 *   <li>{@code /lacuna village} — the record at the source position (created lazily for a loaded vanilla village)
 *       with its civic buildings;</li>
 *   <li>{@code /lacuna village locate} — finds the nearest vanilla village like /locate, registers its record and
 *       prints it (loads the start chunk to the structure-starts status, like /locate);</li>
 *   <li>{@code /lacuna village list} — every known record.</li>
 * </ul>
 * Output lines start with "[village]" so smoke logs can be grepped.
 */
public final class VillageCommand {
    private VillageCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("village")
                .executes(ctx -> info(ctx.getSource()))
                .then(Commands.literal("locate").executes(ctx -> locate(ctx.getSource())))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));
    }

    private static int info(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        if (!VillageService.enabled()) {
            source.sendFailure(Component.literal("[village] the village civic system is disabled (villages.enabled)"));
            return 0;
        }
        Optional<VillageRecord> record = VillageService.at(level, pos);
        if (record.isEmpty()) {
            source.sendFailure(Component.literal("[village] no village at " + pos.toShortString()
                    + " (records are created when a loaded vanilla village is visited; try /lacuna village locate)"));
            return 0;
        }
        print(source, level, record.get());
        return 1;
    }

    private static int locate(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPos.containing(source.getPosition());
        Optional<HolderSet.Named<Structure>> villages = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE);
        if (villages.isEmpty() || !VillageService.enabled()) {
            source.sendFailure(Component.literal("[village] no village structures (or the village civic system is disabled)"));
            return 0;
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, villages.get(), from, 100, false);
        if (found == null) {
            source.sendFailure(Component.literal("[village] no village within 100 chunks of " + from.toShortString()));
            return 0;
        }
        BlockPos at = found.getFirst();
        ChunkAccess chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = chunk.getStartForStructure(found.getSecond().value());
        if (start == null || !start.isValid()) {
            source.sendFailure(Component.literal("[village] structure start missing at " + at.toShortString()));
            return 0;
        }
        VillageRecord record = VillageService.register(level, start);
        String structure = found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?");
        source.sendSuccess(() -> Component.literal("[village] nearest " + structure + " starts in chunk " + start.getChunkPos()
                + ", " + start.getPieces().size() + " pieces"), false);
        print(source, level, record, start);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        var all = VillageSavedData.get(source.getServer()).all();
        source.sendSuccess(() -> Component.literal("[village] " + all.size() + " village records"), false);
        int shown = 0;
        for (VillageRecord r : all) {
            if (shown++ >= 30) {
                break;
            }
            source.sendSuccess(() -> Component.literal("[village] " + r.shortId() + " " + r.kind + " " + r.type + " in "
                    + r.dimension.location() + " at " + r.center.toShortString() + " r=" + r.radius
                    + " reputations=" + r.reputations().size()), false);
        }
        return all.size();
    }

    private static void print(CommandSourceStack source, ServerLevel level, VillageRecord r) {
        print(source, level, r, VillageService.structureStart(level, r).orElse(null));
    }

    private static void print(CommandSourceStack source, ServerLevel level, VillageRecord r, StructureStart start) {
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[village] %s %s %s: centre %s, radius %d, y %d..%d, plaza %s, laws %d/%d, policy %s, coin %.2f, price index %.2f",
                r.shortId(), r.kind, r.type, r.center.toShortString(), r.radius, r.minY, r.maxY, r.plaza.toShortString(),
                r.laws[0], r.laws[1], r.policy, r.coinValue, r.priceIndex)), false);
        if (start != null) {
            List<String> civic = new ArrayList<>();
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof PoolElementStructurePiece pe) {
                    VillagePools.civicBuilding(pe.getElement()).ifPresent(name -> {
                        BoundingBox b = piece.getBoundingBox();
                        civic.add(name + " at " + ((b.minX() + b.maxX()) / 2) + " " + b.minY() + " " + ((b.minZ() + b.maxZ()) / 2));
                    });
                }
            }
            source.sendSuccess(() -> Component.literal("[village] civic buildings: " + (civic.isEmpty() ? "none" : String.join(", ", civic))), false);
        }
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            VillageService.describe(level, r, player);
        }
    }
}
