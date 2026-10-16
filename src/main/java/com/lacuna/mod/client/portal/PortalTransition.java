package com.lacuna.mod.client.portal;

import com.lacuna.mod.banish.ForgottenPortalBlock;
import com.lacuna.mod.banish.ForgottenPortals;
import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Forgotten Portal crossing as the traveller sees it: a fast fade into a swirling grey-violet void that covers the
 * whole screen (HUD included) at the moment of the teleport, stays while the far side loads (the loading screen shows
 * the same void, {@link ForgottenVoidScreen}), holds a few ticks and lifts over 1.5 s on the other side, with a deep
 * whoosh as the new world arrives (the server tolls the bell).
 * <p>
 * The server crosses at the end of the tick the portal is touched and sends {@link ForgottenPortals#CROSSING_FX} right
 * before the transfer, which leaves no time to fade in. So the client starts the fade itself the moment the local
 * player touches the portal plane ({@link Phase#ANTICIPATE}, the same test as the server, after a cooldown that
 * mirrors the vanilla portal cooldown); the payload confirms it, and without one (crossing disabled, refused) the void
 * lifts again. Everything runs on client ticks and is interpolated with the partial tick. With Lacuna screen effects
 * turned off the void is a plain dark fade with a shorter tail.
 */
public final class PortalTransition {
    public static final String FEATURE = "portal_transition";
    /** The void's flat colour (fallback and simple mode): violet-black. */
    private static final int VOID_RGB = 0x0D0A14;

    private static final float ANTICIPATE_RATE = 0.45F; // per tick: two ticks to the anticipated peak
    private static final float ANTICIPATE_PEAK = 0.88F;
    private static final int ANTICIPATE_TIMEOUT = 16; // no word from the server: lift the void again
    private static final int CROSSING_TIMEOUT = 40; // confirmed, yet the far side never came
    private static final int HOLD = 6; // full void once the new terrain is in
    private static final int FADE = 30; // 1.5 s
    private static final int SIMPLE_FADE = 10;
    private static final int ABORT_FADE = 6;
    private static final int COOLDOWN = 12; // vanilla: 10 ticks for players, refreshed while standing in the portal

    private static final int MOTES = 36;

    private enum Phase { IDLE, ANTICIPATE, CROSSING, ARRIVED, FADING }

    private static Phase phase = Phase.IDLE;
    private static int phaseTicks;
    private static float cover;
    private static float prevCover;
    private static float fadeFrom;
    private static int fadeLength = FADE;
    /** Ticks since the transition began: the void's own clock (continuous across the dimension change). */
    private static int clock;
    private static boolean whooshed;
    @Nullable
    private static ClientLevel departed;
    private static int cooldown;

    private PortalTransition() {}

    /** From {@code FxHandlers.bootstrap()}. */
    public static void register() {
        FxHandlers.register(ForgottenPortals.CROSSING_FX, PortalTransition::onCrossing);
    }

    /** True while a transition is running (other screen flashes keep out of its way). */
    public static boolean active() {
        return phase != Phase.IDLE;
    }

    /** True while the void should fully cover the screen: the loading screen then shows it too. */
    public static boolean covering() {
        return phase == Phase.CROSSING || phase == Phase.ARRIVED;
    }

    private static boolean simple() {
        return !LacunaClientConfig.get(LacunaClientConfig.SCREEN_EFFECTS);
    }

    /** Vortex strength: the vanilla Distortion Effects slider calms it, never below a gentle turn. */
    private static float swirl() {
        return 0.35F + 0.65F * Mth.clamp(Minecraft.getInstance().options.screenEffectScale().get().floatValue(), 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------------------------------ state

    private static void onCrossing(FxPayload payload) {
        if (payload.p0() < 0.0F) {
            if (phase == Phase.ANTICIPATE || phase == Phase.CROSSING) {
                lift(ABORT_FADE);
            }
            return;
        }
        if (phase == Phase.IDLE) {
            clock = 0;
        }
        phase = Phase.CROSSING;
        phaseTicks = 0;
        whooshed = false;
        departed = Minecraft.getInstance().level;
        cover = Math.max(cover, ANTICIPATE_PEAK);
    }

    /** Lifts the void without a crossing (or after one), over {@code ticks}. */
    private static void lift(int ticks) {
        phase = Phase.FADING;
        phaseTicks = 0;
        fadeFrom = cover;
        fadeLength = Math.max(1, ticks);
        departed = null;
        cooldown = COOLDOWN;
    }

    private static void reset() {
        phase = Phase.IDLE;
        cover = 0.0F;
        prevCover = 0.0F;
        departed = null;
        cooldown = 0;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (phase != Phase.IDLE) {
                reset();
            }
            return;
        }
        if (mc.isPaused() || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        SafeRun.run(FEATURE, () -> tick(mc), PortalTransition::reset);
    }

    private static void tick(Minecraft mc) {
        prevCover = cover;
        clock++;
        phaseTicks++;
        boolean inside = touchingPortal(mc.player, mc.level);
        if (cooldown > 0) {
            cooldown = inside ? COOLDOWN : cooldown - 1;
        }
        switch (phase) {
            case IDLE -> {
                if (cooldown == 0 && inside && mayCross(mc.player, mc.level)) {
                    phase = Phase.ANTICIPATE;
                    phaseTicks = 0;
                    clock = 0;
                }
            }
            case ANTICIPATE -> {
                cover = Math.min(ANTICIPATE_PEAK, cover + ANTICIPATE_RATE);
                if (phaseTicks > ANTICIPATE_TIMEOUT) {
                    lift(ABORT_FADE);
                }
            }
            case CROSSING -> {
                cover = Math.min(1.0F, cover + 0.34F);
                boolean switched = mc.level != departed;
                boolean loading = mc.screen instanceof ReceivingLevelScreen;
                if (switched && !whooshed) {
                    whooshed = true; // after the level change, which stops every playing sound
                    whoosh(mc);
                }
                if (switched && !loading) {
                    phase = Phase.ARRIVED;
                    phaseTicks = 0;
                    cover = 1.0F;
                    departed = null;
                    cooldown = COOLDOWN;
                } else if (!switched && !loading && phaseTicks > CROSSING_TIMEOUT) {
                    lift(ABORT_FADE);
                }
            }
            case ARRIVED -> {
                cover = 1.0F;
                if (phaseTicks >= HOLD) {
                    lift(simple() ? SIMPLE_FADE : FADE);
                }
            }
            case FADING -> {
                float x = Math.min(1.0F, phaseTicks / (float) fadeLength);
                cover = fadeFrom * (1.0F - x * x * (3.0F - 2.0F * x));
                if (x >= 1.0F) {
                    phase = Phase.IDLE;
                    cover = 0.0F;
                }
            }
        }
    }

    /** The server's own conditions for a player (spectators, riders and mounts never cross; overworld or Forgotten Land). */
    private static boolean mayCross(LocalPlayer player, Level level) {
        return !player.isSpectator() && !player.isPassenger() && !player.isVehicle()
                && (level.dimension() == Level.OVERWORLD || ModDimensions.isForgottenLand(level));
    }

    /** True when the player's box reaches into the plane of a Forgotten Portal block (its entityInside test). */
    private static boolean touchingPortal(LocalPlayer player, Level level) {
        AABB box = player.getBoundingBox().deflate(1.0E-5);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ForgottenPortalBlock && ForgottenPortalBlock.touches(player, state, level, pos)) {
                return true;
            }
        }
        return false;
    }

    /** A deep wing-beat whoosh with the long portal sweep under it, pitched low (the bell comes from the server). */
    private static void whoosh(Minecraft mc) {
        mc.getSoundManager().play(heard(SoundEvents.ENDER_DRAGON_FLAP, 0.7F, 0.5F));
        mc.getSoundManager().play(heard(SoundEvents.PORTAL_TRAVEL, 0.14F, 0.55F));
    }

    /** A non-positional sound on the Players channel (like the bell, it follows the players volume slider). */
    private static SimpleSoundInstance heard(SoundEvent sound, float volume, float pitch) {
        return new SimpleSoundInstance(sound.getLocation(), SoundSource.PLAYERS, volume, pitch, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    // ------------------------------------------------------------------------------------------ drawing

    /** The GUI layer, registered above every other layer: the void covers the HUD too. */
    static void render(GuiGraphics g, DeltaTracker delta) {
        if (phase == Phase.IDLE || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            float partial = delta.getGameTimeDeltaPartialTick(false);
            float alpha = Mth.clamp(Mth.lerp(partial, prevCover, cover), 0.0F, 1.0F);
            if (alpha > 0.003F) {
                drawVoid(g, alpha, partial);
            }
        });
    }

    /**
     * Draws the void over the whole GUI at {@code alpha}: the portal field wound into a vortex (the portal surface's
     * own shader, so stepping in reads as falling into it) with motes streaming out of its heart; a plain violet-black
     * fill in simple mode or without the shader.
     */
    static void drawVoid(GuiGraphics g, float alpha, float partial) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float time = clock + partial;
        boolean simple = simple();
        float swirl = swirl();
        RenderType field = simple ? null : ForgottenPortalRenderTypes.voidOverlay(swirl, time / 24000.0F);
        if (field != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            g.fillRenderType(field, 0, 0, w, h, 0);
            g.flush();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            g.fill(0, 0, w, h, FxDraw.argb(alpha, VOID_RGB));
            g.flush();
        }
        if (!simple) {
            motes(g, w, h, alpha, time / 20.0F, swirl);
        }
    }

    /** Pale motes spiralling out of the vortex's heart and speeding up toward the edges: flying through the void. */
    private static void motes(GuiGraphics g, int w, int h, float alpha, float seconds, float swirl) {
        float cx = w * 0.5F;
        float cy = h * 0.5F;
        float reach = Mth.sqrt(cx * cx + cy * cy) * 1.1F;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        for (int i = 0; i < MOTES; i++) {
            float seed = frac(i * 0.61803398F + 0.137F);
            float speed = 0.38F + 0.32F * frac(seed * 7.13F);
            float life = frac(seed * 3.71F + seconds * speed);
            float dist = (0.04F + life * life) * reach;
            // the field turns counter-clockwise on screen (one turn in ~2 s at full swirl): the motes ride it
            float angle = seed * Mth.TWO_PI - seconds * Mth.PI * swirl - life * 2.2F * swirl;
            float x = cx + Mth.cos(angle) * dist;
            float y = cy + Mth.sin(angle) * dist;
            float size = 2.0F + life * (5.0F + 7.0F * frac(seed * 5.3F));
            float fade = Mth.sin(life * Mth.PI);
            float a = alpha * fade * fade * (0.4F + 0.5F * frac(seed * 9.1F));
            if (a < 0.01F) {
                continue;
            }
            boolean lavender = i % 3 == 0;
            RenderSystem.setShaderColor(lavender ? 0.78F : 0.92F, lavender ? 0.72F : 0.9F, 1.0F, a);
            g.pose().pushPose();
            g.pose().translate(x - size * 0.5F, y - size * 0.5F, 0.0F);
            g.pose().scale(size, size, 1.0F);
            g.blit(FxRenderTypes.GLOW, 0, 0, 0, 0.0F, 0.0F, 1, 1, 1, 1);
            g.pose().popPose();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }
}
