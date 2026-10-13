package com.lacuna.mod.structure;

import com.lacuna.mod.Lacuna;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structure processor {@code lacuna:hanging_entities} (processor list {@code lacuna:murals}): template entities keep
 * template-relative TileX/Y/Z, which a placed painting rejects ("Block-attached entity at invalid position") before it
 * is moved. This rewrites them to the transformed world block position so Memory Mural paintings load cleanly.
 */
public final class HangingEntityProcessor extends StructureProcessor {
    public static final HangingEntityProcessor INSTANCE = new HangingEntityProcessor();
    public static final MapCodec<HangingEntityProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Lacuna.MODID);
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<HangingEntityProcessor>> TYPE =
            PROCESSORS.register("hanging_entities", () -> () -> CODEC);

    private HangingEntityProcessor() {}

    @Override
    public StructureTemplate.StructureEntityInfo processEntity(LevelReader world, BlockPos seedPos,
            StructureTemplate.StructureEntityInfo rawEntityInfo, StructureTemplate.StructureEntityInfo entityInfo,
            StructurePlaceSettings placementSettings, StructureTemplate template) {
        String id = entityInfo.nbt.getString("id");
        if (!id.equals("minecraft:painting") && !id.equals("minecraft:item_frame") && !id.equals("minecraft:glow_item_frame")) {
            return entityInfo;
        }
        CompoundTag nbt = entityInfo.nbt.copy();
        BlockPos pos = BlockPos.containing(entityInfo.pos);
        nbt.putInt("TileX", pos.getX());
        nbt.putInt("TileY", pos.getY());
        nbt.putInt("TileZ", pos.getZ());
        return new StructureTemplate.StructureEntityInfo(entityInfo.pos, entityInfo.blockPos, nbt);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE.get();
    }
}
