package com.lacuna.mod.pearl;

import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import com.lacuna.mod.util.Teleporter;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Fractured Pearl link logic shared by the shards, the thrown shard and the anchor block (spec M5). */
public final class PearlLinks {
    public static final String FEATURE = "fractured_pearl";

    private PearlLinks() {}

    @Nullable
    public static PearlLink link(ItemStack stack) {
        return stack.get(ModComponents.PEARL_LINK.get());
    }

    /** Creates a matched pair of shards sharing a fresh random link id. */
    public static ItemStack[] split() {
        PearlLink link = new PearlLink(UUID.randomUUID(), false);
        ItemStack arrival = new ItemStack(ModItems.ARRIVAL_SHARD.get());
        ItemStack departure = new ItemStack(ModItems.DEPARTURE_SHARD.get());
        arrival.set(ModComponents.PEARL_LINK.get(), link);
        departure.set(ModComponents.PEARL_LINK.get(), link);
        return new ItemStack[] {arrival, departure};
    }

    /**
     * Locks a thrown arrival shard against the hit face: places the anchor in the adjacent air block, records the
     * link (moving any previous anchor of the same link). Returns false if the shard must drop back as an item.
     */
    public static boolean lock(ServerLevel level, BlockPos hitPos, Direction face, ItemStack shard, @Nullable Player thrower) {
        PearlLink link = link(shard);
        if (link == null) {
            return false;
        }
        BlockPos at = hitPos.relative(face);
        if (!level.hasChunkAt(at) || !level.getWorldBorder().isWithinBounds(at)
                || at.getY() < level.getMinBuildHeight() || at.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockState current = level.getBlockState(at);
        if (!current.canBeReplaced() || !current.getFluidState().isEmpty() && !current.isAir()) {
            return false;
        }
        if (thrower != null && !level.mayInteract(thrower, at)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        PearlLinkSavedData data = PearlLinkSavedData.get(server);
        data.get(link.id()).ifPresent(old -> removeAnchorBlock(server, old, link.id()));
        level.setBlock(at, ModBlocks.ARRIVAL_ANCHOR.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof ArrivalAnchorBlockEntity anchor) {
            anchor.setShard(shard);
        }
        data.put(link.id(), new PearlLinkSavedData.Anchor(level.dimension(), at));
        level.sendParticles(ParticleTypes.PORTAL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.3);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        return true;
    }

    /** Removes the previous anchor of a link (loading its chunk once) without dropping the old shard twice. */
    private static void removeAnchorBlock(MinecraftServer server, PearlLinkSavedData.Anchor old, UUID linkId) {
        ServerLevel level = server.getLevel(old.dimension());
        if (level == null) {
            return;
        }
        SafeSpot.loadChunk(level, old.pos());
        if (level.getBlockEntity(old.pos()) instanceof ArrivalAnchorBlockEntity anchor
                && anchor.linkId().map(linkId::equals).orElse(false)) {
            level.removeBlock(old.pos(), false);
        }
    }

    public static void onAnchorBrokenByPlayer(ServerLevel level, BlockPos pos, ArrivalAnchorBlockEntity anchor, Player player) {
        ItemStack shard = anchor.shard();
        anchor.linkId().ifPresent(id -> {
            PearlLinkSavedData data = PearlLinkSavedData.get(level.getServer());
            data.get(id).filter(a -> a.dimension() == level.dimension() && a.pos().equals(pos)).ifPresent(a -> data.remove(id));
        });
        if (!shard.isEmpty() && !player.isCreative()) {
            Block.popResource(level, pos, shard.copy());
        }
    }

    /** Result of resolving a departure shard's link. */
    public record Target(ServerLevel level, BlockPos pos) {}

    /** Validates the link: record exists, anchor block present with the same link, same dimension, no FL crossing. */
    public static Optional<Target> resolve(MinecraftServer server, ResourceKey<Level> from, PearlLink link) {
        PearlLinkSavedData data = PearlLinkSavedData.get(server);
        Optional<PearlLinkSavedData.Anchor> anchor = data.get(link.id());
        if (anchor.isEmpty()) {
            return Optional.empty();
        }
        PearlLinkSavedData.Anchor a = anchor.get();
        if (a.dimension() != from || from == ModDimensions.FORGOTTEN_LAND) {
            return Optional.empty(); // never across dimensions, never into/out of the Forgotten Land
        }
        ServerLevel level = server.getLevel(a.dimension());
        if (level == null) {
            return Optional.empty();
        }
        SafeSpot.loadChunk(level, a.pos());
        if (!(level.getBlockEntity(a.pos()) instanceof ArrivalAnchorBlockEntity be)
                || !be.linkId().map(link.id()::equals).orElse(false)) {
            data.remove(link.id()); // anchor destroyed some other way: clean the record
            return Optional.empty();
        }
        return Optional.of(new Target(level, a.pos()));
    }

    public static boolean travel(ServerPlayer player, Target target) {
        return SafeRun.call(FEATURE, () -> {
            ServerLevel level = target.level();
            BlockPos dest = target.pos();
            BlockPos safe = SafeSpot.isSafe(level, dest) ? dest : SafeSpot.findNear(level, dest).orElse(dest.above());
            Vec3 from = player.position();
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 40, 0.4, 0.8, 0.4, 0.5);
            player.level().playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
            Teleporter.teleport(player, level, Vec3.atBottomCenterOf(safe), player.getYRot(), player.getXRot());
            level.sendParticles(ParticleTypes.PORTAL, safe.getX() + 0.5, safe.getY() + 1, safe.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.5);
            level.playSound(null, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.2F, 1.6F);
            return true;
        }, false);
    }

    public static void dropBack(ServerLevel level, Vec3 at, ItemStack shard) {
        if (!shard.isEmpty()) {
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, shard.copy());
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }
}
