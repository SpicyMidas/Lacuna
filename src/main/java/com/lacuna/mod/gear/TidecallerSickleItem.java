package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.registry.ModComponents;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tidecaller Sickle (spec M7, revision 3).
 * <ul>
 *   <li><b>Use - Tidal Wave</b> (cooldown 3 s, 2 durability): a curling wall of water 5 blocks wide rolls 14 blocks
 *       forward over the terrain, sweeping foes along (4 damage once each), putting out fires and quenching lava.</li>
 *   <li><b>Sneak + use at full tide - Maelstrom</b> (spends 3 charges, cooldown 15 s, 5 durability): a whirlpool
 *       drags foes in and drowns them for 5 s, then erupts in a geyser that blasts them into the air.</li>
 *   <li><b>Melee</b>: hits drench the target (Slowness I, fire out); +3 damage while the wielder is in water or rain.</li>
 *   <li><b>Tide charges</b> (max 3): melee hits in water or rain, waves that hit, and 1 per 10 s standing in water.</li>
 * </ul>
 * The currents themselves live in {@link TideCurrents}.
 */
public class TidecallerSickleItem extends SwordItem {
    public TidecallerSickleItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    private static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.SPECIAL_WEAPONS_ENABLED);
    }

    public static int tide(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TIDE_CHARGES.get(), 0);
    }

    /** "●●○": filled pips for the charges held. */
    public static MutableComponent pips(int charges) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < Balance.SICKLE_MAX_TIDE; i++) {
            out.append(Component.literal(i < charges ? "●" : "○")
                    .withStyle(i < charges ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    /** Sets the charges on {@code stack} and tells the wielder (action bar) when they change. */
    static void setTide(Player player, ItemStack stack, int value) {
        int old = tide(stack);
        int now = Mth.clamp(value, 0, Balance.SICKLE_MAX_TIDE);
        if (now == old) {
            return;
        }
        stack.set(ModComponents.TIDE_CHARGES.get(), now);
        String key = now < old ? "lacuna.sickle.tide_ebb" : now >= Balance.SICKLE_MAX_TIDE ? "lacuna.sickle.tide_full" : "lacuna.sickle.tide_rise";
        player.displayClientMessage(Component.translatable(key, pips(now)).withStyle(ChatFormatting.DARK_AQUA), true);
        if (now > old) {
            Sfx.at(player, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.9F, 0.9F + 0.2F * now);
            if (now >= Balance.SICKLE_MAX_TIDE) {
                Sfx.at(player, SoundEvents.CONDUIT_AMBIENT_SHORT, 1.0F, 1.3F);
            }
        }
    }

    /** The sickle the player holds (main hand first), or {@link ItemStack#EMPTY}. */
    static ItemStack heldSickle(Player player) {
        if (player.getMainHandItem().getItem() instanceof TidecallerSickleItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof TidecallerSickleItem) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    /** One more tide charge on the sickle the player holds. */
    static void gainTide(Player player) {
        ItemStack stack = heldSickle(player);
        if (!stack.isEmpty() && enabled()) {
            setTide(player, stack, tide(stack) + 1);
        }
    }

    // ------------------------------------------------------------------ use: wave / maelstrom

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!enabled()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            if (tide(stack) < Balance.SICKLE_MAX_TIDE) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("lacuna.sickle.tide_low", pips(tide(stack)))
                            .withStyle(ChatFormatting.GRAY), true);
                    Sfx.at(player, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.6F, 0.6F);
                }
                return InteractionResultHolder.fail(stack);
            }
            if (level instanceof ServerLevel server) {
                SafeRun.run(TideCurrents.MAELSTROM_FEATURE, () -> {
                    TideCurrents.startMaelstrom(server, player);
                    setTide(player, stack, 0);
                    player.getCooldowns().addCooldown(this, Balance.SICKLE_MAELSTROM_COOLDOWN);
                    stack.hurtAndBreak(Balance.SICKLE_MAELSTROM_DURABILITY, player, LivingEntity.getSlotForHand(hand));
                });
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (level instanceof ServerLevel server) {
            SafeRun.run(TideCurrents.WAVE_FEATURE, () -> {
                TideCurrents.startWave(server, player);
                player.getCooldowns().addCooldown(this, Balance.SICKLE_WAVE_COOLDOWN);
                stack.hurtAndBreak(Balance.SICKLE_WAVE_DURABILITY, player, LivingEntity.getSlotForHand(hand));
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ------------------------------------------------------------------ melee: the drench

    /** "The tide is with you": +3 damage in water or rain, scaled like the swing itself by the attack charge. */
    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || !attacker.isInWaterOrRain() || !enabled()) {
            return 0.0F;
        }
        float scale = 1.0F;
        if (attacker instanceof Player player) {
            double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            scale = base > 0.0 ? Mth.clamp((float) (damage / base), 0.0F, 1.0F) : 1.0F;
        }
        return Balance.SICKLE_TIDE_BONUS * scale;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel server && enabled()) {
            SafeRun.run("tidecaller_melee", () -> drench(server, stack, target, attacker));
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    private static void drench(ServerLevel level, ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Balance.SICKLE_DRENCH_TICKS, 0), attacker);
        if (target.isOnFire()) {
            target.extinguishFire();
        }
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.2);
        level.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 0.3, c.z, 5, 0.3, 0.2, 0.3, 0.0);
        Sfx.varied(target, SoundEvents.GENERIC_SPLASH, 0.45F, 1.3F, 0.15F);
        if (attacker.isInWaterOrRain()) {
            // the tide is with you: a heavier crash of water and a charge for the sickle
            level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 8, 0.35, 0.35, 0.35, 0.05);
            Sfx.varied(target, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.5F, 1.2F, 0.1F);
            if (attacker instanceof Player player) {
                setTide(player, stack, tide(stack) + 1);
            }
        }
    }

    // ------------------------------------------------------------------ passive: the rising tide

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof Player player) || level.getGameTime() % Balance.SICKLE_TIDE_WATER_TICKS != 0
                || tide(stack) >= Balance.SICKLE_MAX_TIDE || !player.isInWater() || !enabled()) {
            return;
        }
        if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
            setTide(player, stack, tide(stack) + 1);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
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
        tooltip.add(Component.translatable("item.lacuna.tidecaller_sickle.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.lacuna.tidecaller_sickle.wave").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.lacuna.tidecaller_sickle.wave_detail", (int) Balance.SICKLE_WAVE_RANGE,
                (int) Balance.SICKLE_WAVE_DAMAGE).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.tidecaller_sickle.maelstrom", Balance.SICKLE_MAX_TIDE)
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.lacuna.tidecaller_sickle.maelstrom_detail",
                Balance.SICKLE_MAELSTROM_TICKS / 20).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lacuna.tidecaller_sickle.melee", (int) Balance.SICKLE_TIDE_BONUS)
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.lacuna.tidecaller_sickle.charges").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("lacuna.sickle.tide", tide(stack), Balance.SICKLE_MAX_TIDE).withStyle(ChatFormatting.AQUA)
                .append(" ").append(pips(tide(stack))));
    }
}
