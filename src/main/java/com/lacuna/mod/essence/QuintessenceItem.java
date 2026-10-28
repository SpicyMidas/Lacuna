package com.lacuna.mod.essence;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Lacunar Quintessence, the legendary essence every totem is crafted from (the Totems of Banishing, Confusion and
 * Undying; sources and rates in docs/PROGRESSION.md). It always glints; dropped, it lasts {@link #ENTITY_LIFESPAN}
 * ticks instead of five minutes and shrugs off fire, lava, cactus and explosions (only the void or /kill take it), so
 * the one a natural falling star leaves behind ({@link Quintessence#onNaturalStarLanded}) waits for whoever looks.
 */
public class QuintessenceItem extends Item {
    /** Twenty minutes on the ground. */
    public static final int ENTITY_LIFESPAN = 24000;

    public QuintessenceItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return ENTITY_LIFESPAN;
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.lacunar_quintessence.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.lacunar_quintessence.use").withStyle(ChatFormatting.GRAY));
    }
}
