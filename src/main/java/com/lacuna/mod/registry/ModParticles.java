package com.lacuna.mod.registry;

import com.lacuna.mod.Lacuna;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Lacuna particle types. Sprites live in {@code textures/particle/} (tools/art/m13_fx.py), sprite lists in
 * {@code particles/*.json} (tools/content/m13_fx.py); the client providers are in {@code client/fx/FxParticles}.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Lacuna.MODID);

    /** Soft warm light orb, additive, drifts and fades. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LIGHT_MOTE = simple("light_mote");
    /** Four-point golden star glint, additive, short. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOLAR_GLINT = simple("solar_glint");
    /** Hot red spark with gravity, additive. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRIMSON_SPARK = simple("crimson_spark");
    /** Black-violet smoke puff that shrinks away. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHADOW_MOTE = simple("shadow_mote");
    /** Black crescent sword slash with a crimson rim. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UMBRAL_SLASH = simple("umbral_slash");
    /** The Eclipser's own cut: a black blade with a crimson edge (Umbral Steel slashes are blue and violet). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ECLIPSE_SLASH = simple("eclipse_slash");
    /** Dark violet shard fragment with gravity. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UMBRAL_SHARD = simple("umbral_shard");
    /** Small violet glowing wisp (umbral energy), additive. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UMBRAL_WISP = simple("umbral_wisp");
    /** Large, very faint drifting fog wisp (Forgotten Land). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST = simple("mist");
    /** Ember Triangle: an ash grain or flake of a pulverized body (tinted and cooled by client/gear/EmberDust). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER_DUST = simple("ember_dust");
    /** Ember Triangle: a glowing ember speck that cools from orange to grey. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER_SPECK = simple("ember_speck");

    /** Chrono Hourglass: a tiny pale-blue clock face whose hand sweeps round as it rises and fades. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CHRONO_TICK = simple("chrono_tick");

    /** Banishment: a tumbling bone-and-umber stone chip of the Totem of Banishing, pinwheeling out of a totem burst. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BANISH_MOTE = simple("banish_mote");
    /** Banishment: a pale four-point spark with a lichen-green halo (the rift, the portal surface, the held totem). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_SPARK = simple("rift_spark");

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
