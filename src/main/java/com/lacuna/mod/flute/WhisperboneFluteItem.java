package com.lacuna.mod.flute;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.SafeSpot;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Whisperbone Flute (spec M6): calls up to 3 Echoes of mobs that died nearby in the last minute. */
public class WhisperboneFluteItem extends Item {
    public static final String FEATURE = "whisperbone_flute";

    public WhisperboneFluteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LacunaConfig.get(LacunaConfig.FLUTE_ENABLED)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        int summoned = SafeRun.call(FEATURE, () -> summon((ServerLevel) level, player), 0);
        if (summoned == 0) {
            player.displayClientMessage(Component.translatable("lacuna.flute.silent"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(7).value(),
                SoundSource.PLAYERS, 1.0F, 0.6F);
        // the bones answer: souls slipping loose under the note
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.25F, 1.6F);
        player.getCooldowns().addCooldown(this, Balance.FLUTE_COOLDOWN);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.consume(stack);
    }

    private static int summon(ServerLevel level, Player player) {
        List<DeathMemory.Death> deaths = DeathMemory.take(level, player.position(), Balance.FLUTE_MAX_ECHOES);
        int count = 0;
        for (DeathMemory.Death death : deaths) {
            BlockPos at = SafeSpot.findNear(level, BlockPos.containing(death.pos())).orElse(player.blockPosition());
            EchoEntity echo = ModEntities.ECHO.get().create(level);
            if (echo == null) {
                continue;
            }
            echo.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0.0F);
            echo.setup(player.getUUID(), death.maxHealth(), death.attack());
            level.addFreshEntity(echo);
            level.sendParticles(ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 12, 0.3, 0.6, 0.3, 0.02);
            count++;
        }
        return count;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 40;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return false; // fixed +20 per Echo Fragment, see AnvilRepairs
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.whisperbone_flute.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.whisperbone_flute.use").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("lacuna.tooltip.cooldown", Balance.FLUTE_COOLDOWN / 20).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("lacuna.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
