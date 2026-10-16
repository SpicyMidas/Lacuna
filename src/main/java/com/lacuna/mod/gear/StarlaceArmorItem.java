package com.lacuna.mod.gear;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A Starlace armor piece: plain armor whose tooltip lists the set abilities ({@link StarlaceSet}). */
public class StarlaceArmorItem extends ArmorItem {
    public StarlaceArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        StarlaceSet.appendTooltip(this, tooltip);
    }
}
