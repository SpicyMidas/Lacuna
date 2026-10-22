package com.lacuna.mod.banish;

import com.lacuna.mod.Balance;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.Sfx;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Totem of Banishing's knockback: a radial blast at both ends of a totem banishment (the spot the traveller is torn
 * from and the spot it lands on in the Forgotten Land) and the hard shove that throws a struck target back before it
 * vanishes. Only a {@link BanishmentManager.Cause#TOTEM} banishment blasts; portals and commands never do.
 * <p>
 * The blast pushes every living thing within {@value #RADIUS} blocks straight away from the centre, hardest close in,
 * with a lift that throws it about two blocks up (never high enough for fall damage on flat ground). It deals no
 * damage: the totem exiles, it does not wound. Spared: the traveller and the totem's user, their mounts and
 * passengers, teammates, armour stands, the user's own pets (anyone's pets and other players only with PvP on, never
 * creative or spectating players). Knockback resistance (netherite, heavy mobs) softens it as usual. Every blast sends
 * {@link #FX} ({@code client/banish/BanishBlastFx}: a shockwave ring on the ground in the exilite palette, chips and
 * sparks) and a heavy layered boom.
 */
public final class TotemBlast {
    /** FxPayload kind: a shockwave at {@code a}; {@code p0} = radius, {@code p1} = 1 on arrival (bigger, slower ring). */
    public static final String FX = "banish_blast";
    /** Blast reach, in blocks. */
    public static final double RADIUS = 6.0;
    /** Horizontal speed at the centre and at the edge (blocks per tick). */
    private static final double PUSH_NEAR = 2.3;
    private static final double PUSH_FAR = 0.7;
    /** Upward speed at the centre and at the edge: ~2 blocks of flight at most. */
    private static final double LIFT_NEAR = 0.62;
    private static final double LIFT_FAR = 0.3;
    /** The struck target: horizontal speed (times {@link Balance#TOTEM_KNOCKBACK}) and lift. */
    private static final double STRIKE_PUSH = 1.6;
    private static final double STRIKE_LIFT = 0.55;

    private TotemBlast() {}

    /**
     * Blasts everything near {@code at} away from it. {@code traveller} (the banished entity) and {@code owner} (the
     * totem's user, may be the traveller or null) are never pushed.
     */
    static void blast(ServerLevel level, Vec3 at, Entity traveller, @Nullable Entity owner, boolean arrival) {
        AABB box = new AABB(at, at).inflate(RADIUS, RADIUS * 0.6, RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> affects(e, traveller, owner))) {
            Vec3 d = new Vec3(e.getX() - at.x, 0.0, e.getZ() - at.z);
            double dist = d.length();
            if (dist > RADIUS) {
                continue;
            }
            double near = 1.0 - dist / RADIUS;
            Vec3 dir = dist < 1.0E-3 ? new Vec3(level.random.nextDouble() - 0.5, 0.0, level.random.nextDouble() - 0.5).normalize()
                    : d.scale(1.0 / dist);
            push(e, dir, PUSH_FAR + (PUSH_NEAR - PUSH_FAR) * near, LIFT_FAR + (LIFT_NEAR - LIFT_FAR) * near);
        }
        shockwave(level, at, arrival);
    }

    /** The struck target is thrown hard away from the striker (it vanishes mid-flight, {@code MELEE_DELAY} later). */
    static void strike(ServerPlayer attacker, LivingEntity target) {
        Vec3 d = new Vec3(target.getX() - attacker.getX(), 0.0, target.getZ() - attacker.getZ());
        Vec3 dir = d.lengthSqr() < 1.0E-6 ? attacker.getLookAngle().multiply(1.0, 0.0, 1.0).normalize() : d.normalize();
        push(target, dir, STRIKE_PUSH * Balance.TOTEM_KNOCKBACK, STRIKE_LIFT);
        ServerLevel level = attacker.serverLevel();
        Vec3 at = target.position();
        LacunaFx.send(level, at, FxPayload.at(FX, at).withParams(2.5F, 0.0F, 0.0F).withColor(BanishFx.RIFT_ARGB));
        level.sendParticles(ModParticles.BANISH_MOTE.get(), at.x, at.y + target.getBbHeight() * 0.5, at.z, 16, 0.3, 0.4, 0.3, 0.35);
        Sfx.at(level, at, SoundEvents.WARDEN_ATTACK_IMPACT, 1.0F, 0.7F);
        Sfx.at(level, at, SoundEvents.WIND_CHARGE_BURST, 0.9F, 0.75F);
        Sfx.at(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 0.55F);
    }

    /** Sets the speed (most of the old one is dropped), scaled by knockback resistance; synced to players too. */
    private static void push(LivingEntity e, Vec3 dir, double speed, double lift) {
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0.0) {
            return;
        }
        Vec3 old = e.getDeltaMovement();
        e.setDeltaMovement(old.x * 0.2 + dir.x * speed * resist, Math.max(old.y, lift * resist), old.z * 0.2 + dir.z * speed * resist);
        e.hasImpulse = true;
        e.hurtMarked = true; // ServerEntity sends the motion to the pushed player and to everyone tracking it
        e.resetFallDistance();
    }

    /** Who a blast may push (see the class comment). */
    private static boolean affects(LivingEntity e, Entity traveller, @Nullable Entity owner) {
        if (e == traveller || e == owner || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) {
            return false;
        }
        if (traveller.hasPassenger(e) || e.hasPassenger(traveller) || traveller.isPassengerOfSameVehicle(e)) {
            return false;
        }
        if (owner != null && (owner.hasPassenger(e) || e.hasPassenger(owner) || owner.isAlliedTo(e) || e.isAlliedTo(owner))) {
            return false;
        }
        MinecraftServer server = e.getServer();
        boolean pvp = server != null && server.isPvpAllowed();
        if (e instanceof Player other) {
            return pvp && !other.isCreative() && !(owner instanceof Player user && !user.canHarmPlayer(other));
        }
        if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            return pvp && (owner == null || !owner.getUUID().equals(pet.getOwnerUUID()))
                    && !(owner != null && pet.getOwner() instanceof LivingEntity petOwner && owner.isAlliedTo(petOwner));
        }
        return true;
    }

    /** The visible and audible half: ring + chips on every client nearby, a heavy layered boom. */
    private static void shockwave(ServerLevel level, Vec3 at, boolean arrival) {
        LacunaFx.send(level, at, FxPayload.at(FX, at).withParams((float) RADIUS, arrival ? 1.0F : 0.0F, 0.0F)
                .withColor(BanishFx.RIFT_ARGB));
        level.sendParticles(ModParticles.BANISH_MOTE.get(), at.x, at.y + 0.4, at.z, 40, 1.2, 0.25, 1.2, 0.45);
        level.sendParticles(ModParticles.RIFT_SPARK.get(), at.x, at.y + 0.2, at.z, 24, 1.6, 0.1, 1.6, 0.2);
        Sfx.at(level, at, SoundEvents.GENERIC_EXPLODE, 0.9F, 0.55F);
        Sfx.at(level, at, SoundEvents.WARDEN_SONIC_BOOM, 0.5F, arrival ? 0.6F : 0.75F);
        Sfx.at(level, at, SoundEvents.WIND_CHARGE_BURST, 1.2F, 0.6F);
        Sfx.at(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F);
    }
}
