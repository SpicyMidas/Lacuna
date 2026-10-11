package com.lacuna.mod.wood;

import com.lacuna.mod.registry.ModFeatures;
import com.lacuna.mod.registry.ModWoodBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/** Tree decorator placing Crystal Willow strands (1-3 long) under leaves with an air gap below. */
public class HangingStrandsDecorator extends TreeDecorator {
    public static final MapCodec<HangingStrandsDecorator> CODEC = Codec.floatRange(0.0F, 1.0F).fieldOf("probability")
            .xmap(HangingStrandsDecorator::new, d -> d.probability);

    private final float probability;

    public HangingStrandsDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return ModFeatures.HANGING_STRANDS.get();
    }

    @Override
    public void place(Context context) {
        RandomSource random = context.random();
        for (BlockPos leaf : context.leaves()) {
            if (random.nextFloat() >= probability) {
                continue;
            }
            int length = 1 + random.nextInt(3);
            BlockPos p = leaf.below();
            for (int i = 0; i < length && context.isAir(p); i++) {
                context.setBlock(p, ModWoodBlocks.CRYSTAL_WILLOW_STRANDS.get().defaultBlockState());
                p = p.below();
            }
        }
    }
}
