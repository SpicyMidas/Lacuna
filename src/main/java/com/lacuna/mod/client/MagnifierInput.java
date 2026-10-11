package com.lacuna.mod.client;

import com.lacuna.mod.beam.DivineMagnifyingGlassItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Channelling the Divine Magnifying Glass must not slow the player down (spec M3). Vanilla multiplies the movement
 * input by 0.2 right after this event while an item is used, so the input is pre-scaled by 5 here.
 */
public final class MagnifierInput {
    private MagnifierInput() {}

    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player.isUsingItem() && !player.isPassenger() && player.getUseItem().getItem() instanceof DivineMagnifyingGlassItem) {
            event.getInput().leftImpulse *= 5.0F;
            event.getInput().forwardImpulse *= 5.0F;
        }
    }
}
