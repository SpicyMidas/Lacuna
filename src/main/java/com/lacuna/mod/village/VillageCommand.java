package com.lacuna.mod.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Operator debug command {@code /lacuna village} (permission 2, under the /lacuna tree):
 * <ul>
 *   <li>{@code /lacuna village} — the record at the source position (created lazily for a loaded vanilla village)
 *       with its civic buildings;</li>
 *   <li>{@code /lacuna village locate} — finds the nearest vanilla village like /locate, registers its record and
 *       prints it (loads the start chunk to the structure-starts status, like /locate);</li>
 *   <li>{@code /lacuna village visit [building]} — finds the nearest village with that civic building (the village
 *       hall unless another is named) and teleports the player 8 blocks in front of its door; {@code plaza} visits the
 *       nearest village of any kind;</li>
 *   <li>{@code /lacuna village list} — every known record.</li>
 * </ul>
 * Output lines start with "[village]" so smoke logs can be grepped.
 */
public final class VillageCommand {
    private static final List<String> VISIT_TARGETS = List.of("village_hall", "mint", "guild_hall", "schoolhouse", "civic_square", "plaza");
    private static final int VISIT_RINGS = 6;

    private VillageCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("village")
                .executes(ctx -> info(ctx.getSource()))
                .then(Commands.literal("locate").executes(ctx -> locate(ctx.getSource())))
                .then(Commands.literal("visit").executes(ctx -> visit(ctx.getSource(), "village_hall"))
                        .then(Commands.argument("building", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VISIT_TARGETS, builder))
                                .executes(ctx -> visit(ctx.getSource(), StringArgumentType.getString(ctx, "building")))))
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
        StructureStart start = nearestStart(source);
        if (start == null) {
            return 0;
        }
        VillageRecord record = VillageService.register(source.getLevel(), start);
        print(source, source.getLevel(), record, start);
        return 1;
    }

    /** The nearest vanilla village start (computed to the structure-starts status like /locate), or null with feedback. */
    private static StructureStart nearestStart(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPos.containing(source.getPosition());
        Optional<HolderSet.Named<Structure>> villages = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE);
        if (villages.isEmpty() || !VillageService.enabled()) {
            source.sendFailure(Component.literal("[village] no village structures (or the village civic system is disabled)"));
            return null;
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, villages.get(), from, 100, false);
        if (found == null) {
            source.sendFailure(Component.literal("[village] no village within 100 chunks of " + from.toShortString()));
            return null;
        }
        BlockPos at = found.getFirst();
        ChunkAccess chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = chunk.getStartForStructure(found.getSecond().value());
        if (start == null || !start.isValid()) {
            source.sendFailure(Component.literal("[village] structure start missing at " + at.toShortString()));
            return null;
        }
        String structure = found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?");
        source.sendSuccess(() -> Component.literal("[village] nearest " + structure + " starts in chunk " + start.getChunkPos()
                + ", " + start.getPieces().size() + " pieces"), false);
        return start;
    }

    /**
     * The nearest village whose pieces include {@code building}: scans the vanilla village placement grid in rings
     * of regions around the source (each candidate chunk is computed to the structure-starts status only, like
     * /locate), at most {@link #VISIT_RINGS} rings. Null when none is found.
     */
    private static StructureStart nearestWith(CommandSourceStack source, String building) {
        ServerLevel level = source.getLevel();
        var set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(BuiltinStructureSets.VILLAGES);
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
            return null;
        }
        var villages = VillageService.villageStructures(level);
        long seed = level.getSeed();
        BlockPos from = BlockPos.containing(source.getPosition());
        int spacing = spread.spacing();
        int rx0 = Math.floorDiv(from.getX() >> 4, spacing);
        int rz0 = Math.floorDiv(from.getZ() >> 4, spacing);
        for (int ring = 0; ring <= VISIT_RINGS; ring++) {
            StructureStart best = null;
            double bestDist = Double.MAX_VALUE;
            for (int rx = rx0 - ring; rx <= rx0 + ring; rx++) {
                for (int rz = rz0 - ring; rz <= rz0 + ring; rz++) {
                    if (Math.max(Math.abs(rx - rx0), Math.abs(rz - rz0)) != ring) {
                        continue;
                    }
                    ChunkPos candidate = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                    ChunkAccess chunk = level.getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS);
                    for (StructureStart start : chunk.getAllStarts().values()) {
                        if (start.isValid() && villages.contains(start.getStructure()) && hasBuilding(start, building)) {
                            double d = candidate.getMiddleBlockPosition(from.getY()).distSqr(from);
                            if (d < bestDist) {
                                bestDist = d;
                                best = start;
                            }
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        source.sendFailure(Component.literal("[village] no village with a " + building + " within " + VISIT_RINGS + " placement rings"));
        return null;
    }

    private static boolean hasBuilding(StructureStart start, String building) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece pe && VillagePools.civicBuilding(pe.getElement()).filter(building::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Teleports the player 8 blocks in front of a civic building's door (or 10 blocks south of the plaza), facing it. */
    private static int visit(CommandSourceStack source, String building) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StructureStart start = building.equals("plaza") ? nearestStart(source) : nearestWith(source, building);
        if (start == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        VillageRecord record = VillageService.register(level, start);
        BlockPos stand = record.plaza.offset(0, 0, 10);
        float yaw = 180.0F; // facing north, toward the plaza
        String what = "plaza";
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pe)
                    || VillagePools.civicBuilding(pe.getElement()).filter(building::equals).isEmpty()) {
                continue;
            }
            if (building.equals(VillagePools.SQUARE)) {
                BoundingBox b = pe.getBoundingBox();
                stand = new BlockPos((b.minX() + b.maxX()) / 2, b.minY(), b.maxZ() + 4);
                what = building;
                break;
            }
            for (StructureTemplate.StructureBlockInfo jigsaw : pe.getElement().getShuffledJigsawBlocks(level.getStructureManager(),
                    pe.getPosition(), pe.getRotation(), RandomSource.create(0L))) {
                if (jigsaw.nbt() != null && "minecraft:building_entrance".equals(jigsaw.nbt().getString("name"))) {
                    // a three-quarter view from the street: 5 blocks out of the door, 7 along the street (away from
                    // the plaza, so the view is not blocked by the building across the street), facing the door
                    BlockPos door = jigsaw.pos();
                    Direction out = JigsawBlock.getFrontFacing(jigsaw.state());
                    BlockPos street = door.relative(out, 5);
                    BlockPos a = street.relative(out.getClockWise(), 7);
                    BlockPos b = street.relative(out.getCounterClockWise(), 7);
                    stand = a.distSqr(record.plaza) >= b.distSqr(record.plaza) ? a : b;
                    yaw = (float) (Math.atan2(door.getZ() - stand.getZ(), door.getX() - stand.getX()) * 180.0 / Math.PI) - 90.0F;
                    what = building;
                }
            }
            if (what.equals(building)) {
                break;
            }
        }
        // generate/load the target chunk first: Level.getHeight answers the world bottom for unloaded chunks
        int y = level.getChunk(stand.getX() >> 4, stand.getZ() >> 4)
                .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stand.getX() & 15, stand.getZ() & 15) + 1;
        player.teleportTo(level, stand.getX() + 0.5, y, stand.getZ() + 0.5, yaw, 10.0F);
        String target = what;
        BlockPos at = new BlockPos(stand.getX(), y, stand.getZ());
        source.sendSuccess(() -> Component.literal("[village] visiting the " + target + " of " + record.shortId() + " from "
                + at.toShortString()), false);
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
