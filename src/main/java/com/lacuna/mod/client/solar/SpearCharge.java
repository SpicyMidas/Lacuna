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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Drawing back Sun's Fury: sunlight gathers on the spear head — a glow that swells with the draw and flares when
 * fully charged, with glints and motes. The head position matches the drawn-back hand pose of the
 * {@code suns_fury_throwing} model (first person: held low on the throwing side; otherwise raised over the shoulder).
 */
public final class SpearCharge {
    private SpearCharge() {}

    static boolean drawing(Player player) {
        return player.isUsingItem() && player.getUseItem().is(ModItems.SUNS_FURY.get());
    }

    private static int side(Player player) {
        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    /** Draw progress 0..1 (full at {@link Balance#SUNS_FURY_FULL_CHARGE}). */
    static float charge(Player player, float pt) {
        return (float) SolarMath.clamp01((player.getTicksUsingItem() + pt) / Balance.SUNS_FURY_FULL_CHARGE);
    }

    static boolean firstPerson(Minecraft mc, Player player) {
        return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    /** Spear head in first person (camera space of the drawn-back model, as world position). */
    static Vec3 firstPersonTip(Player player, Camera camera) {
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        int side = side(player);
        return camera.getPosition()
                .add(look.x() * 1.5, look.y() * 1.5, look.z() * 1.5)
                .add(-left.x() * 0.17 * side, -left.y() * 0.17 * side, -left.z() * 0.17 * side)
                .add(-up.x() * 0.1, -up.y() * 0.1, -up.z() * 0.1);
    }

    /** Spear head held over the shoulder (third person, other players). */
    static Vec3 raisedTip(Player player, float pt) {
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
        Vec3 forward = new Vec3(-Mth.sin(bodyYaw), 0.0, Mth.cos(bodyYaw));
        double height = player.isCrouching() ? 2.2 : 2.45;
        return player.getPosition(pt).add(0.0, height, 0.0).add(forward.scale(0.62)).add(right.scale(0.38 * side(player)));
    }

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Player player : level.players()) {
            if (!drawing(player) || player.position().distanceToSqr(cam) > 96.0 * 96.0) {
                continue;
            }
            float charge = charge(player, 0.0F);
            boolean first = firstPerson(mc, player);
            Vec3 tip = first ? firstPersonTip(player, mc.gameRenderer.getMainCamera()) : raisedTip(player, 0.0F);
            int every = charge >= 1.0F ? 2 : 4;
            if (player.tickCount % every == 0) {
                double spread = first ? 0.08 : 0.2;
                level.addParticle(first ? ModParticles.LIGHT_MOTE.get() : ModParticles.SOLAR_GLINT.get(),
                        tip.x + (random.nextDouble() - 0.5) * spread, tip.y + (random.nextDouble() - 0.5) * spread,
                        tip.z + (random.nextDouble() - 0.5) * spread, 0.0, 0.01, 0.0);
            }
            if (!first && charge >= 1.0F && random.nextFloat() < 0.5F) {
                double a = random.nextDouble() * Math.PI * 2.0;
                level.addParticle(ModParticles.LIGHT_MOTE.get(), tip.x + Math.cos(a) * 0.6, tip.y - 0.2, tip.z + Math.sin(a) * 0.6,
                        -Math.cos(a) * 0.03, 0.02, -Math.sin(a) * 0.03);
            }
        }
    }

    static void render(FxRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        float pt = ctx.partialTick();
        VertexConsumer glow = null;
        for (Player player : ctx.level().players()) {
            if (!drawing(player) || player.position().distanceToSqr(ctx.cam()) > 96.0 * 96.0) {
                continue;
            }
            float charge = charge(player, pt);
            boolean first = firstPerson(mc, player);
            Vec3 tip = first ? firstPersonTip(player, ctx.camera()) : raisedTip(player, pt);
            float t = ctx.time(player.tickCount);
            float full = charge >= 1.0F ? 1.0F + 0.12F * Mth.sin(t * 0.9F) : charge;
            float size = (first ? 0.16F : 0.45F) * (0.4F + 0.8F * full);
            if (glow == null) {
                glow = ctx.buffers().getBuffer(FxRenderTypes.glowShader());
            }
            FxDraw.billboard(ctx.pose().last(), glow, ctx.camera(), ctx.rel(tip), size, t * 0.04F,
                    FxDraw.argb(0.35F + 0.6F * Math.min(1.0F, full), 0xFFE2A0));
        }
    }
}
