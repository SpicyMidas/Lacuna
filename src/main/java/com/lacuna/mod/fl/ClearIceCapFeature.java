package com.lacuna.mod.fl;

import com.lacuna.mod.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Cinderfrost Flats: caps still water with nearly transparent {@code clear_ice} (like the vanilla freeze_top_layer
 * pass, one chunk per call), so the ruins under the ponds stay visible.
 */
public class ClearIceCapFeature extends Feature<NoneFeatureConfiguration> {
    public ClearIceCapFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BlockState ice = ModBlocks.CLEAR_ICE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int x = origin.getX() + i;
                int z = origin.getZ() + j;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                pos.set(x, y, z);
                if (level.getBlockState(pos).is(Blocks.WATER) && ForgottenBiomes.is(level, pos, ForgottenBiomes.CINDERFROST_FLATS)) {
                    level.setBlock(pos, ice, 2);
                }
            }
        }
        return true; // like vanilla freeze_top_layer: a chunk without ponds is not a failure
    }
}
