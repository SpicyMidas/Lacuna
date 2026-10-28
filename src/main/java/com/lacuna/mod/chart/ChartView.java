package com.lacuna.mod.chart;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Which part of the Overworld a chart shows while held in the holder's current dimension.
 * <ul>
 * <li>Overworld: the land right around the holder ({@code scale} 1).</li>
 * <li>Forgotten Land: the Overworld under the holder's translated position, with the banishment's own scale
 * ({@code forgottenLand.scale}, 1 block there = 12 here), so the chart shows where the way home would end.</li>
 * <li>The End: no position there matches one in the Overworld, so the chart rests on where leaving would bring the
 * holder (their Overworld bed or respawn anchor, else the world spawn) and does not follow them ({@code follow}
 * false).</li>
 * <li>Any other dimension (the Nether, modded ones): the ratio of the dimension types' {@code coordinate_scale}, as
 * vanilla portals use it (the Nether: 1 block = 8).</li>
 * </ul>
 */
public record ChartView(boolean follow, double scale, BlockPos anchor) {
    public static ChartView of(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ServerLevel overworld = player.server.overworld();
        if (level == overworld) {
            return new ChartView(true, 1.0, BlockPos.ZERO);
        }
        if (ModDimensions.isForgottenLand(level)) {
            return new ChartView(true, LacunaConfig.get(LacunaConfig.FL_SCALE), BlockPos.ZERO);
        }
        if (level.dimension() == Level.END) {
            return new ChartView(false, 0.0, home(player, overworld));
        }
        return new ChartView(true, DimensionType.getTeleportationScale(level.dimensionType(), overworld.dimensionType()), BlockPos.ZERO);
    }

    /** The holder's Overworld bed or respawn anchor, else the world spawn. */
    public static BlockPos home(ServerPlayer player, ServerLevel overworld) {
        BlockPos respawn = player.getRespawnPosition();
        return respawn != null && player.getRespawnDimension() == Level.OVERWORLD ? respawn : overworld.getSharedSpawnPos();
    }

    /** Overworld X the chart is centred on for a holder at {@code x} in their own dimension. */
    public double centreX(double x) {
        return follow ? x * scale : anchor.getX() + 0.5;
    }

    public double centreZ(double z) {
        return follow ? z * scale : anchor.getZ() + 0.5;
    }
}
