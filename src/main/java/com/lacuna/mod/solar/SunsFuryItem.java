package com.lacuna.mod.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModDimensions;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Sun's Fury (spec 11.3): single-use thrown spear of sunlight; fizzles (and is kept) inside the Forgotten Land. */
public class SunsFuryItem extends Item {
    public SunsFuryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.SUNS_FURY_ENABLED)) {
            return InteractionResultHolder.fail(stack);
        }
        if (ModDimensions.isForgottenLand(level)) {
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 10, 0.2, 0.2, 0.2, 0.01);
                server.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.8F);
                player.displayClientMessage(Component.translatable("lacuna.suns_fury.fizzles"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide()) {
            SunsFuryEntity spear = new SunsFuryEntity(level, player);
            spear.setItem(stack.copyWithCount(1));
            spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, Balance.SUNS_FURY_SPEED, 0.5F);
            level.addFreshEntity(spear);
            com.lacuna.mod.util.LacunaAdvancements.award(player, "lacuna/throw_suns_fury");
            // x/y/z overload accepts both SoundEvent and Holder<SoundEvent>
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.suns_fury.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.suns_fury.use").withStyle(ChatFormatting.GOLD));
    }
}
