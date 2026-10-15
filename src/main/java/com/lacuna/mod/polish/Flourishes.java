package com.lacuna.mod.polish;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.registry.ModBlocks;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3f;

/**
 * Small server-side flourishes that make the mod's materials feel alive: ores and crystals ring and sparkle as they
 * break, coins jingle when picked up, rare Lacuna items chime, ruby blades leave red glints. Purely cosmetic and
 * fail-safe ({@link SafeRun}).
 */
public final class Flourishes {
    public static final String FEATURE = "flourishes";
    private static final DustParticleOptions RUBY_DUST = new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.24F), 1.0F);
    private static final DustParticleOptions ZENITH_DUST = new DustParticleOptions(new Vector3f(0.45F, 0.9F, 1.0F), 0.8F);

    private Flourishes() {}

    public static void register(IEventBus bus) {
        bus.addListener(Flourishes::onBreak);
        bus.addListener(Flourishes::onPickup);
        bus.addListener(Flourishes::onHurt);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        SafeRun.run(FEATURE, () -> ring(level, event.getPos(), event.getState()));
    }

    private static void ring(ServerLevel level, BlockPos pos, BlockState state) {
        Vec3 c = Vec3.atCenterOf(pos);
        if (state.is(ModBlocks.RUBY_ORE.get()) || state.is(ModBlocks.DEEPSLATE_RUBY_ORE.get())) {
            level.sendParticles(RUBY_DUST, c.x, c.y, c.z, 16, 0.35, 0.35, 0.35, 0.02);
            level.sendParticles(ModParticles.CRIMSON_SPARK.get(), c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0.08);
            Sfx.at(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8F, 1.35F);
            Sfx.at(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        } else if (state.is(ModBlocks.UMBRAL_STEEL_ORE.get()) || state.is(ModBlocks.DEEPSLATE_UMBRAL_STEEL_ORE.get())) {
            level.sendParticles(ModParticles.UMBRAL_WISP.get(), c.x, c.y, c.z, 12, 0.35, 0.35, 0.35, 0.02);
            level.sendParticles(ModParticles.UMBRAL_SHARD.get(), c.x, c.y, c.z, 5, 0.25, 0.25, 0.25, 0.1);
            Sfx.at(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.55F);
            Sfx.at(level, c, SoundEvents.SCULK_CLICKING, 0.6F, 0.7F);
        } else if (state.is(ModBlocks.ZENITHITE_ORE.get()) || state.is(ModBlocks.DEEPSLATE_ZENITHITE_ORE.get())) {
            level.sendParticles(ZENITH_DUST, c.x, c.y, c.z, 14, 0.35, 0.35, 0.35, 0.02);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.15);
            Sfx.at(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.95F);
            Sfx.at(level, c, SoundEvents.BEACON_POWER_SELECT, 0.25F, 2.0F);
        } else if (state.is(ModBlocks.DIVINE_GLASS_CLUSTER.get()) || state.is(ModBlocks.DIVINE_GLASS.get())
                || state.is(ModBlocks.DIVINE_GLASS_PANE.get())) {
            level.sendParticles(ModParticles.LIGHT_MOTE.get(), c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.03);
            Sfx.at(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.6F);
            Sfx.at(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 2.0F);
        }
    }

    private static void onPickup(ItemEntityPickupEvent.Post event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getOriginalStack();
        if (player.level().isClientSide() || stack.isEmpty() || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            if (stack.is(ModItems.VILLAGE_COIN.get())) {
                // a little purse jingle, brighter for bigger handfuls
                float pitch = 1.55F + Math.min(0.4F, stack.getCount() * 0.03F) + player.getRandom().nextFloat() * 0.1F;
                Sfx.at(player, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.55F, pitch);
                Sfx.at(player, SoundEvents.CHAIN_STEP, 0.35F, 1.9F);
            } else if (Lacuna.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                    && stack.getRarity().ordinal() >= Rarity.RARE.ordinal()) {
                Sfx.at(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.7F);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ModParticles.LIGHT_MOTE.get(), player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                }
            }
        });
    }

    private static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getSource().getDirectEntity() instanceof Player player) || event.getNewDamage() <= 0
                || !(player.level() instanceof ServerLevel level) || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.RUBY_SWORD.get()) && !held.is(ModItems.RUBY_AXE.get())) {
            return;
        }
        LivingEntity target = event.getEntity();
        SafeRun.run(FEATURE, () -> {
            level.sendParticles(RUBY_DUST, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8,
                    target.getBbWidth() * 0.4, target.getBbHeight() * 0.25, target.getBbWidth() * 0.4, 0.02);
            Sfx.varied(target, SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 1.45F, 0.1F);
        });
    }
}
