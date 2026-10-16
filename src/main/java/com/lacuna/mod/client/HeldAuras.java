package com.lacuna.mod.client;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.eclipse.EclipserItem;
import com.lacuna.mod.gear.TidecallerSickleItem;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Faint auras around the mod's special items while they are held (and Starlace while it is worn), drawn by every
 * client for every nearby player: rift motes around the Totem, sun glints on Sun's Fury, violet wisps from a lit Null
 * Lantern, crimson sparks that grow with the Eclipser's charge, drips from the Tidecaller... Respects the particle
 * setting; in first person the motes sit in front of the held item, out of the way.
 */
public final class HeldAuras {
    /** Particle and chance per tick for one held item; {@code null} particle = decided by {@link #special}. */
    private record Aura(Supplier<ParticleOptions> particle, float chance, double rise) {}

    private static final Map<Item, Aura> AURAS = new IdentityHashMap<>();
    private static boolean built;

    private HeldAuras() {}

    private static void build() {
        built = true;
        put(ModItems.TOTEM_OF_BANISHING.get(), () -> ParticleTypes.REVERSE_PORTAL, 0.22F, 0.0);
        put(ModItems.SUNS_FURY.get(), () -> ModParticles.SOLAR_GLINT.get(), 0.12F, 0.01);
        put(ModItems.DIVINE_MAGNIFYING_GLASS.get(), () -> ModParticles.LIGHT_MOTE.get(), 0.07F, 0.01);
        put(ModItems.GRAVEMAW_GREATSWORD.get(), () -> ModParticles.UMBRAL_WISP.get(), 0.12F, 0.02);
        put(ModItems.UMBRAL_STEEL_SWORD.get(), () -> ModParticles.UMBRAL_WISP.get(), 0.05F, 0.015);
        put(ModItems.UMBRAL_STEEL_AXE.get(), () -> ModParticles.UMBRAL_WISP.get(), 0.05F, 0.015);
        put(ModItems.ECHO_DAGGER.get(), () -> ParticleTypes.SCULK_CHARGE_POP, 0.07F, 0.0);
        put(ModItems.PYRRHIC_SCEPTER.get(), () -> ParticleTypes.SMALL_FLAME, 0.1F, 0.01);
        put(ModItems.CHRONOSHARD_POCKET_WATCH.get(), () -> ModParticles.LIGHT_MOTE.get(), 0.05F, 0.005);
        put(ModItems.MIRROR_OF_RECURSION.get(), () -> ModParticles.LIGHT_MOTE.get(), 0.05F, 0.0);
        put(ModItems.WHISPERBONE_FLUTE.get(), () -> ParticleTypes.SOUL, 0.03F, 0.02);
        put(ModItems.HEARTWOOD_BOW.get(), () -> ParticleTypes.HAPPY_VILLAGER, 0.03F, 0.0);
        put(ModItems.EMBER.get(), () -> ParticleTypes.SMALL_FLAME, 0.04F, 0.02);
        put(ModItems.SOLAR_FLARE.get(), () -> ModParticles.SOLAR_GLINT.get(), 0.08F, 0.01);
        put(ModItems.ZENITHITE_SHARD.get(), () -> ParticleTypes.ELECTRIC_SPARK, 0.03F, 0.0);
        // items added by other features are looked up by id, so this list never depends on them existing
        putById("ember_triangle", () -> ParticleTypes.SMALL_FLAME, 0.14F, 0.03);
        putById("quickening_wand", () -> ParticleTypes.HAPPY_VILLAGER, 0.06F, 0.01);
    }

    private static void put(Item item, Supplier<ParticleOptions> particle, float chance, double rise) {
        AURAS.put(item, new Aura(particle, chance, rise));
    }

    private static void putById(String path, Supplier<ParticleOptions> particle, float chance, double rise) {
        Item item = BuiltInRegistries.ITEM.get(Lacuna.id(path));
        if (item != net.minecraft.world.item.Items.AIR) {
            put(item, particle, chance, rise);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || mc.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        if (!built) {
            build();
        }
        float budget = mc.options.particles().get() == ParticleStatus.DECREASED ? 0.5F : 1.0F;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        for (Player player : level.players()) {
            if (player.isSpectator() || player.isInvisible() || player.distanceToSqr(cam) > 32 * 32) {
                continue;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.isEmpty()) {
                    continue;
                }
                // in first person the motes gather at the held item in the screen corner: fewer and tighter
                boolean own = firstPerson(mc, player);
                float scale = own ? 0.35F : 1.0F;
                special(mc, level, player, hand, stack, budget * scale, rnd);
                Aura aura = AURAS.get(stack.getItem());
                if (aura != null && rnd.nextFloat() < aura.chance() * budget * scale) {
                    Vec3 at = handPos(mc, player, hand).add(jitter(rnd, own ? 0.04 : 0.12));
                    level.addParticle(aura.particle().get(), at.x, at.y, at.z, (rnd.nextDouble() - 0.5) * 0.01, aura.rise(),
                            (rnd.nextDouble() - 0.5) * 0.01);
                }
            }
            starlace(level, player, budget, rnd);
        }
    }

    /** Auras that depend on the item's state. */
    private static void special(Minecraft mc, ClientLevel level, Player player, InteractionHand hand, ItemStack stack, float budget,
                                RandomSource rnd) {
        if (stack.getItem() instanceof EclipserItem) {
            // the blade smoulders brighter as the charge builds
            float charge = EclipserItem.charge(stack) / 100.0F;
            if (charge > 0.05F && rnd.nextFloat() < (0.03F + 0.3F * charge) * budget) {
                Vec3 at = handPos(mc, player, hand).add(0, 0.15, 0).add(jitter(rnd, 0.18));
                level.addParticle(ModParticles.CRIMSON_SPARK.get(), at.x, at.y, at.z, 0.0, 0.02, 0.0);
            }
        } else if (stack.is(ModItems.NULL_LANTERN.get())) {
            boolean lit = stack.getDamageValue() < stack.getMaxDamage();
            if (lit && rnd.nextFloat() < 0.2F * budget) {
                Vec3 at = handPos(mc, player, hand).add(jitter(rnd, 0.1));
                level.addParticle(ModParticles.UMBRAL_WISP.get(), at.x, at.y, at.z, 0.0, 0.025, 0.0);
            }
        } else if (stack.getItem() instanceof TidecallerSickleItem) {
            if (rnd.nextFloat() < (0.06F + 0.05F * TidecallerSickleItem.tide(stack)) * budget) {
                Vec3 at = handPos(mc, player, hand).add(jitter(rnd, 0.1));
                level.addParticle(ParticleTypes.DRIPPING_WATER, at.x, at.y, at.z, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Starlace shimmers with tiny stars, more pieces and more at night. */
    private static void starlace(ClientLevel level, Player player, float budget, RandomSource rnd) {
        int pieces = com.lacuna.mod.gear.StarlaceSet.pieces(player);
        if (pieces == 0) {
            return;
        }
        boolean night = level.getDayTime() % 24000L > 13000L && level.getDayTime() % 24000L < 23000L;
        float chance = pieces * (night ? 0.06F : 0.02F) * (player.isSprinting() ? 2.0F : 1.0F) * budget;
        if (rnd.nextFloat() < chance) {
            double y = player.getY() + rnd.nextDouble() * player.getBbHeight();
            level.addParticle(ModParticles.LIGHT_MOTE.get(), player.getX() + (rnd.nextDouble() - 0.5) * 0.8, y,
                    player.getZ() + (rnd.nextDouble() - 0.5) * 0.8, 0.0, -0.005, 0.0);
        }
    }

    /** Where a held item roughly is: the hand for other players and third person, in front of the view in first person. */
    private static Vec3 handPos(Minecraft mc, Player player, InteractionHand hand) {
        boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        float side = right ? 1.0F : -1.0F;
        if (firstPerson(mc, player)) {
            // where the first-person item is drawn: low in the screen corner, a little over an arm's length away
            Vec3 look = player.getViewVector(1.0F);
            Vec3 rightVec = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 upVec = rightVec.cross(look).normalize();
            return player.getEyePosition().add(look.scale(1.1)).add(rightVec.scale(0.62 * side)).add(upVec.scale(-0.58));
        }
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 rightVec = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        double y = player.getY() + (player.isCrouching() ? 0.85 : 1.05);
        return new Vec3(player.getX(), y, player.getZ()).add(rightVec.scale(0.38 * side)).add(forward.scale(0.3));
    }

    private static boolean firstPerson(Minecraft mc, Player player) {
        return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    private static Vec3 jitter(RandomSource rnd, double r) {
        return new Vec3((rnd.nextDouble() - 0.5) * 2 * r, (rnd.nextDouble() - 0.5) * 2 * r, (rnd.nextDouble() - 0.5) * 2 * r);
    }
}
