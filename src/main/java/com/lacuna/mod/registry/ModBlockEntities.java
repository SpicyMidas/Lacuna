package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.fl.UpdraftSpringBlockEntity;
import com.lacuna.mod.lantern.NullLanternBlockEntity;
import com.lacuna.mod.pearl.ArrivalAnchorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Lacuna.MODID);

    @SuppressWarnings("DataFlowIssue") // null data fixer type is the documented way for mod block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArrivalAnchorBlockEntity>> ARRIVAL_ANCHOR =
            BLOCK_ENTITIES.register("arrival_anchor",
                    () -> BlockEntityType.Builder.of(ArrivalAnchorBlockEntity::new, ModBlocks.ARRIVAL_ANCHOR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UpdraftSpringBlockEntity>> UPDRAFT_SPRING =
            BLOCK_ENTITIES.register("updraft_spring",
                    () -> BlockEntityType.Builder.of(UpdraftSpringBlockEntity::new, ModBlocks.UPDRAFT_SPRING.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NullLanternBlockEntity>> NULL_LANTERN =
            BLOCK_ENTITIES.register("null_lantern",
                    () -> BlockEntityType.Builder.of(NullLanternBlockEntity::new, ModBlocks.NULL_LANTERN.get()).build(null));

    private ModBlockEntities() {}
}
