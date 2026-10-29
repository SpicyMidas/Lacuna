package com.lacuna.mod.structure;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.network.RevealOresPayload;
import com.lacuna.mod.util.SafeRun;
import com.mojang.serialization.MapCodec;
import java.util.List;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sunken Bell (spec M10): ringing it (60 s cooldown per player) reveals up to 64 ores within 24 blocks to the ringer
 * for 8 s, with a bell toll and expanding rings of particles. Cooldowns are volatile (cleared on server stop).
 */
public class SunkenBellBlock extends Block {
    public static final MapCodec<SunkenBellBlock> CODEC = simpleCodec(SunkenBellBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 2, 12), Block.box(5, 2, 5, 11, 11, 11),
            Block.box(7, 11, 7, 9, 14, 9));
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    public SunkenBellBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public static void clearVolatile() {
        COOLDOWNS.clear();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
            return InteractionResult.PASS;
        }
        if (!LacunaConfig.get(LacunaConfig.SUNKEN_BELL_ENABLED)) {
            // sunkenBell off: just a bell
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.7F);
            return InteractionResult.CONSUME;
        }
        long now = level.getGameTime();
        Long ready = COOLDOWNS.get(sp.getUUID());
        if (ready != null && now < ready) {
            sp.displayClientMessage(Component.translatable("lacuna.bell.cooldown", (ready - now + 19) / 20), true);
            return InteractionResult.CONSUME;
        }
        COOLDOWNS.put(sp.getUUID(), now + Balance.BELL_COOLDOWN);
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.7F);
        SafeRun.run("sunken_bell", () -> {
            List<BlockPos> ores = OreScan.scan(server, pos, Balance.BELL_RADIUS, Balance.BELL_MAX_POSITIONS);
            PacketDistributor.sendToPlayer(sp, new RevealOresPayload(ores, Balance.BELL_REVEAL_TICKS));
            sp.displayClientMessage(Component.translatable("lacuna.bell.reveal", ores.size()), true);
            rings(server, pos);
        });
        return InteractionResult.CONSUME;
    }

    private static void rings(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.3;
        double cz = pos.getZ() + 0.5;
        for (int ring = 1; ring <= 4; ring++) {
            int points = 10 * ring;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                level.sendParticles(ParticleTypes.NAUTILUS, cx + Math.cos(a) * ring, cy, cz + Math.sin(a) * ring, 1, 0, 0, 0, 0.0);
            }
        }
    }
}
