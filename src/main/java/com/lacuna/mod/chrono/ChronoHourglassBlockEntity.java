package com.lacuna.mod.chrono;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * State of a Chrono Hourglass (lower half only). {@code remaining} ticks of sand are bought with Chronal Shards; while
 * they run (and no redstone signal holds the hourglass) {@link TimeAccelerator} speeds up the world around it.
 * <p>
 * The client keeps its own copy: it is sent on every change (fed, paused, run out) and every few seconds, and the client
 * ticker counts it down in between, so the sand drawn by the renderer flows smoothly without a packet per tick.
 */
public class ChronoHourglassBlockEntity extends BlockEntity {
    public static final String FEATURE = "chrono_hourglass";
    private static final String TAG_REMAINING = "remaining";
    private static final String TAG_RUN = "run_total";
    private static final String TAG_PAUSED = "paused";
    private static final int RESYNC_INTERVAL = 200;

    private int remaining;
    /** Sand of the current run (the top bulb is full at this value): the renderer draws remaining / runTotal. */
    private int runTotal;
    private boolean paused;
    private int lastSignal;
    private boolean noRefund;
    final TimeAccelerator accelerator = new TimeAccelerator();

    // client-side animation state: fraction of the sand in the top bulb, eased towards remaining / runTotal
    private float sandTop;
    private float prevSandTop;
    private boolean clientSynced;
    private int clientAge;

    public ChronoHourglassBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHRONO_HOURGLASS.get(), pos, state);
    }

    public boolean running() {
        return remaining > 0 && !paused;
    }

    public boolean paused() {
        return paused;
    }

    public int remaining() {
        return remaining;
    }

    /** Fraction of the sand shown in the top bulb, interpolated for rendering. */
    public float sandTop(float partialTick) {
        return Mth.lerp(partialTick, prevSandTop, sandTop);
    }

    public int clientAge() {
        return clientAge;
    }

    private float targetTop() {
        return remaining <= 0 || runTotal <= 0 ? 0.0F : Mth.clamp((float) remaining / runTotal, 0.0F, 1.0F);
    }

    public AABB renderBounds() {
        return new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 2, worldPosition.getZ() + 1);
    }

    public int comparatorSignal() {
        return remaining <= 0 ? 0 : Math.max(1, Mth.ceil(15.0F * remaining / Balance.HOURGLASS_MAX_TICKS));
    }

    // ------------------------------------------------------------------ feeding

    /** One Chronal Shard (sneaking: as many as fit) adds sand; an idle hourglass flips and starts. */
    boolean feed(ServerLevel level, Player player, ItemStack stack) {
        int room = (Balance.HOURGLASS_MAX_TICKS - remaining) / Balance.HOURGLASS_TICKS_PER_SHARD;
        if (room <= 0) {
            player.displayClientMessage(Component.translatable("lacuna.hourglass.full").withStyle(ChatFormatting.GOLD), true);
            sound(level, SoundEvents.SAND_HIT, 0.6F, 1.4F);
            return false;
        }
        int shards = player.isShiftKeyDown() ? Math.min(room, stack.getCount()) : 1;
        if (player.getAbilities().instabuild) {
            shards = player.isShiftKeyDown() ? room : 1;
        } else {
            stack.shrink(shards);
        }
        boolean wasIdle = remaining <= 0;
        remaining += shards * Balance.HOURGLASS_TICKS_PER_SHARD;
        runTotal = remaining; // the top bulb fills up again: the hourglass "flips"
        accelerator.invalidate();
        changed(level);
        sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, wasIdle ? 0.9F : 1.3F);
        sound(level, SoundEvents.SAND_PLACE, 0.5F, 1.4F);
        sound(level, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.35F, wasIdle ? 1.0F : 1.5F);
        level.sendParticles(ModParticles.CHRONO_TICK.get(), worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5, 10, 0.3, 0.6, 0.3, 0.01);
        player.displayClientMessage(status(), true);
        return true;
    }

    Component status() {
        if (remaining <= 0) {
            return Component.translatable("lacuna.hourglass.empty").withStyle(ChatFormatting.GRAY);
        }
        int seconds = Mth.ceil(remaining / 20.0F);
        Component time = Component.literal(String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.AQUA);
        return Component.translatable(paused ? "lacuna.hourglass.paused" : "lacuna.hourglass.running", time)
                .withStyle(paused ? ChatFormatting.GRAY : ChatFormatting.GOLD);
    }

    static void suppressRefund(Level level, BlockPos base) {
        if (level.getBlockEntity(base) instanceof ChronoHourglassBlockEntity hourglass) {
            hourglass.noRefund = true;
        }
    }

    /** Broken: whole shards' worth of unspent sand drop back as Chronal Shards. */
    void refund(ServerLevel level, BlockPos pos) {
        int shards = noRefund ? 0 : remaining / Balance.HOURGLASS_TICKS_PER_SHARD;
        remaining = 0;
        if (shards > 0) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(ModItems.CHRONAL_SHARD.get(), shards));
        }
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChronoHourglassBlockEntity hourglass) {
        if (level instanceof ServerLevel server) {
            SafeRun.run(FEATURE, () -> hourglass.tickServer(server, pos));
        }
    }

    private void tickServer(ServerLevel level, BlockPos pos) {
        if (remaining <= 0) {
            return;
        }
        long now = level.getGameTime();
        if (Math.floorMod(now + pos.asLong(), 10L) == 0) {
            boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
            if (powered != paused) {
                paused = powered;
                sound(level, SoundEvents.SAND_STEP, 0.5F, paused ? 0.7F : 1.3F);
                changed(level);
            }
        }
        if (paused) {
            return;
        }
        remaining--;
        accelerator.tick(level, pos);
        if (remaining <= 0) {
            remaining = 0;
            runTotal = 0;
            accelerator.invalidate();
            changed(level);
            sound(level, SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, 0.8F);
            sound(level, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.7F);
            level.sendParticles(ModParticles.CHRONO_TICK.get(), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    6, 0.25, 0.5, 0.25, 0.0);
            return;
        }
        if (comparatorSignal() != lastSignal) {
            lastSignal = comparatorSignal();
            setChanged();
            level.updateNeighbourForOutputSignal(pos.above(), getBlockState().getBlock());
        } else if (remaining % 20 == 0) {
            setChanged();
        }
        if (Math.floorMod(now + pos.asLong(), (long) RESYNC_INTERVAL) == 0) {
            sync(level);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ChronoHourglassBlockEntity hourglass) {
        hourglass.tickClient(level, pos);
    }

    private void tickClient(Level level, BlockPos pos) {
        clientAge++;
        if (remaining > 0 && !paused) {
            remaining--; // predicted; the server corrects it on every sync
        }
        prevSandTop = sandTop;
        float target = targetTop();
        if (!clientSynced) {
            sandTop = prevSandTop = target;
            clientSynced = true;
        } else {
            // follow the sand exactly while it runs, but pour it visibly when the hourglass is refilled or runs out
            float diff = target - sandTop;
            sandTop = Math.abs(diff) <= 0.06F ? target : sandTop + Math.copySign(0.06F, diff);
        }
        if (remaining > 0 && !paused) {
            ambience(level, pos);
        }
    }

    /** Client: a soft tick-tock, the odd trickle of sand and faint glints in the glass. */
    private void ambience(Level level, BlockPos pos) {
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        if (clientAge % 10 == 0) {
            boolean tock = (clientAge / 10) % 2 == 1;
            level.playLocalSound(x, y, z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.12F, tock ? 1.45F : 1.9F, false);
            if (!tock) {
                level.playLocalSound(x, y, z, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.05F, 1.8F, false);
            }
        }
        if (random.nextInt(50) == 0) {
            level.playLocalSound(x, pos.getY() + 0.4, z, SoundEvents.SAND_STEP, SoundSource.BLOCKS, 0.05F, 1.6F + random.nextFloat() * 0.3F,
                    false);
        }
        if (random.nextInt(14) == 0) {
            level.addParticle(ModParticles.CHRONO_TICK.get(), x + (random.nextDouble() - 0.5) * 0.7, y + (random.nextDouble() - 0.5) * 1.4,
                    z + (random.nextDouble() - 0.5) * 0.7, 0.0, 0.01, 0.0);
        }
    }

    private void sound(Level level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, sound,
                SoundSource.BLOCKS, volume, pitch);
    }

    private void changed(ServerLevel level) {
        lastSignal = comparatorSignal();
        setChanged();
        level.updateNeighbourForOutputSignal(worldPosition.above(), getBlockState().getBlock());
        sync(level);
    }

    private void sync(ServerLevel level) {
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ save and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_REMAINING, remaining);
        tag.putInt(TAG_RUN, runTotal);
        tag.putBoolean(TAG_PAUSED, paused);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        remaining = Mth.clamp(tag.getInt(TAG_REMAINING), 0, Balance.HOURGLASS_MAX_TICKS);
        runTotal = Math.max(remaining, tag.getInt(TAG_RUN));
        paused = tag.getBoolean(TAG_PAUSED);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
