package com.lacuna.mod.village;

import com.lacuna.mod.util.SafeRun;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps villages free of trees. Trees, huge mushrooms and the other vegetation are decorated after the structures
 * (vegetal decoration comes after the surface structures), so a forest or taiga village used to get trees inside its
 * yards, against its walls, in front of its doors and across its streets; worse, a street or the civic plaza placed
 * after a neighbouring chunk's tree follows the canopy (terrain-matching pieces use the world surface heightmap) and
 * floats on it. This feature runs last (top layer modification, every overworld biome) for each decorated chunk:
 * <ol>
 *   <li>the villages around come from the chunk's structure references (a village start's box is inflated by 12 for
 *       its terrain adaptation, more than any tree reaches, so the chunk always knows the villages its trees touch);</li>
 *   <li>keep-clear zones: every rigid piece (houses, civic buildings, farms, lamps) with 2 blocks around it and 4 over
 *       it, the doorway in front of each building entrance, and every column where a terrain-matching piece (a street,
 *       the civic plaza) places blocks, at any height, plus one column on each side;</li>
 *   <li>natural vegetation in a zone (logs, non-persistent leaves, huge mushroom blocks, vines, cactus, berry bushes;
 *       never a block the piece's own template put there, checked against the template, so the vanilla houses' log
 *       frames and the tree of the plains meeting point stay) takes its whole tree with it: the connected logs, the
 *       leaves only they kept alive (the vanilla decay rule, so nothing floats), what hung on them (vines, cocoa, bee
 *       nests, snow on the leaves); the ground the canopy covered gets its snow like freeze_top_layer gives it;</li>
 *   <li>the trees a village plants on purpose (feature pool elements: the decor trees beside the streets, the trees of
 *       the meeting points) stay, unless they reach into another building, a doorway, or a street not placed yet;</li>
 *   <li>loose plants on the columns of a street that a later chunk still has to place are cleared too, so the street
 *       is laid on the ground and not on a grass tuft.</li>
 * </ol>
 * A chunk removes only trees whose logs lie within {@link #REACH} blocks of it (its own trees: every tree of a chunk
 * that reaches a zone is handled when that chunk is decorated), so every write stays in the decoration's 3x3 window.
 */
public class VillageClearingFeature extends Feature<NoneFeatureConfiguration> {
    public static final String FEATURE = "village_clearing";
    /** Seeds (vegetation in a zone) are looked for this far around the chunk: farther than any of its trees reaches. */
    private static final int SCAN = 12;
    /** A removable tree keeps every log within this many blocks of the chunk (trunks inside, branches around). */
    private static final int REACH = 8;
    private static final int MARGIN = 2;
    private static final int ABOVE = 4;
    /** Vanilla leaves survive up to this many leaf steps from a log. */
    private static final int LEAF = 6;
    private static final int MAX_LOGS = 2048;
    private static final int MAX_BLOB = 1024;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<StructureTemplate, TemplateInfo> TEMPLATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Block, Boolean> STRIPPED = new ConcurrentHashMap<>();

    enum Kind { NONE, LOG, LEAF, CAP, VINE, COLUMN, BUSH, PLANT }

    public VillageClearingFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        SafeRun.run(FEATURE, () -> clear(level, chunk));
        return true; // like freeze_top_layer: a chunk without a village nearby is not a failure
    }

    /** Clears the trees that the chunk's decoration let grow into the villages around it. */
    public static int clear(WorldGenLevel level, ChunkPos chunk) {
        ServerLevel server = level.getLevel();
        Set<Structure> villages = VillageService.villageStructures(server);
        if (villages.isEmpty()) {
            return 0;
        }
        StructureManager structures = level instanceof WorldGenRegion region
                ? server.structureManager().forWorldGenRegion(region) : server.structureManager();
        List<StructureStart> starts = structures.startsForStructure(chunk, villages::contains);
        if (starts.isEmpty()) {
            return 0;
        }
        return new Clearing(level, server.getStructureManager(), chunk, starts).run();
    }

    // ============================================================================================ classification
    static Kind kind(BlockState s) {
        if (s.isAir()) {
            return Kind.NONE;
        }
        Block b = s.getBlock();
        if (b instanceof LeavesBlock) {
            return s.getValue(LeavesBlock.PERSISTENT) ? Kind.NONE : Kind.LEAF;
        }
        if (s.is(BlockTags.LOGS)) {
            return stripped(b) ? Kind.NONE : Kind.LOG;
        }
        if (b == Blocks.MUSHROOM_STEM || b == Blocks.BROWN_MUSHROOM_BLOCK || b == Blocks.RED_MUSHROOM_BLOCK) {
            return Kind.CAP;
        }
        if (b == Blocks.VINE) {
            return Kind.VINE;
        }
        if (b == Blocks.CACTUS || b == Blocks.BAMBOO || b == Blocks.BAMBOO_SAPLING) {
            return Kind.COLUMN;
        }
        if (b == Blocks.SWEET_BERRY_BUSH) {
            return Kind.BUSH;
        }
        if (s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.TALL_FLOWERS) || b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS
                || b == Blocks.FERN || b == Blocks.LARGE_FERN || b == Blocks.DEAD_BUSH) {
            return Kind.PLANT;
        }
        return Kind.NONE;
    }

    private static boolean stripped(Block b) {
        return STRIPPED.computeIfAbsent(b, k -> BuiltInRegistries.BLOCK.getKey(k).getPath().startsWith("stripped_"));
    }

    private static boolean vegetation(Block b) {
        return b instanceof LeavesBlock || kind(b.defaultBlockState()) != Kind.NONE;
    }

    // ============================================================================================ templates
    /** What a template places: its vegetation blocks (to tell them from wild ones), its columns, its entrances. */
    private record TemplateInfo(Long2ObjectMap<Block> vegetation, LongSet columns, List<Entrance> entrances) {}

    private record Entrance(BlockPos pos, Direction front) {}

    private static TemplateInfo info(StructureTemplate template) {
        return TEMPLATES.computeIfAbsent(template, VillageClearingFeature::parse);
    }

    private static TemplateInfo parse(StructureTemplate template) {
        CompoundTag tag = template.save(new CompoundTag());
        ListTag palette = tag.contains("palette", Tag.TAG_LIST) ? tag.getList("palette", Tag.TAG_COMPOUND)
                : tag.getList("palettes", Tag.TAG_LIST).isEmpty() ? new ListTag() : tag.getList("palettes", Tag.TAG_LIST).getList(0);
        Block[] blocks = new Block[palette.size()];
        String[] orientation = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("Name"));
            blocks[i] = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
            orientation[i] = entry.getCompound("Properties").getString("orientation");
        }
        Long2ObjectMap<Block> vegetation = new Long2ObjectOpenHashMap<>();
        LongSet columns = new LongOpenHashSet();
        List<Entrance> entrances = new ArrayList<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ListTag p = entry.getList("pos", Tag.TAG_INT);
            int state = entry.getInt("state");
            if (state < 0 || state >= blocks.length) {
                continue;
            }
            Block block = blocks[state];
            BlockPos pos = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            if (block == Blocks.JIGSAW) {
                if ("minecraft:building_entrance".equals(entry.getCompound("nbt").getString("name"))) {
                    Direction front = Direction.byName(orientation[state].split("_")[0]);
                    if (front != null && front.getAxis().isHorizontal()) {
                        entrances.add(new Entrance(pos, front));
                    }
                }
                continue;
            }
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.STRUCTURE_VOID || block == Blocks.STRUCTURE_BLOCK) {
                continue;
            }
            columns.add(ChunkPos.asLong(pos.getX(), pos.getZ()));
            if (vegetation(block)) {
                vegetation.put(pos.asLong(), block);
            }
        }
        return new TemplateInfo(vegetation, columns, List.copyOf(entrances));
    }

    // ============================================================================================ the pass
    private record Zone(BoundingBox box, boolean strict, @Nullable BoundingBox exempt) {}

    /** Vegetation in a zone; {@code strict}: decor trees go too, unless planted inside {@code exempt} (their own piece). */
    private static final class Seed {
        final long pos;
        boolean strict;
        @Nullable BoundingBox exempt;

        Seed(long pos, boolean strict, @Nullable BoundingBox exempt) {
            this.pos = pos;
            this.strict = strict;
            this.exempt = exempt;
        }

        void merge(boolean strict2, @Nullable BoundingBox exempt2) {
            if (strict2 && !strict) {
                strict = true;
                exempt = exempt2;
            } else if (strict2 && exempt != exempt2) {
                exempt = null; // two pieces: a tree planted inside one still reaches into the other
            }
        }
    }

    private static final class Tree {
        final LongArrayList logs = new LongArrayList();
        final List<Seed> seeds = new ArrayList<>();
        boolean complete = true;
        boolean leafy;
        @Nullable BlockPos decor;
    }

    private static final class Piece {
        final BoundingBox box;
        @Nullable final TemplateInfo template; // null: contents unknown, the whole box counts as the piece's own
        final BlockPos position;
        final Rotation rotation;
        final boolean terrain;
        @Nullable private Long2ObjectMap<Block> own;

        Piece(BoundingBox box, @Nullable TemplateInfo template, BlockPos position, Rotation rotation, boolean terrain) {
            this.box = box;
            this.template = template;
            this.position = position;
            this.rotation = rotation;
            this.terrain = terrain;
        }

        BlockPos world(BlockPos rel) {
            return StructureTemplate.transform(rel, Mirror.NONE, rotation, BlockPos.ZERO).offset(position);
        }

        /** Whether the block at {@code pos} is one this piece placed. Terrain-matching pieces own by column. */
        boolean owns(BlockPos pos, Block block) {
            if (template == null) {
                return box.isInside(pos);
            }
            if (own == null) {
                own = new Long2ObjectOpenHashMap<>();
                for (Long2ObjectMap.Entry<Block> e : template.vegetation().long2ObjectEntrySet()) {
                    BlockPos w = world(BlockPos.of(e.getLongKey()));
                    own.put(terrain ? ChunkPos.asLong(w.getX(), w.getZ()) : w.asLong(), e.getValue());
                }
            }
            if (terrain) {
                return own.get(ChunkPos.asLong(pos.getX(), pos.getZ())) == block;
            }
            return box.isInside(pos) && own.get(pos.asLong()) == block;
        }
    }

    private static final class Clearing {
        final WorldGenLevel level;
        final StructureTemplateManager templates;
        final ChunkPos chunk;
        final List<StructureStart> starts;
        final int minY;
        final int maxY;
        final List<Piece> pieces = new ArrayList<>();
        final List<BlockPos> decor = new ArrayList<>();
        final List<Zone> zones = new ArrayList<>();
        /** Columns of terrain-matching pieces: packed (x, z) to {min y, max y}. */
        final Long2ObjectMap<int[]> paths = new Long2ObjectOpenHashMap<>();
        final Long2ObjectMap<Seed> seeds = new Long2ObjectOpenHashMap<>();
        final Long2ObjectMap<Tree> treeOf = new Long2ObjectOpenHashMap<>();
        final List<Tree> trees = new ArrayList<>();
        final LongSet removed = new LongOpenHashSet();
        final LongSet orphans = new LongOpenHashSet();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Clearing(WorldGenLevel level, StructureTemplateManager templates, ChunkPos chunk, List<StructureStart> starts) {
            this.level = level;
            this.templates = templates;
            this.chunk = chunk;
            this.starts = starts;
            this.minY = level.getMinBuildHeight();
            this.maxY = level.getMaxBuildHeight() - 1;
        }

        // ------------------------------------------------------------------ boxes
        /** Inside the decoration's writable 3x3 chunks, shrunk by {@code inset}. */
        boolean inWrite(int x, int y, int z, int inset) {
            int r = 16 - inset;
            return y >= minY && y <= maxY && x >= chunk.getMinBlockX() - r && x <= chunk.getMaxBlockX() + r
                    && z >= chunk.getMinBlockZ() - r && z <= chunk.getMaxBlockZ() + r;
        }

        boolean inWrite(BlockPos p) {
            return inWrite(p.getX(), p.getY(), p.getZ(), 0);
        }

        boolean near(BoundingBox b, int r) {
            return b.maxX() >= chunk.getMinBlockX() - r && b.minX() <= chunk.getMaxBlockX() + r
                    && b.maxZ() >= chunk.getMinBlockZ() - r && b.minZ() <= chunk.getMaxBlockZ() + r;
        }

        /** Whether the chunk's structures are placed already (the decorated chunk itself: yes, they come first). */
        boolean decorated(int cx, int cz) {
            if ((cx == chunk.x && cz == chunk.z) || !(level instanceof WorldGenRegion)) {
                return true;
            }
            return level.getChunk(cx, cz).getPersistedStatus().isOrAfter(ChunkStatus.FEATURES);
        }

        BlockState state(long pos) {
            return level.getBlockState(cursor.set(pos));
        }

        boolean owned(BlockPos pos, Block block) {
            for (Piece p : pieces) {
                if ((p.terrain || p.box.isInside(pos)) && p.owns(pos, block)) {
                    return true;
                }
            }
            return false;
        }

        /** The kind of a block that grew there by itself (NONE for air, the village's own blocks, everything else). */
        Kind wild(long pos) {
            BlockState s = state(pos);
            Kind k = kind(s);
            if (k == Kind.NONE) {
                return k;
            }
            return owned(BlockPos.of(pos), s.getBlock()) ? Kind.NONE : k;
        }

        // ------------------------------------------------------------------ run
        int run() {
            collect();
            if (zones.isEmpty() && paths.isEmpty()) {
                return 0;
            }
            findSeeds();
            if (seeds.isEmpty()) {
                return 0;
            }
            resolve();
            if (removed.isEmpty() && orphans.isEmpty()) {
                return 0;
            }
            LongSet logs = new LongOpenHashSet(removed);
            leaves(logs);
            LongSet all = new LongOpenHashSet(removed);
            for (long pos : all) {
                BlockState s = state(pos);
                level.setBlock(cursor.set(pos), s.getFluidState().isSource() ? s.getFluidState().createLegacyBlock()
                        : Blocks.AIR.defaultBlockState(), FLAGS);
            }
            attachments(logs);
            freeze();
            return removed.size();
        }

        void collect() {
            for (StructureStart start : starts) {
                for (StructurePiece sp : start.getPieces()) {
                    BoundingBox b = sp.getBoundingBox();
                    if (!near(b, SCAN + MARGIN + 1)) {
                        continue;
                    }
                    if (!(sp instanceof PoolElementStructurePiece pe)) {
                        Piece p = new Piece(b, null, BlockPos.ZERO, Rotation.NONE, false);
                        pieces.add(p);
                        house(p);
                        continue;
                    }
                    StructurePoolElement element = pe.getElement();
                    if (element instanceof FeaturePoolElement) {
                        decor.add(pe.getPosition());
                        continue;
                    }
                    if (element instanceof EmptyPoolElement) {
                        continue;
                    }
                    TemplateInfo info = null;
                    if (element instanceof SinglePoolElement single) {
                        StructureTemplate template = single.template.map(templates::getOrCreate, Function.identity());
                        info = info(template);
                    }
                    boolean terrain = element.getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING && info != null;
                    Piece p = new Piece(b, info, pe.getPosition(), pe.getRotation(), terrain);
                    pieces.add(p);
                    if (terrain) {
                        path(p);
                    } else {
                        house(p);
                    }
                }
            }
        }

        /** A rigid piece: its box (strict), 2 blocks around and 4 above (soft), the doorway of each entrance (strict). */
        void house(Piece p) {
            BoundingBox b = p.box;
            zones.add(new Zone(b, true, b));
            zones.add(new Zone(new BoundingBox(b.minX() - MARGIN, b.minY() - 1, b.minZ() - MARGIN, b.maxX() + MARGIN,
                    b.maxY() + ABOVE, b.maxZ() + MARGIN), false, null));
            if (p.template == null) {
                return;
            }
            for (Entrance e : p.template.entrances()) {
                BlockPos at = p.world(e.pos());
                Direction front = p.rotation.rotate(e.front());
                Direction side = front.getClockWise();
                BlockPos a = at.relative(side, -1).below();
                BlockPos c = at.relative(front, 3).relative(side, 1).above(3);
                zones.add(new Zone(BoundingBox.fromCorners(a, c), true, null));
            }
        }

        /** A terrain-matching piece: every column it places blocks in, one more on each side, at any height. */
        void path(Piece p) {
            int lo = Math.max(minY, p.box.minY() - 16);
            int hi = Math.min(maxY, p.box.maxY() + 32);
            for (long column : p.template.columns()) {
                BlockPos w = p.world(new BlockPos(ChunkPos.getX(column), 0, ChunkPos.getZ(column)));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = w.getX() + dx;
                        int z = w.getZ() + dz;
                        if (!inWrite(x, minY, z, 16 - SCAN)) {
                            continue;
                        }
                        int[] range = paths.computeIfAbsent(ChunkPos.asLong(x, z), k -> new int[] {lo, hi});
                        range[0] = Math.min(range[0], lo);
                        range[1] = Math.max(range[1], hi);
                    }
                }
            }
        }

        void findSeeds() {
            int x0 = chunk.getMinBlockX() - SCAN;
            int x1 = chunk.getMaxBlockX() + SCAN;
            int z0 = chunk.getMinBlockZ() - SCAN;
            int z1 = chunk.getMaxBlockZ() + SCAN;
            for (Zone zone : zones) {
                BoundingBox b = zone.box();
                for (int x = Math.max(x0, b.minX()); x <= Math.min(x1, b.maxX()); x++) {
                    for (int z = Math.max(z0, b.minZ()); z <= Math.min(z1, b.maxZ()); z++) {
                        for (int y = Math.max(minY, b.minY()); y <= Math.min(maxY, b.maxY()); y++) {
                            seed(BlockPos.asLong(x, y, z), zone.strict(), zone.exempt(), false);
                        }
                    }
                }
            }
            for (Long2ObjectMap.Entry<int[]> e : paths.long2ObjectEntrySet()) {
                int x = ChunkPos.getX(e.getLongKey());
                int z = ChunkPos.getZ(e.getLongKey());
                boolean pending = !decorated(x >> 4, z >> 4);
                for (int y = e.getValue()[0]; y <= e.getValue()[1]; y++) {
                    seed(BlockPos.asLong(x, y, z), pending, null, pending);
                }
            }
        }

        void seed(long pos, boolean strict, @Nullable BoundingBox exempt, boolean plants) {
            Seed s = seeds.get(pos);
            if (s != null) {
                s.merge(strict, exempt);
                return;
            }
            BlockState state = state(pos);
            if (state.isAir()) {
                return;
            }
            Kind k = kind(state);
            if (k == Kind.NONE || (k == Kind.PLANT && !plants) || owned(BlockPos.of(pos), state.getBlock())) {
                return;
            }
            seeds.put(pos, new Seed(pos, strict, exempt));
        }

        // ------------------------------------------------------------------ trees
        void resolve() {
            for (Seed seed : seeds.values()) {
                switch (wild(seed.pos)) {
                    case LOG -> tree(seed.pos).seeds.add(seed);
                    case LEAF -> {
                        LongArrayList logs = supports(seed.pos);
                        if (logs.isEmpty()) {
                            orphans.add(seed.pos);
                        }
                        for (int i = 0; i < logs.size(); i++) {
                            tree(logs.getLong(i)).seeds.add(seed);
                        }
                    }
                    case CAP -> blob(seed.pos);
                    case COLUMN -> column(seed.pos);
                    case PLANT -> plant(seed.pos);
                    case VINE, BUSH -> removed.add(seed.pos);
                    default -> { }
                }
            }
            for (Tree t : trees) {
                if (remove(t)) {
                    removed.addAll(t.logs);
                }
            }
        }

        boolean remove(Tree t) {
            if (!t.complete || !t.leafy || t.seeds.isEmpty()) {
                return false;
            }
            if (t.decor == null) {
                return true;
            }
            for (Seed s : t.seeds) {
                if (s.strict && (s.exempt == null || !s.exempt.isInside(t.decor))) {
                    return true;
                }
            }
            return false;
        }

        /** The logs connected to {@code start} (26-neighbourhood): one tree, or a few grown together. */
        Tree tree(long start) {
            Tree known = treeOf.get(start);
            if (known != null) {
                return known;
            }
            Tree t = new Tree();
            trees.add(t);
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            treeOf.put(start, t);
            queue.enqueue(start);
            while (!queue.isEmpty()) {
                long pos = queue.dequeueLong();
                t.logs.add(pos);
                int x = BlockPos.getX(pos);
                int y = BlockPos.getY(pos);
                int z = BlockPos.getZ(pos);
                if (!inWrite(x, y, z, 16 - REACH) || t.logs.size() > MAX_LOGS) {
                    t.complete = false;
                }
                for (Direction d : Direction.values()) {
                    if (!t.leafy && inWrite(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ(), 0)
                            && wild(BlockPos.offset(pos, d)) == Kind.LEAF) {
                        t.leafy = true;
                    }
                }
                if (t.decor == null) {
                    for (BlockPos d : decor) {
                        if (d.getX() == x && d.getZ() == z && Math.abs(d.getY() - y) <= 2) {
                            t.decor = d;
                        }
                    }
                }
                if (!t.complete && t.logs.size() > MAX_LOGS) {
                    break;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if ((dx | dy | dz) == 0 || !inWrite(x + dx, y + dy, z + dz, 0)) {
                                continue;
                            }
                            long n = BlockPos.asLong(x + dx, y + dy, z + dz);
                            if (!treeOf.containsKey(n) && wild(n) == Kind.LOG) {
                                treeOf.put(n, t);
                                queue.enqueue(n);
                            }
                        }
                    }
                }
            }
            return t;
        }

        /** The wild logs that keep the leaf at {@code start} alive (within the vanilla decay distance). */
        LongArrayList supports(long start) {
            LongArrayList logs = new LongArrayList();
            LongOpenHashSet seen = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            Long2IntOpenHashMap depth = new Long2IntOpenHashMap();
            queue.enqueue(start);
            seen.add(start);
            depth.put(start, 0);
            while (!queue.isEmpty()) {
                long pos = queue.dequeueLong();
                int d = depth.get(pos);
                for (Direction dir : Direction.values()) {
                    long n = BlockPos.offset(pos, dir);
                    if (!seen.add(n) || !inWrite(BlockPos.getX(n), BlockPos.getY(n), BlockPos.getZ(n), 0)) {
                        continue;
                    }
                    Kind k = wild(n);
                    if (k == Kind.LOG) {
                        logs.add(n);
                    } else if (k == Kind.LEAF && d + 1 < LEAF) {
                        depth.put(n, d + 1);
                        queue.enqueue(n);
                    }
                }
            }
            return logs;
        }

        /** A huge mushroom: its stem and cap blocks (26-neighbourhood, within 6 blocks). */
        void blob(long start) {
            LongOpenHashSet found = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            found.add(start);
            queue.enqueue(start);
            int sx = BlockPos.getX(start);
            int sz = BlockPos.getZ(start);
            while (!queue.isEmpty() && found.size() <= MAX_BLOB) {
                long pos = queue.dequeueLong();
                int x = BlockPos.getX(pos);
                int y = BlockPos.getY(pos);
                int z = BlockPos.getZ(pos);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            long n = BlockPos.asLong(x + dx, y + dy, z + dz);
                            if (Math.abs(x + dx - sx) > 6 || Math.abs(z + dz - sz) > 6 || !inWrite(x + dx, y + dy, z + dz, 0)
                                    || found.contains(n) || wild(n) != Kind.CAP) {
                                continue;
                            }
                            found.add(n);
                            queue.enqueue(n);
                        }
                    }
                }
            }
            if (found.size() <= MAX_BLOB) {
                removed.addAll(found);
            }
        }

        /** A cactus or bamboo stalk, top to bottom. */
        void column(long start) {
            Block block = state(start).getBlock();
            for (Direction d : new Direction[] {Direction.UP, Direction.DOWN}) {
                long pos = start;
                for (int i = 0; i < 32 && inWrite(BlockPos.of(pos)); i++) {
                    Block b = state(pos).getBlock();
                    if (b != block && !(block == Blocks.BAMBOO && b == Blocks.BAMBOO_SAPLING)) {
                        break;
                    }
                    removed.add(pos);
                    pos = BlockPos.offset(pos, d);
                }
            }
        }

        /** A loose plant on a street still to be placed (both halves of a tall one). */
        void plant(long pos) {
            removed.add(pos);
            BlockState s = state(pos);
            if (s.getBlock() instanceof DoublePlantBlock && s.hasProperty(DoublePlantBlock.HALF)) {
                long other = BlockPos.offset(pos, s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
                if (inWrite(BlockPos.of(other)) && state(other).is(s.getBlock())) {
                    removed.add(other);
                }
            }
        }

        // ------------------------------------------------------------------ leaves
        /**
         * Removes the wild leaves the removed logs (and the orphan leaves in the zones) leave without a log within the
         * vanilla decay distance: exactly what would decay in game, done now so nothing floats. Leaves whose support
         * cannot be seen (past the decoration window) stay and get a block tick: the game recomputes their distance
         * and lets them decay if they really hang on nothing.
         */
        void leaves(LongSet logs) {
            LongOpenHashSet candidates = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            Long2IntOpenHashMap depth = new Long2IntOpenHashMap();
            for (long log : logs) {
                if (wild(log) != Kind.LOG) {
                    continue;
                }
                for (Direction d : Direction.values()) {
                    long n = BlockPos.offset(log, d);
                    if (inWrite(BlockPos.of(n)) && !candidates.contains(n) && wild(n) == Kind.LEAF) {
                        candidates.add(n);
                        depth.put(n, 1);
                        queue.enqueue(n);
                    }
                }
            }
            for (long o : orphans) {
                if (candidates.add(o)) {
                    depth.put(o, 1);
                    queue.enqueue(o);
                }
            }
            while (!queue.isEmpty()) {
                long pos = queue.dequeueLong();
                int d = depth.get(pos);
                if (d >= LEAF) {
                    continue;
                }
                for (Direction dir : Direction.values()) {
                    long n = BlockPos.offset(pos, dir);
                    if (!candidates.contains(n) && inWrite(BlockPos.of(n)) && wild(n) == Kind.LEAF) {
                        candidates.add(n);
                        depth.put(n, d + 1);
                        queue.enqueue(n);
                    }
                }
            }
            if (candidates.isEmpty()) {
                return;
            }
            // the leaves around the candidates up to the decay distance, the logs left there and the window's edge
            LongOpenHashSet area = new LongOpenHashSet(candidates);
            Long2IntOpenHashMap reach = new Long2IntOpenHashMap();
            for (long c : candidates) {
                reach.put(c, 0);
                queue.enqueue(c);
            }
            LongArrayList edge = new LongArrayList();
            Long2IntOpenHashMap dist = new Long2IntOpenHashMap();
            dist.defaultReturnValue(Integer.MAX_VALUE);
            LongArrayFIFOQueue support = new LongArrayFIFOQueue();
            while (!queue.isEmpty()) {
                long pos = queue.dequeueLong();
                int r = reach.get(pos);
                for (Direction dir : Direction.values()) {
                    long n = BlockPos.offset(pos, dir);
                    if (!inWrite(BlockPos.of(n))) {
                        edge.add(pos);
                        continue;
                    }
                    Kind k = removed.contains(n) ? Kind.NONE : wild(n);
                    if (k == Kind.LOG && dist.get(pos) > 1) {
                        dist.put(pos, 1);
                        support.enqueue(pos);
                    } else if (k == Kind.LEAF && r < LEAF && area.add(n)) {
                        reach.put(n, r + 1);
                        queue.enqueue(n);
                    }
                }
            }
            spread(support, dist, area);
            LongOpenHashSet doubtful = new LongOpenHashSet();
            if (!edge.isEmpty()) {
                Long2IntOpenHashMap edgeDist = new Long2IntOpenHashMap();
                edgeDist.defaultReturnValue(Integer.MAX_VALUE);
                LongArrayFIFOQueue fromEdge = new LongArrayFIFOQueue();
                for (int i = 0; i < edge.size(); i++) {
                    long e = edge.getLong(i);
                    if (dist.get(e) > LEAF && edgeDist.get(e) > 1) {
                        edgeDist.put(e, 1);
                        fromEdge.enqueue(e);
                    }
                }
                spread(fromEdge, edgeDist, area);
                for (long c : candidates) {
                    if (dist.get(c) > LEAF && edgeDist.get(c) <= LEAF) {
                        doubtful.add(c);
                    }
                }
            }
            for (long c : candidates) {
                if (dist.get(c) > LEAF && !doubtful.contains(c)) {
                    removed.add(c);
                } else if (doubtful.contains(c)) {
                    level.scheduleTick(BlockPos.of(c), state(c).getBlock(), 1);
                }
            }
        }

        void spread(LongArrayFIFOQueue queue, Long2IntMap dist, LongSet area) {
            while (!queue.isEmpty()) {
                long pos = queue.dequeueLong();
                int d = dist.get(pos);
                if (d >= LEAF) {
                    continue;
                }
                for (Direction dir : Direction.values()) {
                    long n = BlockPos.offset(pos, dir);
                    if (area.contains(n) && !removed.contains(n) && dist.get(n) > d + 1) {
                        dist.put(n, d + 1);
                        queue.enqueue(n);
                    }
                }
            }
        }

        // ------------------------------------------------------------------ what hung on the trees
        /** Vines, cocoa, lichen, moss and snow that lost their support fall with the tree; bee nests on its logs too. */
        void attachments(LongSet logs) {
            PriorityQueue<Long> queue = new PriorityQueue<>((a, b) -> Integer.compare(BlockPos.getY(b), BlockPos.getY(a)));
            LongOpenHashSet queued = new LongOpenHashSet();
            for (long r : new LongArrayList(removed)) {
                around(r, logs.contains(r), queue, queued);
            }
            int budget = 8192;
            while (!queue.isEmpty() && budget-- > 0) {
                long pos = queue.poll();
                BlockPos at = BlockPos.of(pos);
                BlockState s = level.getBlockState(at);
                BlockState now = s;
                for (Direction d : Direction.values()) {
                    long n = BlockPos.offset(pos, d);
                    if (removed.contains(n) && !now.isAir()) {
                        now = now.updateShape(d, level.getBlockState(BlockPos.of(n)), level, at, BlockPos.of(n));
                    }
                }
                if (now != s) {
                    level.setBlock(at, now, FLAGS);
                    if (now.isAir()) {
                        removed.add(pos);
                        around(pos, false, queue, queued);
                    }
                }
            }
        }

        void around(long r, boolean log, PriorityQueue<Long> queue, LongSet queued) {
            for (Direction d : Direction.values()) {
                long n = BlockPos.offset(r, d);
                BlockPos at = BlockPos.of(n);
                if (removed.contains(n) || !inWrite(at)) {
                    continue;
                }
                BlockState s = level.getBlockState(at);
                Block b = s.getBlock();
                if (log && b == Blocks.BEE_NEST) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
                    removed.add(n);
                } else if ((b instanceof VineBlock || b instanceof CocoaBlock || b instanceof MultifaceBlock
                        || b instanceof SnowLayerBlock || b instanceof CarpetBlock) && queued.add(n)) {
                    queue.add(n);
                }
            }
        }

        // ------------------------------------------------------------------ snow
        /** The ground under a removed canopy gets the snow and ice freeze_top_layer gives the open ground (chunks still
         *  to be decorated get theirs from their own pass). */
        void freeze() {
            Long2IntOpenHashMap top = new Long2IntOpenHashMap();
            top.defaultReturnValue(Integer.MIN_VALUE);
            for (long pos : removed) {
                long column = ChunkPos.asLong(BlockPos.getX(pos), BlockPos.getZ(pos));
                top.put(column, Math.max(top.get(column), BlockPos.getY(pos)));
            }
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (Long2IntMap.Entry e : top.long2IntEntrySet()) {
                int x = ChunkPos.getX(e.getLongKey());
                int z = ChunkPos.getZ(e.getLongKey());
                if (!decorated(x >> 4, z >> 4)) {
                    continue; // its own freeze pass comes after its streets (snow first would lift them a block)
                }
                boolean open = true;
                for (int y = e.getIntValue() + 1; y <= Math.min(maxY, e.getIntValue() + 24) && open; y++) {
                    open = level.getBlockState(p.set(x, y, z)).isAir();
                }
                if (!open) {
                    continue;
                }
                int y = e.getIntValue();
                while (y > minY) {
                    BlockState s = level.getBlockState(p.set(x, y, z));
                    if (s.blocksMotion() || !s.getFluidState().isEmpty()) {
                        break;
                    }
                    y--;
                }
                BlockPos ground = new BlockPos(x, y, z);
                BlockPos above = ground.above();
                if (!inWrite(ground) || !inWrite(above)) {
                    continue;
                }
                Biome biome = level.getBiome(above).value();
                if (biome.shouldFreeze(level, ground, false)) {
                    level.setBlock(ground, Blocks.ICE.defaultBlockState(), FLAGS);
                }
                if (biome.shouldSnow(level, above)) {
                    level.setBlock(above, Blocks.SNOW.defaultBlockState(), FLAGS);
                    BlockState below = level.getBlockState(ground);
                    if (below.hasProperty(BlockStateProperties.SNOWY)) {
                        level.setBlock(ground, below.setValue(BlockStateProperties.SNOWY, true), FLAGS);
                    }
                }
            }
        }
    }
}
