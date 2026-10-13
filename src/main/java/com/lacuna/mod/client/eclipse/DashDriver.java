package com.lacuna.mod.client.eclipse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Moves the local player along an Eclipser dash sent by the server: every tick its velocity is set to reach the next
 * point of the path (closed loop, so lag or a small obstacle never accumulates error), movement input is ignored,
 * and normal collision still applies (the path itself was checked on the server). Right after the final step the
 * dash momentum is dropped so the player stops on the end point. The server finishes the dash for a client that
 * never moves.
 */
final class DashDriver {
    private static boolean active;
    private static Vec3 start = Vec3.ZERO;
    private static Vec3 end = Vec3.ZERO;
    private static Vec3 dir = Vec3.ZERO;
    private static int ticks;
    private static int age;
    private static ClientLevel level;
    /** Ticks since the last dash started (for the FOV punch), or a large value when idle. */
    private static int sinceStart = 1000;
    private static int lastTicks = 6;

    private DashDriver() {}

    static void start(Vec3 a, Vec3 b, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        start = a;
        end = b;
        Vec3 d = b.subtract(a);
        dir = d.lengthSqr() > 1.0E-6 ? d.normalize() : Vec3.ZERO;
        ticks = Math.max(1, duration);
        lastTicks = ticks;
        age = 0;
        level = mc.level;
        active = true;
        sinceStart = 0;
    }

    static boolean active() {
        return active;
    }

    static void stop() {
        active = false;
        level = null;
    }

    /** 0..1 strength of the dash FOV punch: snaps in, holds while moving, eases out. */
    static float punch(float partial) {
        float t = sinceStart + partial;
        float rise = Mth.clamp(t / 1.5F, 0.0F, 1.0F);
        float fall = 1.0F - Mth.clamp((t - lastTicks) / 9.0F, 0.0F, 1.0F);
        return rise * fall * fall;
    }

    static void clientTick() {
        if (sinceStart < 1000) {
            sinceStart++;
        }
    }

    private static LocalPlayer driven(Object entity) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player != null && entity == player ? player : null;
    }

    /** Before the player moves: aim this tick's velocity at the next point of the path. */
    static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        LocalPlayer player = driven(event.getEntity());
        if (!active || player == null) {
            return;
        }
        if (Minecraft.getInstance().level != level || !player.isAlive()) {
            stop();
            return;
        }
        if (player.isSpectator() || player.isPassenger()) {
            player.setDeltaMovement(dir.scale(0.06));
            stop();
            return;
        }
        age++;
        player.resetFallDistance();
        Vec3 target = start.add(end.subtract(start).scale(Math.min(1.0, age / (double) ticks)));
        player.setDeltaMovement(target.subtract(player.position()));
    }

    /** After the final step: drop the dash momentum at once so the player stops on the end point. */
    static void onPlayerTickPost(PlayerTickEvent.Post event) {
        LocalPlayer player = driven(event.getEntity());
        if (!active || player == null || age < ticks) {
            return;
        }
        player.setDeltaMovement(dir.scale(0.06));
        player.resetFallDistance();
        stop();
    }

    /** No steering, jumping or sneaking while the dash carries the player. */
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active || event.getEntity() != Minecraft.getInstance().player) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    static void reset() {
        stop();
        sinceStart = 1000;
    }
}
