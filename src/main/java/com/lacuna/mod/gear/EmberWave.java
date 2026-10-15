package com.lacuna.mod.gear;

import com.lacuna.mod.Balance;
import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModDamageTypes;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.LacunaAdvancements;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Ember Triangle's thermal wave (server). A cast flares for {@link Balance#EMBER_TRIANGLE_WINDUP} ticks, then a
 * circular front runs out from the caster's feet along the ground (the same terrain path the clients draw it on,
 * {@link TideCurrents#path}) to {@link Balance#EMBER_TRIANGLE_RADIUS} blocks in {@link Balance#EMBER_TRIANGLE_WAVE_TICKS}
 * ticks. Whatever it catches and {@link #isTarget targets} is pulverized: one {@link #DUST_FX} payload goes out first so
 * every client can turn the body into dust while it still has its shape, then the victim dies of
 * {@link ModDamageTypes#PULVERIZED} and its body is removed at once (loot and experience drop as usual). Bosses are
 * scorched for {@link Balance#EMBER_TRIANGLE_BOSS_DAMAGE} instead. Casts are short-lived, never saved, and cleared when
 * the server stops.
 */
public final class EmberWave {
    public static final String FEATURE = "ember_triangle";
    public static final String WINDUP_FX = "ember_windup";
    public static final String WAVE_FX = "ember_wave";
    public static final String DUST_FX = "ember_dust";
    /** {@link #DUST_FX} p2: the body crumbles completely / only its surface flakes off (the victim survives). */
    public static final float DUST_PULVERIZE = 0.0F;
    public static final float DUST_SHED = 1.0F;
    public static final String ADVANCEMENT = "lacuna/triangle_of_instant_pulverization";
    /** Full layered crumbling sounds for this many victims per tick (the rest get a single hiss). */
    private static final int LOUD_PER_TICK = 6;
    private static final int MAX_ACTIVE = 32;

    private static final List<Cast> CASTS = new ArrayList<>();

    private EmberWave() {}

    /** Radius of the front {@code ticks} after release: fast at first, slowing as it spreads (shared with the clients). */
    public static double radius(double ticks, double maxRadius, int expandTicks) {
        double t = Mth.clamp(ticks / Math.max(1, expandTicks), 0.0, 1.0);
        return maxRadius * (1.0 - (1.0 - t) * (1.0 - t));
    }

    /** The triangle flares in the caster's hand; the wave follows {@link Balance#EMBER_TRIANGLE_WINDUP} ticks later. */
    public static void start(ServerLevel level, Player player) {
        if (CASTS.size() >= MAX_ACTIVE) {
            CASTS.remove(0);
        }
        CASTS.add(new Cast(level.dimension(), player.getUUID(), player.getYRot()));
        Vec3 at = player.position();
        LacunaFx.send(level, at, FxPayload.at(WINDUP_FX, at).withEntity(player.getId())
                .withParams(Balance.EMBER_TRIANGLE_WINDUP, player.getYRot(), 0.0F));
        // a rising hum over a crackling fire and the breath of a blaze
        play(level, at, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.3F);
        play(level, at, SoundEvents.FIRE_AMBIENT, 1.6F, 0.7F);
        play(level, at, SoundEvents.BLAZE_AMBIENT, 0.9F, 0.6F);
    }

    // ------------------------------------------------------------------ ticking

    public static void onServerTick(ServerTickEvent.Post event) {
        if (CASTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        SafeRun.run(FEATURE, () -> {
            Iterator<Cast> it = CASTS.iterator();
            while (it.hasNext()) {
                Cast cast = it.next();
                ServerLevel level = server.getLevel(cast.dimension);
                if (level == null || !cast.tick(level)) {
                    it.remove();
                }
            }
        }, CASTS::clear);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CASTS.clear();
    }

    // ------------------------------------------------------------------ rules

    /**
     * Who the wave pulverizes: hostile creatures ({@link Enemy}), any mob hunting the caster, and other players only
     * when PvP is on. Never the caster, their pets, mount or allies, villagers, golems or peaceful animals.
     */
    static boolean isTarget(@Nullable Player owner, LivingEntity e) {
        if (!e.isAlive() || e.isSpectator() || e == owner || e instanceof ArmorStand) {
            return false;
        }
        if (owner != null && (e == owner.getVehicle() || owner.isAlliedTo(e))) {
            return false;
        }
        if (e instanceof Player other) {
            MinecraftServer server = other.getServer();
            return owner != null && !other.isCreative() && server != null && server.isPvpAllowed() && owner.canHarmPlayer(other);
        }
        if (e instanceof AbstractVillager || e instanceof IronGolem || e instanceof SnowGolem) {
            return false;
        }
        if (owner != null && e instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID())) {
            return false;
        }
        if (e instanceof Enemy) {
            return true;
        }
        if (owner != null && e instanceof Mob mob) {
            return mob.getTarget() == owner || mob instanceof NeutralMob neutral && neutral.isAngryAt(owner);
        }
        return false;
    }

    static boolean isBoss(LivingEntity e) {
        return e.getType().is(ModTags.Entities.CANNOT_BE_BANISHED);
    }

    private static boolean holdsTotem(LivingEntity e) {
        return e.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || e.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    /** Server sound with a slight random pitch variation, so repeated layers never sound identical. */
    private static void play(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        Sfx.at(level, at, sound, volume, pitch * (0.96F + level.random.nextFloat() * 0.08F));
    }

    private static void play(Level level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        play(level, at, sound.value(), volume, pitch);
    }

    @Nullable
    private static Player owner(ServerLevel level, UUID id) {
        return level.getPlayerByUUID(id);
    }

    // ------------------------------------------------------------------ one cast

    private static final class Cast {
        final ResourceKey<Level> dimension;
        final UUID owner;
        final float yaw;
        final Set<Integer> done = new HashSet<>();
        Vec3 origin;
        /** Starts at -1: the cast's first server tick is the same tick as the use and does not count. */
        int age = -1;
        int loud;

        Cast(ResourceKey<Level> dimension, UUID owner, float yaw) {
            this.dimension = dimension;
            this.owner = owner;
            this.yaw = yaw;
        }

        boolean tick(ServerLevel level) {
            int windup = Balance.EMBER_TRIANGLE_WINDUP;
            Player player = owner(level, owner);
            if (age < windup) {
                age++;
                if (player == null || !player.isAlive()) {
                    return false; // the caster died or left: the flare gutters out
                }
                Vec3 at = player.position();
                if (age == 2) {
                    play(level, at, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.65F);
                } else if (age == 4) {
                    play(level, at, SoundEvents.BEACON_POWER_SELECT, 0.9F, 2.0F);
                    play(level, at, SoundEvents.FIRE_AMBIENT, 1.2F, 1.25F);
                }
                if (age < windup) {
                    return true;
                }
                release(level, player);
            }
            int t = age - windup;
            age++;
            loud = 0;
            sweep(level, player, radius(t, Balance.EMBER_TRIANGLE_RADIUS, Balance.EMBER_TRIANGLE_WAVE_TICKS));
            if (t == 3) {
                play(level, origin, SoundEvents.FIRE_AMBIENT, 2.0F, 0.55F); // the roar of the front
            } else if (t == 7) {
                play(level, origin, SoundEvents.LAVA_AMBIENT, 1.4F, 0.6F);
                play(level, origin, SoundEvents.BLAZE_BURN, 0.8F, 0.6F);
            }
            if (t >= Balance.EMBER_TRIANGLE_WAVE_TICKS) {
                // the heat spends itself in a long hiss
                play(level, origin, SoundEvents.FIRE_EXTINGUISH, 0.9F, 0.55F);
                play(level, origin, SoundEvents.LAVA_EXTINGUISH, 0.6F, 0.6F);
                return false;
            }
            return true;
        }

        /** The front leaves the caster's feet: a boom, a burst of fire and a hiss of quenched heat. */
        private void release(ServerLevel level, Player player) {
            Vec3 feet = player.position();
            double y = TideCurrents.surface(level, feet.x, feet.z, feet.y, 0.6, 3);
            origin = new Vec3(feet.x, Double.isNaN(y) ? feet.y : y, feet.z);
            LacunaFx.send(level, origin, FxPayload.at(WAVE_FX, origin).withEntity(player.getId())
                    .withParams((float) Balance.EMBER_TRIANGLE_RADIUS, Balance.EMBER_TRIANGLE_WAVE_TICKS, yaw));
            play(level, origin, SoundEvents.WARDEN_SONIC_BOOM, 0.4F, 0.7F);
            play(level, origin, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.5F);
            play(level, origin, SoundEvents.FIRECHARGE_USE, 1.3F, 0.75F);
            play(level, origin, SoundEvents.BLAZE_SHOOT, 1.2F, 0.5F);
            play(level, origin, SoundEvents.LAVA_EXTINGUISH, 1.0F, 0.8F);
        }

        /** Catches everything the front has reached that stands on (or just above) the ground it runs over. */
        private void sweep(ServerLevel level, @Nullable Player player, double r) {
            AABB box = new AABB(origin, origin).inflate(r + 2.0, 0.0, r + 2.0)
                    .expandTowards(0.0, Balance.EMBER_TRIANGLE_REACH_UP + 4.0, 0.0).expandTowards(0.0, -6.0, 0.0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !done.contains(e.getId()))) {
                double dx = e.getX() - origin.x;
                double dz = e.getZ() - origin.z;
                double flat = Math.sqrt(dx * dx + dz * dz);
                double reach = Math.max(0.0, flat - e.getBbWidth() * 0.5);
                if (reach > r || !isTarget(player, e)) {
                    continue;
                }
                // the front hugs the terrain: only what stands near the ground it is running over is caught
                double ground = origin.y;
                if (flat > 0.25) {
                    Vec3 dir = new Vec3(dx / flat, 0.0, dz / flat);
                    ground = TideCurrents.heightAt(TideCurrents.path(level, origin, dir, reach + 0.5, false), reach);
                }
                if (e.getY() > ground + Balance.EMBER_TRIANGLE_REACH_UP || e.getY() + e.getBbHeight() < ground - Balance.EMBER_TRIANGLE_REACH_DOWN) {
                    continue;
                }
                done.add(e.getId());
                pulverize(level, player, e);
            }
        }

        private void pulverize(ServerLevel level, @Nullable Player player, LivingEntity e) {
            DamageSource source = ModDamageTypes.source(level, ModDamageTypes.PULVERIZED, player);
            if (e.isInvulnerableTo(source)) {
                return;
            }
            boolean boss = isBoss(e);
            // one payload, BEFORE the kill: the clients sample the body while it still has its shape
            Vec3 at = e.position();
            LacunaFx.send(level, at, new FxPayload(DUST_FX, e.getId(), at, origin, e.getBbWidth(), e.getBbHeight(),
                    boss || holdsTotem(e) ? DUST_SHED : DUST_PULVERIZE, Float.floatToIntBits(e.yBodyRot)));
            float amount = boss ? Balance.EMBER_TRIANGLE_BOSS_DAMAGE : Math.max(1000.0F, (e.getMaxHealth() + e.getAbsorptionAmount()) * 4.0F);
            e.hurt(source, amount);
            Vec3 c = at.add(0.0, e.getBbHeight() * 0.5, 0.0);
            boolean full = loud++ < LOUD_PER_TICK;
            if (!e.isDeadOrDying()) {
                // scorched, not pulverized: the surface flakes off with a sizzle
                play(level, c, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
                if (full) {
                    play(level, c, SoundEvents.GENERIC_BURN, 1.0F, 0.7F);
                }
                return;
            }
            // a crumbling hiss: the body falls to dust
            play(level, c, SoundEvents.SAND_BREAK, 1.3F, 0.75F);
            if (full) {
                play(level, c, SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.6F);
                play(level, c, SoundEvents.FIRE_EXTINGUISH, 0.7F, 1.5F);
            }
            if (!(e instanceof Player)) {
                e.remove(Entity.RemovalReason.KILLED); // no corpse: only the dust remains
            }
            if (player != null) {
                LacunaAdvancements.award(player, ADVANCEMENT);
            }
        }
    }
}
