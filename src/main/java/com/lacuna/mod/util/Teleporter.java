package com.lacuna.mod.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * All Lacuna teleports go through here. While a Lacuna transfer is in progress {@link #isLacunaTransfer()} is true,
 * so the Forgotten Land travel blocker lets our own transfers through (spec 3, "1.21.1 API pitfalls").
 */
public final class Teleporter {
    private static int depth;

    private Teleporter() {}

    public static boolean isLacunaTransfer() {
        return depth > 0;
    }

    /**
     * Moves an entity to {@code pos} in {@code dest}. Returns the entity instance that now exists in the destination
     * (non-player entities are recreated on dimension change), or null if the transfer did not happen.
     */
    @Nullable
    public static Entity teleport(Entity entity, ServerLevel dest, Vec3 pos, float yRot, float xRot) {
        entity.stopRiding();
        entity.ejectPassengers();
        if (entity instanceof Leashable leashable && leashable.isLeashed()) {
            leashable.dropLeash(true, true);
        }
        depth++;
        try {
            if (entity instanceof ServerPlayer player) {
                player.teleportTo(dest, pos.x, pos.y, pos.z, yRot, xRot);
                player.resetFallDistance();
                return player.level() == dest ? player : null;
            }
            if (entity.level() == dest) {
                entity.teleportTo(pos.x, pos.y, pos.z);
                entity.setYRot(yRot);
                entity.resetFallDistance();
                return entity;
            }
            Entity moved = entity.changeDimension(new DimensionTransition(dest, pos, Vec3.ZERO, yRot, xRot, DimensionTransition.DO_NOTHING));
            if (moved != null) {
                moved.resetFallDistance();
            }
            return moved;
        } finally {
            depth--;
        }
    }
}
