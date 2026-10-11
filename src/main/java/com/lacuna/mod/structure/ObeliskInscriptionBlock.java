package com.lacuna.mod.structure;

import com.lacuna.mod.Balance;
import com.lacuna.mod.logic.ObeliskRules;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.util.SafeRun;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Obelisk inscription (spec M10): right click shows one of 24 lore lines chosen by the position hash; 12 % chance of
 * a small reward, at most once per player per obelisk and not more often than every 20 minutes per obelisk.
 */
public class ObeliskInscriptionBlock extends Block {
    public static final MapCodec<ObeliskInscriptionBlock> CODEC = simpleCodec(ObeliskInscriptionBlock::new);

    public ObeliskInscriptionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
            return InteractionResult.PASS;
        }
        int line = ObeliskRules.loreIndex(pos.getX(), pos.getY(), pos.getZ(), Balance.OBELISK_LORE_LINES);
        sp.sendSystemMessage(Component.translatable("lacuna.obelisk.line." + line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        level.playSound(null, pos, SoundEvents.SCULK_CLICKING, SoundSource.BLOCKS, 0.6F, 0.6F);
        SafeRun.run("obelisk", () -> maybeReward(server, sp, pos));
        return InteractionResult.CONSUME;
    }

    private static void maybeReward(ServerLevel level, ServerPlayer player, BlockPos pos) {
        ObeliskSavedData data = ObeliskSavedData.get(level.getServer());
        String key = ObeliskSavedData.key(level.dimension(), pos);
        long now = level.getGameTime();
        if (!ObeliskRules.mayReward(data.claimed(player.getUUID(), key), now, data.cooldownUntil(key))) {
            return;
        }
        if (level.getRandom().nextFloat() >= Balance.OBELISK_REWARD_CHANCE) {
            return;
        }
        ItemStack reward = switch (ObeliskRules.rewardKind(level.getRandom().nextDouble())) {
            case 0 -> new ItemStack(ModItems.RUBY.get(), 1 + level.getRandom().nextInt(3));
            case 1 -> new ItemStack(ModItems.DIVINE_GLASS_SHARD.get(), 1 + level.getRandom().nextInt(2));
            default -> new ItemStack(ModItems.CHRONAL_SHARD.get());
        };
        data.reward(player.getUUID(), key, now + Balance.OBELISK_COOLDOWN);
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        player.sendSystemMessage(Component.translatable("lacuna.obelisk.reward").withStyle(ChatFormatting.GOLD));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
    }
}
