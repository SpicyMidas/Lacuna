package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.eclipse.EclipseCharge;
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

    private ModComponents() {}
}
