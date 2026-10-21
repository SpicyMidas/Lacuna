package com.lacuna.mod.banish;

import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a banishment looks and sounds like, server side. Every crossing to or from the Forgotten Land (the totem used on
 * oneself, a struck target, a Forgotten Portal, the {@code /lacuna banish} command, the timed return) goes through
 * {@link BanishmentManager}, which calls these:
 * <ul>
 *   <li>{@link #travel}: both ends of every crossing. Lacuna's own particles in the totem's palette (rift sparks and
 *       stone chips, {@code client/fx/BanishParticles}) and a bone-coloured rift; no vanilla End or portal particles.</li>
 *   <li>{@link #arrival}: whoever lands in the Forgotten Land gets the Totem of Banishing's activation, like a totem of
 *       undying going off ({@link #ARRIVAL_FX}, drawn by {@code client/banish/BanishArrivalFx}): the totem pops up on the
 *       arriving player's screen once the world is visible again, and everyone nearby sees the burst of stone chips
 *       and hears it.</li>
 * </ul>
 */
public final class BanishFx {
    /**
     * FxPayload kind: the totem goes off for the entity {@code entityId} that has just arrived at {@code a}. {@code p0}
     * is 1 on the arriving player's own copy (its client shows the item activation), 0 for onlookers.
     */
    public static final String ARRIVAL_FX = "banish_totem";
    /** The rift's colour: the bone of the totem's stone hand (it used to be an End-like pale blue). */
    public static final int RIFT_ARGB = 0xFFE4DCC8;

    private BanishFx() {}

    /** One end of a crossing, at the traveller's feet. */
    static void travel(ServerLevel level, Vec3 pos) {
        level.sendParticles(ModParticles.RIFT_SPARK.get(), pos.x, pos.y + 1.0, pos.z, 26, 0.5, 0.9, 0.5, 0.04);
        level.sendParticles(ModParticles.BANISH_MOTE.get(), pos.x, pos.y + 1.0, pos.z, 18, 0.35, 0.7, 0.35, 0.18);
        LacunaFx.send(level, pos, FxPayload.at("rift", pos).withColor(RIFT_ARGB));
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 0.6F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.15F, 1.4F);
    }

    /**
     * The totem goes off for {@code moved}, just arrived in the Forgotten Land. Sent to the players near the arrival
     * point (onlookers) and on its own to the arriving player; each client plays the layered totem sound itself, in
     * step with what it shows (the arriving player's pop-in waits until the loading screen or the portal's void lifts).
     */
    static void arrival(ServerLevel level, Entity moved) {
        Vec3 pos = moved.position();
        FxPayload fx = FxPayload.at(ARRIVAL_FX, pos).withEntity(moved.getId()).withColor(RIFT_ARGB);
        ServerPlayer self = moved instanceof ServerPlayer player ? player : null;
        PacketDistributor.sendToPlayersNear(level, self, pos.x, pos.y, pos.z, LacunaFx.NEAR, fx);
        if (self != null) {
            LacunaFx.sendTo(self, fx.withParams(1.0F, 0.0F, 0.0F));
        }
    }
}
