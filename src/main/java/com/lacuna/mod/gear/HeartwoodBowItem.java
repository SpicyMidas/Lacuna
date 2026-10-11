package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModAttachments;
import com.lacuna.mod.registry.ModEffects;
import com.lacuna.mod.registry.ModTags;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Heartwood Bow (spec M7): arrows root their target for 2 s (movement and jump -100 %), 4 s with "pinned" particles
 * when a solid wall is within 1.5 blocks behind the target along the arrow's flight. Arrows hitting blocks sprout.
 */
public class HeartwoodBowItem extends BowItem {
    public HeartwoodBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setData(ModAttachments.HEARTWOOD_ARROW, true);
        return super.customArrow(arrow, projectileStack, weaponStack);
    }

    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)
                || !arrow.hasData(ModAttachments.HEARTWOOD_ARROW) || !arrow.getData(ModAttachments.HEARTWOOD_ARROW)
                || !LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED)) {
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target) {
            Vec3 dir = arrow.getDeltaMovement().normalize();
            Vec3 behind = target.position().add(0, target.getBbHeight() * 0.5, 0).add(dir.scale(target.getBbWidth() * 0.5 + 1.5));
            BlockPos wall = BlockPos.containing(behind);
            boolean pinned = level.hasChunkAt(wall) && level.getBlockState(wall).isCollisionShapeFullBlock(level, wall);
            target.addEffect(new MobEffectInstance(ModEffects.ROOTED, pinned ? Balance.ROOTED_PINNED_TICKS : Balance.ROOTED_TICKS, 0));
            if (pinned) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 0.5, target.getZ(), 16, 0.4, 0.6, 0.4, 0.0);
                level.sendParticles(ParticleTypes.CRIT, behind.x, behind.y, behind.z, 8, 0.1, 0.3, 0.1, 0.1);
            }
        } else if (event.getRayTraceResult() instanceof BlockHitResult block) {
            Vec3 at = block.getLocation();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.0);
        }
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(ModTags.Items.HEARTWOOD_REPAIR);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return Enchants.unbreakingOnly(enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return Enchants.unbreakingOnly(enchantment);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lacuna.heartwood_bow.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.heartwood_bow.use").withStyle(ChatFormatting.GREEN));
    }
}
