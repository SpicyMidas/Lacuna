package com.lacuna.mod.essence;

import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Where a Lacunar Quintessence comes from, besides loot tables (docs/PROGRESSION.md has every source and rate):
 * a star nobody called sometimes carries one down. The loot-table sources are data: the Reflection of a Mirror
 * Labyrinth (always, when a player defeats it), Prime Star Rock, the Sunken Bell Tower and ruin caches; the Last
 * Merchant's offer lives in {@link com.lacuna.mod.logic.MerchantPricing}.
 */
public final class Quintessence {
    public static final String FEATURE = "quintessence";
    /** Chance that a natural falling star leaves a Quintessence at its impact (called stars never do). */
    public static final float NATURAL_STAR_CHANCE = 0.2F;

    private Quintessence() {}

    /**
     * A natural star just struck at {@code at}: one time in five a Quintessence rests at the impact, glowing so it can
     * be found in the dark. It is immune to the star's own fire and lasts twenty minutes ({@link QuintessenceItem}).
     */
    public static void onNaturalStarLanded(ServerLevel level, Vec3 at) {
        SafeRun.run(FEATURE, () -> {
            if (level.getRandom().nextFloat() >= NATURAL_STAR_CHANCE) {
                return;
            }
            ItemEntity drop = new ItemEntity(level, at.x, at.y + 1.0, at.z, new ItemStack(ModItems.LACUNAR_QUINTESSENCE.get()),
                    0.0, 0.15, 0.0);
            drop.setGlowingTag(true);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.6F);
        });
    }
}
