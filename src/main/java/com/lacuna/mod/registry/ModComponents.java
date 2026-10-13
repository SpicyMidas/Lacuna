package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.chrono.WatchSnapshot;
import com.lacuna.mod.eclipse.EclipseCharge;
import com.lacuna.mod.mirror.StoredProjectile;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import com.lacuna.mod.pearl.PearlLink;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components (spec section 3, "Data"): every one has a Codec, a StreamCodec and a safe default. */
public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Lacuna.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EclipseCharge>> ECLIPSE_CHARGE =
            COMPONENTS.registerComponentType("eclipse_charge",
                    b -> b.persistent(EclipseCharge.CODEC).networkSynchronized(EclipseCharge.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PearlLink>> PEARL_LINK =
            COMPONENTS.registerComponentType("pearl_link",
                    b -> b.persistent(PearlLink.CODEC).networkSynchronized(PearlLink.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<WatchSnapshot>>> WATCH_SNAPSHOTS =
            COMPONENTS.registerComponentType("watch_snapshots",
                    b -> b.persistent(WatchSnapshot.LIST_CODEC).networkSynchronized(WatchSnapshot.LIST_STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHRONAL_SHARDS =
            COMPONENTS.registerComponentType("chronal_shards",
                    b -> b.persistent(Codec.intRange(0, 5)).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<StoredProjectile>>> MIRROR_STORED =
            COMPONENTS.registerComponentType("mirror_stored",
                    b -> b.persistent(StoredProjectile.LIST_CODEC).networkSynchronized(StoredProjectile.LIST_STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIDE_CHARGES =
            COMPONENTS.registerComponentType("tide_charges",
                    b -> b.persistent(Codec.intRange(0, 3)).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.lacuna.mod.village.ContractData>> GUILD_CONTRACT =
            COMPONENTS.registerComponentType("guild_contract",
                    b -> b.persistent(com.lacuna.mod.village.ContractData.CODEC).networkSynchronized(com.lacuna.mod.village.ContractData.STREAM_CODEC));

    private ModComponents() {}
}
