package com.lacuna.mod.lantern;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModBlockEntities;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The placed Null Lantern: a vanilla lantern (standing, or hanging under a ceiling; waterloggable; needs support;
 * destroyed by pistons) with a {@link #LIT} state. Its {@link NullLanternBlockEntity} holds the charge; breaking it in
 * any way drops exactly one lantern carrying that charge. Using an Ender Pearl or Echo Shard on it recharges and
 * relights it.
 */
public class NullLanternBlock extends LanternBlock implements EntityBlock {
    public static final MapCodec<NullLanternBlock> CODEC = simpleCodec(NullLanternBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public NullLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, true));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<LanternBlock> codec() {
        return (MapCodec<LanternBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    /** Placed dark when the lantern in hand has no charge left. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        ItemStack stack = context.getItemInHand();
        return state.setValue(LIT, !(stack.getItem() instanceof NullLanternItem) || NullLanternItem.charge(stack) > 0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NullLanternBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.NULL_LANTERN.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<NullLanternBlockEntity>) NullLanternBlockEntity::serverTick;
    }

    /** Ender Pearl / Echo Shard on the placed lantern: recharge it and light it again. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (LanternWard.fuelValue(stack, 1) <= 0 || !LacunaConfig.get(LacunaConfig.LANTERN_ENABLED)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS; // the server decides; never fall through to throwing the pearl
        }
        if (!(level.getBlockEntity(pos) instanceof NullLanternBlockEntity lantern)) {
            return ItemInteractionResult.CONSUME;
        }
        boolean fed = SafeRun.call(LanternWard.FEATURE, () -> lantern.feed((ServerLevel) level, stack, player), false);
        return fed ? ItemInteractionResult.SUCCESS : ItemInteractionResult.CONSUME;
    }

    /** Exactly one lantern, carrying the stored charge (and name), whatever broke the block: hand, tool, explosion, piston. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack stack = new ItemStack(asItem());
        if (stack.isEmpty()) {
            return List.of();
        }
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof NullLanternBlockEntity lantern) {
            stack.applyComponents(lantern.collectComponents());
        } else if (!state.getValue(LIT)) {
            stack.setDamageValue(stack.getMaxDamage());
        }
        return List.of(stack);
    }

    /** Faint motes of shadow and the odd violet wisp drifting off a lit lantern. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + (state.getValue(HANGING) ? 0.4 : 0.3);
        double z = pos.getZ() + 0.5;
        level.addParticle(ModParticles.SHADOW_MOTE.get(), x + (random.nextDouble() - 0.5) * 0.5, y + random.nextDouble() * 0.3,
                z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.01, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.UMBRAL_WISP.get(), x + (random.nextDouble() - 0.5) * 0.3, y + 0.1,
                    z + (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.02, 0.02, (random.nextDouble() - 0.5) * 0.02);
        }
    }
}
