package com.lacuna.mod.orrery;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.logic.CoordMath;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.util.SafeRun;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Orrery of Thresholds (spec M6): right click shows the Overworld/Forgotten Land coordinate translation and the
 * direction and distance of the nearest structure in {@code lacuna:orrery_targets}. Per-player 5 s cooldown and a
 * global rate limit on structure searches; no locate inside the Forgotten Land.
 */
public class OrreryBlock extends Block {
    public static final MapCodec<OrreryBlock> CODEC = simpleCodec(OrreryBlock::new);
    public static final TagKey<Structure> TARGETS = TagKey.create(Registries.STRUCTURE, com.lacuna.mod.Lacuna.id("orrery_targets"));
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static long lastSearch = Long.MIN_VALUE;

    public OrreryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
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
        if (!LacunaConfig.get(LacunaConfig.ORRERY_ENABLED)) {
            return InteractionResult.PASS;
        }
        long now = level.getGameTime();
        Long until = COOLDOWNS.get(player.getUUID());
        if (until != null && now < until) {
            return InteractionResult.CONSUME;
        }
        COOLDOWNS.put(player.getUUID(), now + Balance.ORRERY_COOLDOWN);
        SafeRun.run("orrery", () -> report((ServerLevel) level, pos, player));
        return InteractionResult.CONSUME;
    }

    private static void report(ServerLevel level, BlockPos pos, Player player) {
        double scale = LacunaConfig.get(LacunaConfig.FL_SCALE);
        int x = player.getBlockX();
        int z = player.getBlockZ();
        boolean inFl = ModDimensions.isForgottenLand(level);
        Component coords = inFl
                ? Component.translatable("lacuna.orrery.coords_fl", x, z, CoordMath.toOverworld(x, scale), CoordMath.toOverworld(z, scale))
                : Component.translatable("lacuna.orrery.coords_ow", x, z, CoordMath.toForgotten(x, scale), CoordMath.toForgotten(z, scale));
        if (inFl) {
            player.displayClientMessage(coords, true);
            return;
        }
        long now = level.getGameTime();
        if (now - lastSearch < Balance.ORRERY_GLOBAL_INTERVAL) {
            player.displayClientMessage(Component.translatable("lacuna.orrery.busy", coords), true);
            return;
        }
        lastSearch = now;
        BlockPos found = level.findNearestMapStructure(TARGETS, pos, LacunaConfig.get(LacunaConfig.ORRERY_RADIUS_CHUNKS), false);
        if (found == null) {
            player.displayClientMessage(Component.translatable("lacuna.orrery.nothing", coords), true);
            return;
        }
        int dx = found.getX() - x;
        int dz = found.getZ() - z;
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        player.displayClientMessage(Component.translatable("lacuna.orrery.found", coords,
                Component.translatable("lacuna.direction." + direction(dx, dz)), dist), true);
    }

    static String direction(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        if (angle < 0) {
            angle += 360;
        }
        String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return names[(int) Math.round(angle / 45.0) % 8];
    }

    public static void clearVolatile() {
        COOLDOWNS.clear();
        lastSearch = Long.MIN_VALUE;
    }
}
