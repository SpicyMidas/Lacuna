package com.lacuna.mod.client.eclipse;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.network.PossessInputPayload;
import com.lacuna.mod.network.PossessSyncPayload;
import com.lacuna.mod.registry.ModEffects;
import com.lacuna.mod.util.SafeRun;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of Shadow Possession ({@code eclipse/Possession}), for both roles:
 * <ul>
 *   <li><b>Shadow walker</b>: a click (attack or use) on a creature or player within reach asks the server to
 *       possess it instead of the vanilla spectate. While possessing, the movement keys, look, jump and sprint go to
 *       the server every tick (read straight from the key mappings: the own client player may sit in unloaded chunks),
 *       clicks make the host strike, sneak leaves, and the host's view is turned to the shadow's own look every frame
 *       so the camera (and the crosshair) answer the mouse at once instead of waiting for the server.</li>
 *   <li><b>Possessed player</b>: the shadow's keys are blended into its own (its own only add a nudge on top), its view
 *       is pulled back to where the shadow looks, its own clicks are refused, and whatever it does against the shadow
 *       (moving against it, looking away, clicking, jumping, sneaking) is reported as struggle.</li>
 * </ul>
 * The screen side is {@link PossessionOverlay}. State times out on its own if the server stops syncing.
 */
public final class PossessionClient {
    public static final String FEATURE = "shadow_possession_client";

    // the shadow (possessor)
    static int hostId = -1;
    static float hold;
    static float countdown = -1.0F;
    static boolean struggling;
    static int possessTicks;
    private static int sinceSync;
    private static boolean sneakHeld;

    // the possessed player (host)
    static boolean possessed;
    static float hostHold;
    static float hostCountdown = -1.0F;
    private static float forcedForward;
    private static float forcedStrafe;
    private static float forcedYaw;
    private static float forcedPitch;
    private static boolean forcedJump;
    private static boolean forcedSprint;
    private static float ownWeight = Balance.POSSESS_PLAYER_OWN_INPUT;
    private static int hostSinceSync;
    private static float struggle;
    private static int struggleTimer;

    private PossessionClient() {}

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(PossessionClient::onRegisterLayers);
        forgeBus.addListener(PossessionClient::onClientTick);
        forgeBus.addListener(PossessionClient::onRenderFrame);
        forgeBus.addListener(EventPriority.HIGH, PossessionClient::onClick);
        forgeBus.addListener(EventPriority.LOW, PossessionClient::onMovementInput);
        forgeBus.addListener(PossessionClient::onLogout);
    }

    private static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, Lacuna.id("shadow_possession"),
                (g, delta) -> SafeRun.run(FEATURE, () -> PossessionOverlay.render(g, delta)));
    }

    /** The local player is a shadow inside a host. */
    public static boolean possessing() {
        return hostId >= 0;
    }

    @Nullable
    static Entity host(Minecraft mc) {
        return hostId < 0 || mc.level == null ? null : mc.level.getEntity(hostId);
    }

    public static void onSync(PossessSyncPayload p) {
        SafeRun.run(FEATURE, () -> sync(p), PossessionClient::reset);
    }

    private static void sync(PossessSyncPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean aboutMe = p.hostId() == mc.player.getId();
        switch (p.role()) {
            case PossessSyncPayload.POSSESSOR -> {
                if (hostId != p.hostId()) {
                    possessTicks = 0;
                    sneakHeld = true; // a sneak key still held from flying down must not leave at once
                    ScreenFx.flash(0x1A0630, 0.55F, 12);
                    PossessionOverlay.onEnter();
                }
                hostId = p.hostId();
                hold = p.hold();
                countdown = p.countdown();
                struggling = p.has(PossessSyncPayload.STRUGGLING);
                sinceSync = 0;
            }
            case PossessSyncPayload.HOST -> {
                if (!aboutMe) {
                    return;
                }
                if (!possessed) {
                    ScreenFx.flash(0x12041F, 0.5F, 14);
                    ScreenFx.shake(1.2F, 8);
                    PossessionOverlay.onEnter();
                }
                possessed = true;
                forcedForward = p.forward();
                forcedStrafe = p.strafe();
                forcedYaw = p.yaw();
                forcedPitch = p.pitch();
                forcedJump = p.has(PossessSyncPayload.JUMP);
                forcedSprint = p.has(PossessSyncPayload.SPRINT);
                ownWeight = Mth.clamp(p.control(), 0.0F, 1.0F);
                hostHold = p.hold();
                hostCountdown = p.countdown();
                hostSinceSync = 0;
            }
            default -> {
                boolean expelled = p.has(PossessSyncPayload.EXPELLED);
                if (aboutMe && possessed) {
                    possessed = false;
                    ScreenFx.flash(expelled ? 0x3A0A2A : 0x12041F, 0.35F, 10);
                } else if (hostId >= 0 && hostId == p.hostId()) {
                    hostId = -1;
                    if (expelled) {
                        ScreenFx.shake(2.4F, 12);
                        ScreenFx.flash(0x2A0610, 0.45F, 12);
                    } else {
                        ScreenFx.flash(0x000000, 0.25F, 8);
                    }
                }
            }
        }
    }

    static void reset() {
        hostId = -1;
        possessed = false;
        struggle = 0.0F;
        PossessionOverlay.reset();
    }

    private static void send(PossessInputPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    // ------------------------------------------------------------------------------------------ tick

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        SafeRun.run(FEATURE, () -> {
            if (hostId >= 0 && ++sinceSync > 20) {
                hostId = -1; // the server stopped telling us: we are back in our own body
            }
            if (possessed && ++hostSinceSync > 20) {
                possessed = false;
            }
            if (hostId >= 0) {
                possessorTick(mc);
            }
            if (possessed) {
                hostTick(mc);
            }
            PossessionOverlay.tick(mc);
        }, PossessionClient::reset);
    }

    private static float axis(KeyMapping plus, KeyMapping minus) {
        return (plus.isDown() ? 1.0F : 0.0F) - (minus.isDown() ? 1.0F : 0.0F);
    }

    private static void possessorTick(Minecraft mc) {
        possessTicks++;
        Options o = mc.options;
        boolean free = mc.screen == null;
        float forward = free ? axis(o.keyUp, o.keyDown) : 0.0F;
        float strafe = free ? axis(o.keyLeft, o.keyRight) : 0.0F;
        LocalPlayer player = mc.player;
        send(PossessInputPayload.input(forward, strafe, player.getYRot(), player.getXRot(), free && o.keyJump.isDown(), free && o.keySprint.isDown()));
        boolean sneak = free && o.keyShift.isDown();
        if (sneak && !sneakHeld) {
            send(PossessInputPayload.release());
        }
        sneakHeld = sneak;
    }

    private static void hostTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        float dy = Math.abs(Mth.wrapDegrees(forcedYaw - player.getYRot()));
        float dp = Math.abs(forcedPitch - player.getXRot());
        if (dy > 20.0F || dp > 20.0F) {
            struggle += 0.6F; // looking away from where the shadow wants to look
        }
        if (++struggleTimer >= 2) {
            struggleTimer = 0;
            if (struggle > 0.0F) {
                send(PossessInputPayload.struggle(struggle));
                struggle = 0.0F;
            }
        }
    }

    // ------------------------------------------------------------------------------------------ input

    /** The shadow's keys win over a possessed player's own (which add a nudge on top). */
    private static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!possessed || event.getEntity() != mc.player) {
            return;
        }
        LocalPlayer player = mc.player;
        Input in = event.getInput();
        float ownF = in.forwardImpulse;
        float ownS = in.leftImpulse;
        // the shadow's keys are relative to its look; turn them into this player's frame (they may differ while it resists)
        float delta = (forcedYaw - player.getYRot()) * Mth.DEG_TO_RAD;
        float c = Mth.cos(delta);
        float s = Mth.sin(delta);
        float fx = forcedStrafe * c - forcedForward * s;
        float fz = forcedForward * c + forcedStrafe * s;
        in.leftImpulse = Mth.clamp(fx + ownWeight * ownS, -1.0F, 1.0F);
        in.forwardImpulse = Mth.clamp(fz + ownWeight * ownF, -1.0F, 1.0F);
        float ownLen = Mth.sqrt(ownF * ownF + ownS * ownS);
        if (ownLen > 0.2F) {
            float forcedLen = Mth.sqrt(fx * fx + fz * fz);
            if (forcedLen < 0.1F || ownF * fz + ownS * fx < 0.5F * ownLen * forcedLen) {
                struggle += 1.0F; // moving against the shadow
            }
        }
        if (in.jumping && !forcedJump || in.shiftKeyDown) {
            struggle += 0.4F;
        }
        in.jumping = forcedJump;
        in.shiftKeyDown = false;
        if (forcedSprint && in.forwardImpulse > 0.8F) {
            player.setSprinting(true);
        }
    }

    private static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() && !event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        if (possessed) {
            // a possessed player's hands are not its own: the attempt is a struggle
            event.setCanceled(true);
            event.setSwingHand(true);
            struggle += Balance.POSSESS_ACTION_STRUGGLE;
            return;
        }
        if (hostId >= 0) {
            event.setCanceled(true);
            event.setSwingHand(false);
            int hint = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
            send(PossessInputPayload.action(hint, event.isUseItem(), player.getYRot(), player.getXRot()));
            return;
        }
        if (player.isSpectator() && player.hasEffect(ModEffects.SHADOW_WALK) && mc.getCameraEntity() == player) {
            Entity target = aimed(mc, player);
            if (target != null) {
                event.setCanceled(true);
                event.setSwingHand(false);
                send(PossessInputPayload.request(target.getId(), event.isUseItem()));
            }
        }
    }

    /** The living body under the crosshair within possession reach (through walls: shadows pass them). */
    @Nullable
    private static Entity aimed(Minecraft mc, LocalPlayer player) {
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity) {
            return hit.getEntity();
        }
        double reach = Balance.POSSESS_REACH;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable() && (e instanceof LivingEntity || e instanceof PartEntity<?>), reach * reach);
        return hit == null ? null : hit.getEntity();
    }

    // ------------------------------------------------------------------------------------------ view

    private static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        SafeRun.run(FEATURE, () -> {
            if (hostId >= 0) {
                // the host looks where the shadow's mouse looks, this very frame (camera and crosshair follow)
                Entity host = host(mc);
                if (host != null && mc.getCameraEntity() == host) {
                    float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
                    float yaw = Mth.lerp(pt, player.yRotO, player.getYRot());
                    float pitch = Mth.lerp(pt, player.xRotO, player.getXRot());
                    host.setYRot(yaw);
                    host.yRotO = yaw;
                    host.setXRot(pitch);
                    host.xRotO = pitch;
                    if (host instanceof LivingEntity living) {
                        living.setYHeadRot(yaw);
                        living.yHeadRotO = yaw;
                    }
                }
            }
            if (possessed && !mc.isPaused()) {
                // a possessed player's view is pulled back to where the shadow looks (frame-rate independent)
                float dt = Math.min(event.getPartialTick().getGameTimeDeltaTicks(), 5.0F);
                float k = 1.0F - (float) Math.pow(1.0F - Balance.POSSESS_LOOK_PULL, dt);
                float dy = Mth.wrapDegrees(forcedYaw - player.getYRot()) * k;
                float dp = (forcedPitch - player.getXRot()) * k;
                player.turn(dy / 0.15, dp / 0.15);
            }
        }, PossessionClient::reset);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
