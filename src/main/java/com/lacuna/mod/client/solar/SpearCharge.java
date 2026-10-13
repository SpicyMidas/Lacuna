package com.lacuna.mod.client.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.FxRenderContext;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.registry.ModItems;
import com.lacuna.mod.registry.ModParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Drawing back Sun's Fury: sunlight gathers on the spear head — a glow and a starburst that swell with the draw and
 * flare when fully charged, with glints and motes. In first person the head is found exactly on the drawn model
 * ({@link HandPose}); for other players (and third person) it sits over the throwing shoulder, matching the
 * {@code suns_fury_throwing} model's raised pose (tip 35 degrees up, forward).
 */
public final class SpearCharge {
    /** Sprite pixel of the spear head (the 16 px texture's top-right tip). */
    private static final float TIP_X = 14.5F;
    private static final float TIP_Y = 1.5F;

    private SpearCharge() {}

    static boolean drawing(Player player) {
        return player.isUsingItem() && player.getUseItem().is(ModItems.SUNS_FURY.get());
    }

    /** Draw progress 0..1 (full at {@link Balance#SUNS_FURY_FULL_CHARGE}). */
    static float charge(Player player, float pt) {
        return (float) SolarMath.clamp01((player.getTicksUsingItem() + pt) / Balance.SUNS_FURY_FULL_CHARGE);
    }

    static boolean firstPerson(Minecraft mc, Player player) {
        return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    /** Spear head held over the shoulder (third person, other players): solved from the throwing model's pose. */
    static Vec3 raisedTip(Player player, float pt) {
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
        Vec3 forward = new Vec3(-Mth.sin(bodyYaw), 0.0, Mth.cos(bodyYaw));
        double height = player.isCrouching() ? 2.42 : 2.7;
        return player.getPosition(pt).add(0.0, height, 0.0).add(forward.scale(0.62)).add(right.scale(0.28 * HandPose.side(player)));
    }

    static Vec3 tip(Minecraft mc, Player player, Camera camera, float pt) {
        if (firstPerson(mc, player) && player instanceof LocalPlayer local) {
            return HandPose.spearPixel(local, camera, pt, TIP_X, TIP_Y);
        }
        return raisedTip(player, pt);
    }

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        for (Player player : level.players()) {
            if (!drawing(player) || player.position().distanceToSqr(cam) > 96.0 * 96.0) {
                continue;
            }
            float charge = charge(player, 0.0F);
            boolean first = firstPerson(mc, player);
            if (first) {
                continue; // motes right in front of the eyes would only blur the view
            }
            Vec3 tip = raisedTip(player, 0.0F);
            int every = charge >= 1.0F ? 2 : 4;
            if (player.tickCount % every == 0) {
                level.addParticle(ModParticles.SOLAR_GLINT.get(), tip.x + (random.nextDouble() - 0.5) * 0.2,
                        tip.y + (random.nextDouble() - 0.5) * 0.2, tip.z + (random.nextDouble() - 0.5) * 0.2, 0.0, 0.01, 0.0);
            }
            if (charge >= 1.0F && random.nextFloat() < 0.5F) {
                double a = random.nextDouble() * Math.PI * 2.0;
                level.addParticle(ModParticles.LIGHT_MOTE.get(), tip.x + Math.cos(a) * 0.7, tip.y - 0.2, tip.z + Math.sin(a) * 0.7,
                        -Math.cos(a) * 0.035, 0.02, -Math.sin(a) * 0.035);
            }
        }
    }

    static void render(FxRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        float pt = ctx.partialTick();
        VertexConsumer glow = null;
        VertexConsumer flare = null;
        for (Player player : ctx.level().players()) {
            if (!drawing(player) || player.position().distanceToSqr(ctx.cam()) > 96.0 * 96.0) {
                continue;
            }
            float charge = charge(player, pt);
            boolean first = firstPerson(mc, player);
            Vec3 tip = tip(mc, player, ctx.camera(), pt);
            float t = ctx.time(player.tickCount);
            float full = charge >= 1.0F ? 1.0F + 0.12F * Mth.sin(t * 0.9F) : charge;
            // first person: the head is ~1.5 blocks from the eye, keep the light small there
            float size = (first ? 0.09F : 0.4F) * (0.5F + 0.7F * full);
            if (glow == null) {
                glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
            }
            FxDraw.billboard(ctx.pose().last(), glow, ctx.camera(), ctx.rel(tip), size, t * 0.04F,
                    FxDraw.argb(0.4F + 0.6F * Math.min(1.0F, full), 0xFFE2A0));
            if (charge >= 1.0F) {
                if (flare == null) {
                    flare = ctx.buffers().getBuffer(SolarRenderTypes.flare());
                }
                FxDraw.billboard(ctx.pose().last(), flare, ctx.camera(), ctx.rel(tip), size * 3.2F, -t * 0.03F,
                        FxDraw.argb(0.55F + 0.1F * Mth.sin(t * 0.7F), SolarVisuals.GOLD));
            }
        }
    }
}
