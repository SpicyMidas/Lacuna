package com.lacuna.mod.bag;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.util.Sfx;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side rules of the Comically Large Bag: one entity or one block goes in with every bit of its data and comes
 * back out as the very same thing.
 * <ul>
 *   <li>Entities are saved whole (id, UUID, passengers) and leave the world the way a dimension change takes them
 *   ({@link Entity.RemovalReason#CHANGED_DIMENSION}): no death, no drops, no spilled chest boat. On release they are
 *   loaded back with the same UUID, so it is the same creature, not a copy; a UUID already in the level is refused.</li>
 *   <li>Blocks keep their exact state and their block entity saved with full metadata. The block entity is detached
 *   before the block is cleared, so containers never spill and furnaces never pop their XP twice. A block comes back
 *   turned the same way relative to the player as when it was bagged.</li>
 *   <li>"Too much stuff": anything whose saved data tops {@link #MAX_BYTES} or holds more than {@link #MAX_STUFFED}
 *   stuffed containers (non-empty shulker boxes, bundles, full bags), however deeply nested, is refused.</li>
 * </ul>
 */
public final class BagCapture {
    public static final String FEATURE = "comically_large_bag";
    /** Serialized NBT budget: a full bag rides in every inventory packet, so stay far below network limits. */
    public static final int MAX_BYTES = 64 * 1024;
    /** Stuffed containers (non-empty shulker boxes, bundles, full bags) allowed inside whatever gets bagged. */
    public static final int MAX_STUFFED = 4;

    public static final TagKey<EntityType<?>> REFUSED_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, Lacuna.id("bag_refuses"));
    public static final TagKey<Block> REFUSED_BLOCKS = TagKey.create(Registries.BLOCK, Lacuna.id("bag_refuses"));

    private static final String KEY_STATE = "state";
    private static final String KEY_BLOCK_ENTITY = "block_entity";
    private static final String KEY_FACING = "facing";
    private static final String COMPONENT_ID = Lacuna.MODID + ":bag_contents";

    private BagCapture() {}

    @Nullable
    public static BagContents contents(ItemStack stack) {
        return stack.get(ModComponents.BAG_CONTENTS.get());
    }

    public static boolean isFull(ItemStack stack) {
        return stack.has(ModComponents.BAG_CONTENTS.get());
    }

    // ================================================================================================ entities

    /** Right click with an empty bag on a creature, cart or boat. */
    public static InteractionResult captureEntity(ServerPlayer player, ItemStack bag, Entity target) {
        if (isFull(bag) || target.level() != player.level()) {
            return InteractionResult.PASS;
        }
        String refusal = !player.getAbilities().mayBuild ? "lacuna.bag.protected" : entityRefusal(player, target);
        for (Entity rider : target.getIndirectPassengers()) {
            if (refusal == null) {
                refusal = rider instanceof Player ? "lacuna.bag.ridden" : entityRefusal(player, rider);
            }
        }
        if (refusal != null) {
            return refuse(player, refusal);
        }
        CompoundTag tag = new CompoundTag();
        if (!target.saveAsPassenger(tag)) {
            return refuse(player, "lacuna.bag.slippery");
        }
        // a lead held by the bagger comes along (it is re-tied on release); any other lead stays behind as an item
        boolean keepLeash = target instanceof Leashable leashable && leashable.isLeashed() && leashable.getLeashHolder() == player;
        if (!keepLeash) {
            tag.remove(Leashable.LEASH_TAG);
        }
        // a sleeper wakes up on the way in: the bed it lay in stays behind
        tag.remove("SleepingX");
        tag.remove("SleepingY");
        tag.remove("SleepingZ");
        if (tooMuch(tag)) {
            return tooMuch(player);
        }
        Component name = target.getDisplayName();
        Vec3 at = target.position();
        float width = target.getBbWidth();
        float height = target.getBbHeight();
        // commit: the bag holds the save first, then the entity (and whoever rides it) leaves the world
        bag.set(ModComponents.BAG_CONTENTS.get(), new BagContents(BagContents.ENTITY, tag));
        List<Entity> riders = new ArrayList<>();
        target.getIndirectPassengers().forEach(riders::add);
        target.stopRiding();
        for (int i = riders.size() - 1; i >= 0; i--) {
            lift(riders.get(i), player);
        }
        lift(target, player);
        ServerLevel level = player.serverLevel();
        level.gameEvent(player, GameEvent.ENTITY_INTERACT, at);
        thwump(level, at, width, height, true);
        player.displayClientMessage(Component.translatable("lacuna.bag.bagged", name).withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    /** Takes a saved entity out of the world without killing it, like a dimension change does. */
    private static void lift(Entity entity, ServerPlayer player) {
        if (entity instanceof LivingEntity living && living.isSleeping()) {
            living.stopSleeping();
        }
        if (entity instanceof Leashable leashable && leashable.isLeashed()) {
            leashable.dropLeash(true, leashable.getLeashHolder() != player);
        }
        entity.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        // like Mob#removeAfterChangingDimensions: the stale object keeps no usable copy of anything it carried
        if (entity instanceof Mob mob) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.getItemBySlot(slot).setCount(0);
            }
        }
        if (entity instanceof ContainerEntity container) {
            container.clearContent();
        }
        if (entity instanceof InventoryCarrier carrier) {
            carrier.getInventory().clearContent();
        }
    }

    @Nullable
    private static String entityRefusal(ServerPlayer player, Entity entity) {
        if (entity instanceof Player) {
            return "lacuna.bag.no_players";
        }
        if (!entity.isAlive()) {
            return "lacuna.bag.nothing";
        }
        if (entity instanceof PartEntity<?> || entity.isMultipartEntity() || entity.getType().is(Tags.EntityTypes.BOSSES)
                || entity.getType().is(REFUSED_ENTITIES)) {
            return "lacuna.bag.too_big";
        }
        if (!(entity instanceof LivingEntity || entity instanceof VehicleEntity) || !entity.getType().canSerialize()) {
            return "lacuna.bag.wont_fit";
        }
        if (entity.onlyOpCanSetNbt() && !player.canUseGameMasterBlocks()) {
            return "lacuna.bag.op_only";
        }
        if (entity instanceof OwnableEntity ownable && !player.isCreative()) {
            UUID owner = ownable.getOwnerUUID();
            if (owner != null && !owner.equals(player.getUUID())) {
                return "lacuna.bag.not_yours";
            }
        }
        return null;
    }

    private static InteractionResult releaseEntity(ServerPlayer player, ItemStack bag, CompoundTag saved, UseOnContext context) {
        ServerLevel level = player.serverLevel();
        Direction face = context.getClickedFace();
        BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        if (!mayWork(player, level, pos, face, bag)) {
            return refuse(player, "lacuna.bag.protected");
        }
        float yaw = Mth.wrapDegrees(player.getYRot() + 180.0F); // it pops out facing whoever let it out
        Entity entity = EntityType.loadEntityRecursive(saved.copy(), level, e -> {
            e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
            return e;
        });
        if (entity == null) {
            return refuse(player, "lacuna.bag.lost");
        }
        List<Entity> all = entity.getSelfAndPassengers().toList();
        for (Entity e : all) {
            String refusal = e instanceof Player ? "lacuna.bag.no_players" : entityRefusal(player, e);
            if (refusal != null) {
                return refuse(player, refusal);
            }
            if (level.getEntity(e.getUUID()) != null) {
                return refuse(player, "lacuna.bag.already_out");
            }
        }
        Vec3 spot = findSpot(level, entity, pos, face);
        if (spot == null) {
            return refuse(player, "lacuna.bag.no_room");
        }
        for (Entity e : all) {
            e.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
            e.setDeltaMovement(Vec3.ZERO);
            e.resetFallDistance();
            if (e instanceof LivingEntity living) {
                living.setYHeadRot(yaw);
                living.setYBodyRot(yaw);
            }
            if (e instanceof Mob mob) {
                mob.setPersistenceRequired(); // carried this far, it should not despawn on the spot
            }
        }
        entity.setDeltaMovement(0.0, 0.2, 0.0); // a little hop out of the bag
        if (!level.addFreshEntity(entity)) {
            return refuse(player, "lacuna.bag.no_room");
        }
        for (Entity rider : all) {
            if (rider != entity) {
                level.addFreshEntity(rider);
            }
        }
        bag.remove(ModComponents.BAG_CONTENTS.get());
        level.gameEvent(player, GameEvent.ENTITY_PLACE, spot);
        thwump(level, spot, entity.getBbWidth(), entity.getBbHeight(), false);
        return InteractionResult.SUCCESS;
    }

    /** A collision-free spot on the clicked face: big creatures step away from walls and hang down from ceilings. */
    @Nullable
    private static Vec3 findSpot(ServerLevel level, Entity entity, BlockPos pos, Direction face) {
        double half = entity.getBbWidth() / 2.0;
        Vec3 base = Vec3.atBottomCenterOf(pos);
        if (face.getAxis().isHorizontal()) {
            double push = Math.max(0.0, half - 0.5 + 0.01);
            base = base.add(face.getStepX() * push, 0.0, face.getStepZ() * push);
        }
        double[] lifts = face == Direction.DOWN ? new double[] {0.0, -0.5, -1.0, -1.5, -2.0, -2.5}
                : new double[] {0.0, 0.5, 1.0, -0.5, -1.0};
        for (double dy : lifts) {
            Vec3 at = base.add(0.0, dy, 0.0);
            AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(at);
            if (level.getWorldBorder().isWithinBounds(box) && level.noCollision(entity, box)) {
                return at;
            }
        }
        return null;
    }

    // ================================================================================================ blocks

    /** Sneak + right click with an empty bag on a block. */
    public static InteractionResult captureBlock(ServerPlayer player, ItemStack bag, BlockPos pos, Direction face) {
        if (isFull(bag)) {
            return InteractionResult.PASS;
        }
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        String refusal = !mayWork(player, level, pos, face, bag) ? "lacuna.bag.protected" : blockRefusal(player, level, pos, state, blockEntity);
        if (refusal != null) {
            return refuse(player, refusal);
        }
        if (CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()) {
            return refuse(player, "lacuna.bag.protected");
        }
        BlockState stored = state;
        if (stored.hasProperty(BlockStateProperties.CHEST_TYPE)) {
            stored = stored.setValue(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE); // half a double chest leaves as a chest
        }
        if (stored.hasProperty(BlockStateProperties.WATERLOGGED)) {
            stored = stored.setValue(BlockStateProperties.WATERLOGGED, false); // the water stays where it was
        }
        CompoundTag data = new CompoundTag();
        data.put(KEY_STATE, NbtUtils.writeBlockState(stored));
        data.putString(KEY_FACING, player.getDirection().getSerializedName());
        if (blockEntity != null) {
            data.put(KEY_BLOCK_ENTITY, blockEntity.saveWithFullMetadata(level.registryAccess()));
        }
        if (tooMuch(data)) {
            return tooMuch(player);
        }
        Component name = state.getBlock().getName();
        // commit: the bag holds the data first; the block entity is detached before the block goes, so nothing spills
        bag.set(ModComponents.BAG_CONTENTS.get(), new BagContents(BagContents.BLOCK, data));
        if (blockEntity != null) {
            level.removeBlockEntity(pos);
            if (blockEntity instanceof BaseContainerBlockEntity container) {
                container.clearContent();
            }
        }
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        thwump(level, Vec3.atBottomCenterOf(pos), 1.0F, 1.0F, true);
        player.displayClientMessage(Component.translatable("lacuna.bag.bagged", name).withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static String blockRefusal(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return "lacuna.bag.nothing";
        }
        if (state.getDestroySpeed(level, pos) < 0.0F || state.is(REFUSED_BLOCKS)) {
            return "lacuna.bag.wont_budge";
        }
        if (isMultiBlock(state)) {
            return "lacuna.bag.too_tangled";
        }
        if (blockEntity != null && opOnly(blockEntity) && !player.canUseGameMasterBlocks()) {
            return "lacuna.bag.op_only";
        }
        return null;
    }

    /** Doors, beds, tall plants and extended pistons are more than one block; double chests are split instead. */
    private static boolean isMultiBlock(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)
                || state.getBlock() instanceof PistonHeadBlock || state.getBlock() instanceof MovingPistonBlock
                || state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED);
    }

    /** Spawners, trial spawners and command blocks: their data is operator-only, as for vanilla block items. */
    private static boolean opOnly(BlockEntity blockEntity) {
        return blockEntity.onlyOpCanSetNbt() && !(blockEntity instanceof LecternBlockEntity);
    }

    private static InteractionResult releaseBlock(ServerPlayer player, ItemStack bag, CompoundTag data, UseOnContext context) {
        ServerLevel level = player.serverLevel();
        BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), data.getCompound(KEY_STATE));
        if (state.isAir()) {
            return refuse(player, "lacuna.bag.lost");
        }
        BlockPlaceContext place = new BlockPlaceContext(context);
        BlockPos pos = place.getClickedPos();
        Direction face = context.getClickedFace();
        if (!mayWork(player, level, pos, face, bag)) {
            return refuse(player, "lacuna.bag.protected");
        }
        if (!place.canPlace()) {
            return refuse(player, "lacuna.bag.no_room");
        }
        Direction bagged = Direction.byName(data.getString(KEY_FACING));
        if (bagged != null && bagged.getAxis().isHorizontal()) {
            state = state.rotate(level, pos, rotation(bagged, player.getDirection()));
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        }
        state = Block.updateFromNeighbourShapes(state, level, pos);
        if (!state.canSurvive(level, pos)) {
            return refuse(player, "lacuna.bag.wont_stay");
        }
        if (!level.isUnobstructed(state, pos, CollisionContext.of(player))) {
            return refuse(player, "lacuna.bag.no_room");
        }
        CompoundTag beTag = data.contains(KEY_BLOCK_ENTITY, Tag.TAG_COMPOUND) ? data.getCompound(KEY_BLOCK_ENTITY) : null;
        if (beTag != null && state.getBlock() instanceof EntityBlock entityBlock) {
            BlockEntity probe = entityBlock.newBlockEntity(pos, state);
            if (probe != null && opOnly(probe) && !player.canUseGameMasterBlocks()) {
                return refuse(player, "lacuna.bag.op_only");
            }
        }
        BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return refuse(player, "lacuna.bag.no_room");
        }
        if (EventHooks.onBlockPlace(player, before, face)) {
            before.restore(Block.UPDATE_ALL);
            return refuse(player, "lacuna.bag.protected");
        }
        if (beTag != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            String savedId = beTag.getString("id");
            if (blockEntity != null && (savedId.isEmpty() || savedId.equals(String.valueOf(BlockEntityType.getKey(blockEntity.getType()))))) {
                blockEntity.loadWithComponents(beTag, level.registryAccess());
                blockEntity.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            } else {
                Lacuna.LOGGER.warn("[Lacuna] bag: block entity {} does not fit {} at {}; its data was kept out", savedId, state, pos);
            }
        }
        bag.remove(ModComponents.BAG_CONTENTS.get());
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
        level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
        thwump(level, Vec3.atBottomCenterOf(pos), 1.0F, 1.0F, false);
        return InteractionResult.SUCCESS;
    }

    /** Clockwise quarter turns that carry the player's facing at bagging time onto their facing now. */
    static Rotation rotation(Direction from, Direction to) {
        return switch (Math.floorMod(to.get2DDataValue() - from.get2DDataValue(), 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    // ================================================================================================ release

    /** Right click with a full bag on a block face: whatever is inside comes out there. */
    public static InteractionResult release(ServerPlayer player, ItemStack bag, UseOnContext context) {
        BagContents contents = contents(bag);
        if (contents == null) {
            return InteractionResult.PASS;
        }
        if (!player.getAbilities().mayBuild) {
            return refuse(player, "lacuna.bag.protected");
        }
        if (contents.isEntity()) {
            return releaseEntity(player, bag, contents.data(), context);
        }
        if (contents.isBlock()) {
            return releaseBlock(player, bag, contents.data(), context);
        }
        return refuse(player, "lacuna.bag.lost");
    }

    private static boolean mayWork(ServerPlayer player, ServerLevel level, BlockPos pos, Direction face, ItemStack bag) {
        return player.getAbilities().mayBuild && level.mayInteract(player, pos) && player.mayUseItemAt(pos, face, bag)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    // ================================================================================================ too much stuff

    static boolean tooMuch(CompoundTag tag) {
        return byteSize(tag) > MAX_BYTES || stuffed(tag) > MAX_STUFFED;
    }

    /** Exact serialized size of the tag, in bytes. */
    static int byteSize(CompoundTag tag) {
        try (DataOutputStream out = new DataOutputStream(OutputStream.nullOutputStream())) {
            NbtIo.write(tag, out);
            return out.size();
        } catch (IOException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Counts saved item stacks that carry stuff of their own (a non-empty container or bundle, or a full bag),
     * anywhere in the tree: chest slots, a donkey's packs, a villager's pockets, a mob's hands, bundles in bundles.
     */
    static int stuffed(CompoundTag root) {
        int n = 0;
        for (String key : root.getAllKeys()) {
            n += stuffedIn(root.get(key));
        }
        return n;
    }

    private static int stuffedIn(@Nullable Tag tag) {
        int n = 0;
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING) && compound.contains("components", Tag.TAG_COMPOUND)) {
                CompoundTag components = compound.getCompound("components");
                if (nonEmptyList(components, "minecraft:container") || nonEmptyList(components, "minecraft:bundle_contents")
                        || components.contains(COMPONENT_ID)) {
                    n++;
                }
            }
            for (String key : compound.getAllKeys()) {
                n += stuffedIn(compound.get(key));
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) {
                n += stuffedIn(element);
            }
        }
        return n;
    }

    private static boolean nonEmptyList(CompoundTag components, String key) {
        return components.get(key) instanceof ListTag list && !list.isEmpty();
    }

    private static InteractionResult tooMuch(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("lacuna.bag.too_much").withStyle(ChatFormatting.RED), true);
        Sfx.at(player, SoundEvents.WOOL_BREAK, 1.0F, 0.5F);
        Sfx.at(player, SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F, 0.6F);
        Sfx.at(player, SoundEvents.PUFFER_FISH_BLOW_OUT, 0.6F, 0.6F);
        player.serverLevel().sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(),
                6, 0.3, 0.2, 0.3, 0.02);
        return InteractionResult.FAIL;
    }

    private static InteractionResult refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW), true);
        Sfx.at(player, SoundEvents.BUNDLE_REMOVE_ONE, 0.7F, 0.5F);
        return InteractionResult.FAIL;
    }

    // ================================================================================================ fx

    /** The comedic "thwump": a heavy sack sound, a puff of air, leather scraps and a cloud of poof. */
    private static void thwump(ServerLevel level, Vec3 at, float width, float height, boolean in) {
        double cy = at.y + height * 0.5;
        int puffs = 14 + Mth.clamp((int) (width * height * 8.0F), 0, 30);
        level.sendParticles(ParticleTypes.POOF, at.x, cy, at.z, puffs, width * 0.45, height * 0.4, width * 0.45, 0.04);
        level.sendParticles(ParticleTypes.CLOUD, at.x, cy, at.z, 4, width * 0.3, height * 0.3, width * 0.3, 0.02);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.LEATHER)), at.x, cy, at.z,
                6, width * 0.3, height * 0.3, width * 0.3, 0.12);
        Sfx.at(level, at, SoundEvents.WOOL_PLACE, 1.0F, in ? 0.5F : 0.6F);
        Sfx.at(level, at, SoundEvents.BUNDLE_INSERT, 1.0F, in ? 0.5F : 0.7F);
        if (in) {
            Sfx.at(level, at, SoundEvents.PUFFER_FISH_BLOW_OUT, 0.8F, 1.5F);
        } else {
            Sfx.at(level, at, SoundEvents.BUNDLE_DROP_CONTENTS, 1.0F, 0.6F);
            Sfx.at(level, at, SoundEvents.PUFFER_FISH_BLOW_UP, 0.8F, 1.3F);
        }
    }

    // ================================================================================================ dropped bags

    /**
     * A full bag on the ground never despawns and ignores every kind of damage (lava, fire, cactus, explosions: see
     * {@link ComicallyLargeBagItem#canBeHurtBy}); fallen into the void it bounces back to whoever threw it, or to the
     * world spawn, so its contents are never lost.
     */
    public static void guardDropped(ItemStack stack, ItemEntity item) {
        if (!isFull(stack) || !(item.level() instanceof ServerLevel level)) {
            return;
        }
        if (item.getAge() != -32768) {
            item.setUnlimitedLifetime();
        }
        if (item.getY() >= level.getMinBuildHeight() - 16) {
            return;
        }
        Entity owner = item.getOwner();
        Vec3 to;
        if (owner != null && owner.isAlive() && owner.level() == level) {
            to = owner.position().add(0.0, 0.5, 0.0);
        } else {
            BlockPos spawn = level.dimension() == Level.END ? ServerLevel.END_SPAWN_POINT : level.getSharedSpawnPos();
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, spawn);
            if (top.getY() <= level.getMinBuildHeight()) {
                to = Vec3.atCenterOf(spawn);
                item.setNoGravity(true); // nothing to land on there: it hovers until picked up
            } else {
                to = Vec3.atBottomCenterOf(top).add(0.0, 0.25, 0.0);
            }
        }
        item.teleportTo(to.x, to.y, to.z);
        item.setDeltaMovement(Vec3.ZERO);
        item.resetFallDistance();
        level.sendParticles(ParticleTypes.POOF, to.x, to.y + 0.3, to.z, 12, 0.3, 0.3, 0.3, 0.03);
        Sfx.at(level, to, SoundEvents.BUNDLE_DROP_CONTENTS, 1.0F, 0.6F);
    }

    // ================================================================================================ description

    /** "Cow 'Bessie'", "Chest (27 items)", "Boat (+1 riding)": what the tooltip says is inside. */
    public static Component describe(BagContents contents, @Nullable HolderLookup.Provider registries) {
        CompoundTag data = contents.data();
        if (contents.isEntity()) {
            MutableComponent what = EntityType.by(data).map(t -> t.getDescription().copy())
                    .orElseGet(() -> Component.literal(data.getString("id")));
            what = named(what, data, registries);
            int riders = riders(data);
            return riders > 0 ? Component.translatable("lacuna.bag.riders", what, riders) : what;
        }
        if (contents.isBlock()) {
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), data.getCompound(KEY_STATE));
            MutableComponent what = state.getBlock().getName().copy();
            CompoundTag be = data.getCompound(KEY_BLOCK_ENTITY);
            what = named(what, be, registries);
            if (be.contains("Items", Tag.TAG_LIST)) {
                int count = 0;
                for (Tag item : be.getList("Items", Tag.TAG_COMPOUND)) {
                    CompoundTag stack = (CompoundTag) item;
                    count += stack.contains("count", Tag.TAG_ANY_NUMERIC) ? stack.getInt("count") : 1;
                }
                return Component.translatable("lacuna.bag.items", what, count);
            }
            return what;
        }
        return Component.literal("?");
    }

    private static MutableComponent named(MutableComponent what, CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        if (registries == null || !tag.contains("CustomName", Tag.TAG_STRING)) {
            return what;
        }
        try {
            MutableComponent name = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
            return name == null ? what : Component.translatable("lacuna.bag.named", what, name);
        } catch (RuntimeException e) {
            return what;
        }
    }

    private static int riders(CompoundTag tag) {
        int n = 0;
        for (Tag rider : tag.getList("Passengers", Tag.TAG_COMPOUND)) {
            n += 1 + riders((CompoundTag) rider);
        }
        return n;
    }
}
