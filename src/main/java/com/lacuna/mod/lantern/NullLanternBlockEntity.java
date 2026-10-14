package com.lacuna.mod.lantern;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Charge of a placed Null Lantern (same units as the item's durability). Placing copies the item's remaining charge in
 * ({@link #applyImplicitComponents}); breaking copies it back out ({@link #collectImplicitComponents}, used by the
 * block's drops). While lit it burns 1 charge per {@link Balance#LANTERN_TICKS_PER_DURABILITY} ticks, keeps its spot in
 * the {@link LanternWard} registry and makes mobs hunting players inside its zone give up; at 0 it goes dark.
 */
public class NullLanternBlockEntity extends BlockEntity {
    private static final String TAG_CHARGE = "charge";

    private int charge = Balance.LANTERN_DURABILITY;

    public NullLanternBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NULL_LANTERN.get(), pos, state);
    }

    public static int maxCharge() {
        return Balance.LANTERN_DURABILITY;
    }

    public int charge() {
        return charge;
    }

    private boolean lit() {
        return getBlockState().getValue(NullLanternBlock.LIT) && charge > 0;
    }

    // ------------------------------------------------------------------ zone registry

    @Override
    public void onLoad() {
        super.onLoad();
        syncZone();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LanternWard.unregister(level, worldPosition);
    }

    private void syncZone() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (lit()) {
            LanternWard.register(level, worldPosition);
        } else {
            LanternWard.unregister(level, worldPosition);
        }
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, NullLanternBlockEntity lantern) {
        if (!(level instanceof ServerLevel server) || !LanternWard.active()) {
            return;
        }
        SafeRun.run(LanternWard.FEATURE, () -> lantern.tick(server, pos, state));
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(NullLanternBlock.LIT)) {
            LanternWard.unregister(level, pos);
            return;
        }
        if (charge <= 0) {
            extinguish(level, pos, state);
            return;
        }
        long now = level.getGameTime();
        if (now % 20 == 0) {
            LanternWard.register(level, pos); // self-healing: the registry always matches what is really lit
        }
        if (now % Balance.LANTERN_TICKS_PER_DURABILITY == 0) {
            // same game-time clock as the held lantern, so picking it up and placing it again never skips a payment
            charge--;
            setChanged();
            if (charge <= 0) {
                extinguish(level, pos, state);
                return;
            }
        }
        if (Math.floorMod(now + pos.asLong(), 5L) == 0) {
            LanternWard.guardZone(level, pos);
        }
    }

    /** Out of charge: the lantern goes dark (no light, no ward) but stays where it is. */
    private void extinguish(ServerLevel level, BlockPos pos, BlockState state) {
        charge = 0;
        setChanged();
        LanternWard.unregister(level, pos);
        if (state.getValue(NullLanternBlock.LIT)) {
            level.setBlock(pos, state.setValue(NullLanternBlock.LIT, false), Block.UPDATE_ALL);
        }
        Vec3 centre = Vec3.atCenterOf(pos);
        LanternWard.fizzle(level, centre);
        double r2 = (double) Balance.LANTERN_PLACED_RADIUS * Balance.LANTERN_PLACED_RADIUS;
        for (Player player : level.players()) {
            if (player.distanceToSqr(centre) <= r2) {
                player.displayClientMessage(Component.translatable("lacuna.lantern.placed_out").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
    }

    /** One Ender Pearl / Echo Shard from {@code fuel}: recharges and relights. False (nothing consumed) when full. */
    boolean feed(ServerLevel level, ItemStack fuel, Player player) {
        int restore = LanternWard.fuelValue(fuel, maxCharge());
        if (restore <= 0) {
            return false;
        }
        if (charge >= maxCharge()) {
            player.displayClientMessage(Component.translatable("lacuna.lantern.full").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        charge = Math.min(maxCharge(), charge + restore);
        setChanged();
        fuel.consume(1, player);
        BlockState state = getBlockState();
        if (!state.getValue(NullLanternBlock.LIT)) {
            level.setBlock(worldPosition, state.setValue(NullLanternBlock.LIT, true), Block.UPDATE_ALL);
        }
        LanternWard.register(level, worldPosition);
        LanternWard.rechargeCue(level, Vec3.atCenterOf(worldPosition));
        player.displayClientMessage(Component.translatable("lacuna.lantern.recharged", LanternWard.percent(charge, maxCharge()))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    // ------------------------------------------------------------------ item <-> block charge

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        charge = maxCharge() - Mth.clamp(input.getOrDefault(DataComponents.DAMAGE, 0), 0, maxCharge());
        syncZone();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(DataComponents.DAMAGE, maxCharge() - Mth.clamp(charge, 0, maxCharge()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(TAG_CHARGE);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = tag.contains(TAG_CHARGE) ? Mth.clamp(tag.getInt(TAG_CHARGE), 0, maxCharge()) : maxCharge();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_CHARGE, charge);
    }
}
