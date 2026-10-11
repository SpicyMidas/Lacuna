package com.lacuna.mod.structure;

import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModEntities;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mirror Labyrinth flow (spec M10): stepping on the central trigger spawns one Reflection per labyrinth; killing it
 * turns every {@code labyrinth_exit} block of that labyrinth into air (opening the exit door and the Mirror Hoard
 * alcove) and marks the trigger solved. Active reflections are volatile: a restart or a lost reflection re-arms it.
 */
public final class LabyrinthHandler {
    private static final Map<String, UUID> ACTIVE = new ConcurrentHashMap<>();
    private static final int HALF = 13;

    private LabyrinthHandler() {}

    static String key(ServerLevel level, BlockPos trigger) {
        return level.dimension().location() + "|" + trigger.asLong();
    }

    public static void clearVolatile() {
        ACTIVE.clear();
    }

    static void onTrigger(ServerLevel level, BlockPos trigger, BlockState state, ServerPlayer player) {
        if (state.getValue(LabyrinthTriggerBlock.SOLVED) || player.isCreative() || player.isSpectator()) {
            return;
        }
        String key = key(level, trigger);
        UUID existing = ACTIVE.get(key);
        if (existing != null && level.getEntity(existing) instanceof ReflectionEntity r && r.isAlive()) {
            return;
        }
        ReflectionEntity reflection = ModEntities.REFLECTION.get().create(level);
        if (reflection == null) {
            return;
        }
        reflection.moveTo(trigger.getX() + 0.5, trigger.getY(), trigger.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
        reflection.bind(player.getUUID(), trigger);
        level.addFreshEntity(reflection);
        ACTIVE.put(key, reflection.getUUID());
        level.playSound(null, trigger, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.0F, 0.5F);
        player.displayClientMessage(Component.translatable("lacuna.labyrinth.reflection"), true);
    }

    public static void onReflectionGone(ServerLevel level, BlockPos trigger, boolean defeated) {
        ACTIVE.remove(key(level, trigger));
        if (!defeated) {
            return;
        }
        BlockState state = level.getBlockState(trigger);
        if (state.is(ModBlocks.LABYRINTH_TRIGGER.get())) {
            level.setBlock(trigger, state.setValue(LabyrinthTriggerBlock.SOLVED, true), 3);
        }
        int opened = 0;
        for (BlockPos p : BlockPos.betweenClosed(trigger.offset(-HALF, -2, -HALF), trigger.offset(HALF, 4, HALF))) {
            if (level.getBlockState(p).is(ModBlocks.LABYRINTH_EXIT.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
                opened++;
            }
        }
        if (opened > 0) {
            level.playSound(null, trigger, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        }
    }
}
