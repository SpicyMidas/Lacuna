package com.lacuna.mod.pearl;

import com.lacuna.mod.registry.ModBlockEntities;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds the locked Arrival Shard (with its link and durability) so breaking the anchor gives it back. */
public class ArrivalAnchorBlockEntity extends BlockEntity {
    private ItemStack shard = ItemStack.EMPTY;

    public ArrivalAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARRIVAL_ANCHOR.get(), pos, state);
    }

    public ItemStack shard() {
        return shard;
    }

    public void setShard(ItemStack stack) {
        this.shard = stack.copyWithCount(1);
        setChanged();
    }

    public Optional<UUID> linkId() {
        PearlLink link = shard.get(com.lacuna.mod.registry.ModComponents.PEARL_LINK.get());
        return link == null ? Optional.empty() : Optional.of(link.id());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shard = tag.contains("shard") ? ItemStack.parseOptional(registries, tag.getCompound("shard")) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!shard.isEmpty()) {
            tag.put("shard", shard.save(registries));
        }
    }
}
