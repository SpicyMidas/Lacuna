package com.lacuna.mod.client.banish;

import com.lacuna.mod.banish.BanishFx;
import com.lacuna.mod.client.fx.ClientFx;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxManager;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Totem of Banishing going off for whoever lands in the Forgotten Land ({@link BanishFx#ARRIVAL_FX}), the way a
 * totem of undying goes off (vanilla entity event 35): the totem pops up big on the arriving player's screen
 * ({@code GameRenderer.displayItemActivation}), a tracking burst of the totem's stone chips ({@code banish_mote}) flies
 * off the body with a ring of rift sparks at the feet, and a layered totem sound plays: the totem pop pitched down
 * under an amethyst resonance and a soul's sigh.
 * <p>
 * The arriving player's own pop waits until the world can be seen again (the loading screen is gone and a Forgotten
 * Portal's void has lifted), so it is never spent behind them; onlookers see the burst as soon as the newcomer's entity
 * reaches their client (it is tracked a tick or two after it lands).
 */
public final class BanishArrivalFx {
    /** Never later than this (10 s), whatever covers the screen. */
    private static final int MAX_WAIT = 200;
    /** Ticks of clear view before the totem pops. */
    private static final int SETTLE = 3;
    /** Onlookers: how long to wait for the newcomer's entity before bursting at the arrival point instead. */
    private static final int ENTITY_WAIT = 10;

    private BanishArrivalFx() {}

    /** From {@code FxHandlers.bootstrap()}. */
    public static void register() {
        FxHandlers.register(BanishFx.ARRIVAL_FX, p -> FxManager.add(p.p0() > 0.5F ? new OwnPop() : new Onlooker(p)));
    }

    /** The arriving player's own totem. */
    private static final class OwnPop implements ClientFx {
        private int waited;
        private int clear;

        @Override
        public boolean tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                return false;
            }
            waited++;
            boolean visible = !(mc.screen instanceof ReceivingLevelScreen);
            clear = visible ? clear + 1 : 0;
            if (clear < SETTLE && waited < MAX_WAIT) {
                return true;
            }
            mc.gameRenderer.displayItemActivation(new ItemStack(ModItems.TOTEM_OF_BANISHING.get()));
            burst(mc.level, mc.player);
            return false;
        }

        @Override
        public void render(FxRenderContext ctx) {}
    }

    /** Someone else (a player or a banished creature) has landed nearby. */
    private static final class Onlooker implements ClientFx {
        private final int entityId;
        private final Vec3 at;
        private int age;

        Onlooker(FxPayload payload) {
            this.entityId = payload.entityId();
            this.at = payload.a();
        }

        @Override
        public boolean tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return false;
            }
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                burst(level, entity);
                return false;
            }
            if (++age < ENTITY_WAIT) {
                return true;
            }
            burstAt(level, at, 0.6, 1.8);
            return false;
        }

        @Override
        public void render(FxRenderContext ctx) {}
    }

    /** Vanilla's totem burst (a 30-tick tracking emitter) with the totem's stone chips, plus the spark ring and the sound. */
    private static void burst(ClientLevel level, Entity entity) {
        Minecraft.getInstance().particleEngine.createTrackingEmitter(entity, ModParticles.BANISH_MOTE.get(), 30);
        ring(level, entity.position(), Math.max(0.35, entity.getBbWidth() * 0.6), level.random);
        sound(level, entity.position());
    }

    /** The same burst without an entity to follow: chips flung from a box of the given size at the arrival point. */
    private static void burstAt(ClientLevel level, Vec3 at, double width, double height) {
        RandomSource r = level.random;
        for (int i = 0; i < 90; i++) {
            double dx = r.nextFloat() * 2.0F - 1.0F;
            double dy = r.nextFloat() * 2.0F - 1.0F;
            double dz = r.nextFloat() * 2.0F - 1.0F;
            if (dx * dx + dy * dy + dz * dz > 1.0) {
                continue;
            }
            level.addParticle(ModParticles.BANISH_MOTE.get(), at.x + dx * width * 0.5, at.y + height * (0.5 + dy / 4.0),
                    at.z + dz * width * 0.5, dx, dy + 0.2, dz);
        }
        ring(level, at, Math.max(0.35, width * 0.6), r);
        sound(level, at);
    }

    /** Rift sparks in a ring at the feet, flung outward and turning the way the chips pinwheel. */
    private static void ring(ClientLevel level, Vec3 feet, double radius, RandomSource r) {
        int n = 22;
        for (int i = 0; i < n; i++) {
            double a = (i + r.nextDouble() * 0.5) * Mth.TWO_PI / n;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double speed = 0.16 + r.nextDouble() * 0.08;
            level.addParticle(ModParticles.RIFT_SPARK.get(), feet.x + cos * radius, feet.y + 0.1 + r.nextDouble() * 0.15,
                    feet.z + sin * radius, cos * speed - sin * 0.08, 0.015 + r.nextDouble() * 0.02, sin * speed + cos * 0.08);
        }
    }

    /** Layered on this client so it sounds exactly when the totem shows: a deep totem pop, amethyst, a soul's sigh. */
    private static void sound(ClientLevel level, Vec3 at) {
        float jitter = (level.random.nextFloat() - 0.5F) * 0.06F;
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9F, 0.72F + jitter, false);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F, false);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.7F + jitter, false);
    }
}
