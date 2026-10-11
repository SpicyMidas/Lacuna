package com.lacuna.mod.registry;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Lacuna.MODID);

    /** Starlace: defense 2/6/5/2, toughness 0.5, enchantability 18, repaired with Starlace Cloth. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STARLACE = ARMOR_MATERIALS.register("starlace", () -> {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, 2);
        defense.put(ArmorItem.Type.CHESTPLATE, 6);
        defense.put(ArmorItem.Type.LEGGINGS, 5);
        defense.put(ArmorItem.Type.BOOTS, 2);
        defense.put(ArmorItem.Type.BODY, 6);
        return new ArmorMaterial(defense, Balance.STARLACE_ENCHANTABILITY, SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> Ingredient.of(ModItems.STARLACE_CLOTH.get()), List.of(new ArmorMaterial.Layer(Lacuna.id("starlace"))),
                Balance.STARLACE_TOUGHNESS, 0.0F);
    });

    private ModArmorMaterials() {}
}
