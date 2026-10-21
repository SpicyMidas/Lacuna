package com.lacuna.mod.eclipse;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.mummy.Mummification;
import com.lacuna.mod.network.PossessInputPayload;
import com.lacuna.mod.network.PossessSyncPayload;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.util.Sfx;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shadow Possession: what sets Shadow Walk apart from a glorified spectator. While shadow walking, clicking a creature
 * or a player (attack or use button, within {@link Balance#POSSESS_REACH} blocks) slips the shadow into its body:
 * <ul>
 *   <li>The possessor's camera moves into the host's eyes through the vanilla spectator camera
 *       ({@link ServerPlayer#setCamera}, which also carries the invisible shadow body along with the host, so chunk
 *       loading follows it; the client is re-anchored to the host now and then so its own player never falls into
 *       unloaded chunks). Sneaking leaves, as vanilla spectating does.</li>
 *   <li>Its movement keys and look drive the host ({@link HostControl}): a creature's own AI is suspended and it walks,
 *       jumps, swims or flies where the shadow wants; a player keeps its own client, which blends the shadow's keys
 *       in with its own as a mere nudge and is pulled back to the shadow's look (your controls win). Attacking makes the
 *       host strike (or shoot, for ranged creatures).</li>
 *   <li>Hosts fight back: players by struggling (moving against you, looking away, trying to strike or use items,
 *       which are refused), creatures by their will (stronger when hostile, big or wounded) and pain. Once the
 *       struggle fills up, the host fights back and an expulsion countdown starts (faster while a player keeps
 *       struggling); at zero the shadow is cast out, thrown out of the host's back, and must rest a moment. No body
 *       holds a shadow longer than {@link Balance#POSSESS_MAX_TICKS}.</li>
 *   <li>The Shadow Walk clock stands still while possessing (the effect turns endless) and resumes afterwards, so a
 *       short Eclipser walk is still worth a possession. Possession also ends when the walk ends any other way, the
 *       host dies, unloads or changes dimension, either side logs out or an operator changes the game mode.</li>
 * </ul>
 * Never possessed: bosses ({@code #c:bosses}, {@code #lacuna:possession_refuses}), multipart creatures, creatures
 * with no AI or invulnerable ones, mummies, creatures riding or ridden, other players' pets, and players who are in
 * creative/spectator, shadow walking, under spawn protection, or when PvP is off (or players are disabled in the
 * config). Possession never crosses dimensions, so it breaks none of the Forgotten Land's rules and works there.
 * Nothing is saved: a crash or restart simply leaves everyone in their own body (the walk itself restores on login).
 */
public final class Possession {
    public static final String FEATURE = "shadow_possession";
    private static final String FX_FEATURE = "shadow_possession_fx";
    public static final TagKey<EntityType<?>> REFUSED = TagKey.create(Registries.ENTITY_TYPE, Lacuna.id("possession_refuses"));

    enum End { LEFT, EXPELLED, LOST, WALK_ENDED }

    /** One shadow in one body. Server thread only. */
    static final class Link {
        final ServerPlayer possessor;
        final LivingEntity host;
        final int remainingWalk;
        float forward;
        float strafe;
        float yaw;
        float pitch;
        boolean jump;
        boolean sprint;
        long lastInput;
        long nextStrike;
        int ticks;
        float struggle;
        float pending;
        float pain;
        boolean struggled;
        int countdown = -1;
        int countdownMax = 1;
        Vec3 anchor;

        Link(ServerPlayer possessor, LivingEntity host, int remainingWalk, long now) {
            this.possessor = possessor;
            this.host = host;
            this.remainingWalk = remainingWalk;
            this.yaw = possessor.getYRot();
            this.pitch = possessor.getXRot();
            this.lastInput = now;
            this.anchor = host.position();
        }

        boolean fightingBack() {
            return countdown >= 0;
        }

        float hold() {
            return fightingBack() ? 1.0F : Mth.clamp(struggle / Balance.POSSESS_FIGHT_BACK, 0.0F, 1.0F);
        }

        float countdownFraction() {
            return fightingBack() ? Mth.clamp(countdown / (float) countdownMax, 0.0F, 1.0F) : -1.0F;
        }
    }

    private static final Map<UUID, Link> BY_POSSESSOR = new HashMap<>();
    private static final Map<Integer, Link> BY_HOST = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> IMMUNE = new HashMap<>();
    /** True while we make a player host strike: that attack is the shadow's, not the host's resistance. */
    static boolean forcing;

    private Possession() {}

    public static void register(IEventBus bus) {
        bus.addListener(Possession::onServerTick);
        bus.addListener(Possession::onEntityTickPre);
        bus.addListener(Possession::onEntityTickPost);
        bus.addListener(Possession::onDamage);
        bus.addListener(Possession::onDeath);
        bus.addListener(Possession::onAttack);
        bus.addListener(Possession::onRightClickItem);
        bus.addListener(Possession::onRightClickBlock);
        bus.addListener(Possession::onEntityInteract);
        bus.addListener(Possession::onEntityInteractSpecific);
        bus.addListener(Possession::onLeftClickBlock);
        bus.addListener(Possession::onChangeTarget);
        bus.addListener(Possession::onTravel);
        bus.addListener(Possession::onLogout);
        bus.addListener(Possession::onServerStopping);
        bus.addListener(Possession::onDespawn);
    }

    public static boolean enabled() {
        return LacunaConfig.get(LacunaConfig.SHADOW_POSSESSION_ENABLED) && !SafeRun.isDisabled(FEATURE) && ShadowWalk.enabled();
    }

    public static boolean isPossessing(ServerPlayer player) {
        return BY_POSSESSOR.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------------------------------------ client requests

    public static void onPayload(ServerPlayer player, PossessInputPayload p) {
        SafeRun.run(FEATURE, () -> handle(player, p), Possession::releaseAll);
    }

    private static void handle(ServerPlayer player, PossessInputPayload p) {
        long now = player.serverLevel().getGameTime();
        switch (p.kind()) {
            case PossessInputPayload.REQUEST -> request(player, p.entityId(), p.has(PossessInputPayload.SECONDARY));
            case PossessInputPayload.INPUT -> {
                Link link = BY_POSSESSOR.get(player.getUUID());
                if (link != null && look(link, p.yaw(), p.pitch())) {
                    link.forward = finite(p.forward()) ? Mth.clamp(p.forward(), -1.0F, 1.0F) : 0.0F;
                    link.strafe = finite(p.strafe()) ? Mth.clamp(p.strafe(), -1.0F, 1.0F) : 0.0F;
                    link.jump = p.has(PossessInputPayload.JUMP);
                    link.sprint = p.has(PossessInputPayload.SPRINT);
                    link.lastInput = now;
                }
            }
            case PossessInputPayload.ACTION -> {
                Link link = BY_POSSESSOR.get(player.getUUID());
                if (link != null) {
                    look(link, p.yaw(), p.pitch());
                    HostControl.strike(link, p.entityId(), now);
                }
            }
            case PossessInputPayload.STRUGGLE -> {
                Link link = BY_HOST.get(player.getId());
                if (link != null && link.host == player && finite(p.amount())) {
                    link.pending = Math.min(link.pending + Mth.clamp(p.amount(), 0.0F, Balance.POSSESS_PLAYER_STRUGGLE_MAX * 4),
                            Balance.POSSESS_PLAYER_STRUGGLE_MAX * 4);
                }
            }
            case PossessInputPayload.RELEASE -> {
                Link link = BY_POSSESSOR.get(player.getUUID());
                if (link != null) {
                    end(link, End.LEFT);
                }
            }
            default -> { }
        }
    }

    private static boolean finite(float f) {
        return !Float.isNaN(f) && !Float.isInfinite(f);
    }

    private static boolean look(Link link, float yaw, float pitch) {
        if (!finite(yaw) || !finite(pitch)) {
            return false;
        }
        link.yaw = Mth.wrapDegrees(yaw);
        link.pitch = Mth.clamp(pitch, -90.0F, 90.0F);
        return true;
    }

    private static void request(ServerPlayer player, int entityId, boolean secondary) {
        if (!ShadowWalk.isActive(player) || !player.isSpectator() || isPossessing(player)) {
            return;
        }
        Entity target = player.serverLevel().getEntity(entityId);
        if (target instanceof PartEntity<?> part) {
            target = part.getParent();
        }
        if (target == null || target == player) {
            return;
        }
        double reach = Balance.POSSESS_REACH + 2.0;
        if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > reach * reach) {
            return;
        }
        if (!enabled()) {
            if (!secondary && player.canInteractWithEntity(target.getBoundingBox(), 1.0)) {
                player.setCamera(target); // possession is off: the attack button spectates as in vanilla
            }
            return;
        }
        String refusal = refusal(player, target);
        if (refusal != null) {
            refuse(player, refusal);
            return;
        }
        begin(player, (LivingEntity) target);
    }

    private static void refuse(ServerPlayer player, String key) {
        if (key.equals("lacuna.possess.reeling")) {
            long left = COOLDOWN.getOrDefault(player.getUUID(), 0L) - player.serverLevel().getGameTime();
            player.displayClientMessage(Component.translatable(key, String.format(java.util.Locale.ROOT, "%.1f", Math.max(0L, left) / 20.0F))
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        } else {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    /** Why {@code possessor} cannot possess {@code target}, as a lang key, or null if it can. */
    @Nullable
    static String refusal(ServerPlayer possessor, Entity target) {
        if (!(target instanceof LivingEntity) || !target.isAlive() || target == possessor) {
            return "lacuna.possess.nothing";
        }
        ServerLevel level = possessor.serverLevel();
        long now = level.getGameTime();
        if (now < COOLDOWN.getOrDefault(possessor.getUUID(), Long.MIN_VALUE)) {
            return "lacuna.possess.reeling";
        }
        if (BY_HOST.containsKey(target.getId())) {
            return "lacuna.possess.taken";
        }
        if (now < IMMUNE.getOrDefault(target.getUUID(), Long.MIN_VALUE)) {
            return "lacuna.possess.wary";
        }
        if (target.isMultipartEntity() || target.getType().is(Tags.EntityTypes.BOSSES) || target.getType().is(REFUSED)) {
            return "lacuna.possess.too_mighty";
        }
        if (target instanceof Player) {
            if (!(target instanceof ServerPlayer host) || !LacunaConfig.get(LacunaConfig.SHADOW_POSSESSION_PLAYERS)) {
                return "lacuna.possess.no_players";
            }
            if (host.isCreative() || host.isSpectator() || ShadowWalk.isActive(host) || host.getAbilities().invulnerable
                    || host.isSleeping() || host.hasDisconnected()) {
                return "lacuna.possess.untouchable";
            }
            if (!possessor.canHarmPlayer(host) || level.getDifficulty() == Difficulty.PEACEFUL) {
                return "lacuna.possess.no_pvp";
            }
            if (level.getServer().isUnderSpawnProtection(level, host.blockPosition(), possessor)) {
                return "lacuna.possess.protected";
            }
            return null;
        }
        if (!(target instanceof Mob mob)) {
            return "lacuna.possess.nothing"; // armor stands and other bodies without a will
        }
        if (mob.isNoAi() || mob.isInvulnerable() || Mummification.sealed(mob)) {
            return "lacuna.possess.lifeless";
        }
        if (mob.isPassenger() || mob.getControllingPassenger() != null) {
            return "lacuna.possess.ridden";
        }
        if (mob instanceof OwnableEntity pet && pet.getOwnerUUID() != null && !pet.getOwnerUUID().equals(possessor.getUUID())) {
            return "lacuna.possess.not_yours";
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    private static void begin(ServerPlayer possessor, LivingEntity host) {
        long now = possessor.serverLevel().getGameTime();
        int remaining = ShadowWalk.suspend(possessor);
        Link link = new Link(possessor, host, remaining, now);
        BY_POSSESSOR.put(possessor.getUUID(), link);
        BY_HOST.put(host.getId(), link);
        if (possessor.getCamera() != host) {
            possessor.setCamera(host);
        }
        link.anchor = host.position();
        if (host instanceof Mob mob) {
            HostControl.seize(mob);
        }
        SafeRun.run(FX_FEATURE, () -> {
            ServerLevel level = possessor.serverLevel();
            Vec3 c = host.position().add(0, host.getBbHeight() * 0.6, 0);
            particles(level, ModParticles.SHADOW_MOTE.get(), c, 30, host.getBbWidth() * 0.6, host.getBbHeight() * 0.4, 0.04, possessor);
            particles(level, ModParticles.UMBRAL_WISP.get(), host.getEyePosition(), 14, 0.3, 0.3, 0.05, possessor);
            Sfx.at(level, c, SoundEvents.SOUL_ESCAPE, 1.0F, 0.55F);
            Sfx.at(level, c, SoundEvents.VEX_AMBIENT, 0.8F, 0.5F);
            Sfx.at(level, c, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.6F, 0.9F);
        });
        sync(link);
    }

    /** Ends {@code link}: everyone back in their own body, the host's own will restored, the walk clock running again. */
    static void end(Link link, End reason) {
        if (BY_POSSESSOR.get(link.possessor.getUUID()) != link) {
            return;
        }
        BY_POSSESSOR.remove(link.possessor.getUUID());
        BY_HOST.remove(link.host.getId());
        ServerPlayer p = link.possessor;
        LivingEntity host = link.host;
        boolean expelled = reason == End.EXPELLED;
        long now = p.serverLevel().getGameTime();
        try {
            if (host instanceof Mob mob) {
                HostControl.free(mob);
            }
            if (host instanceof ServerPlayer hostPlayer && !hostPlayer.hasDisconnected()) {
                PacketDistributor.sendToPlayer(hostPlayer, PossessSyncPayload.none(host.getId(), expelled));
                if (expelled) {
                    hostPlayer.displayClientMessage(Component.translatable("lacuna.possess.freed").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
        } finally {
            COOLDOWN.put(p.getUUID(), now + (expelled ? Balance.POSSESS_EXPEL_COOLDOWN : Balance.POSSESS_LEAVE_COOLDOWN));
            if (expelled) {
                IMMUNE.put(host.getUUID(), now + (host instanceof Player ? Balance.POSSESS_HOST_IMMUNITY_PLAYER : Balance.POSSESS_HOST_IMMUNITY_MOB));
            }
            if (!p.hasDisconnected()) {
                if (p.getCamera() != p) {
                    p.setCamera(p);
                }
                PacketDistributor.sendToPlayer(p, PossessSyncPayload.none(host.getId(), expelled));
                if (expelled && p.level() == host.level()) {
                    throwOut(p, host);
                    p.displayClientMessage(Component.translatable("lacuna.possess.expelled").withStyle(ChatFormatting.DARK_RED), true);
                }
            }
            if (p.level() == host.level()) {
                SafeRun.run(FX_FEATURE, () -> leaveFx(p.serverLevel(), host, expelled));
            }
            if (reason != End.WALK_ENDED) {
                if (p.isSpectator()) {
                    ShadowWalk.resume(p, link.remainingWalk);
                } else {
                    ShadowWalk.restore(p); // an operator changed the game mode meanwhile: the walk is over
                }
            }
        }
    }

    /** Called first thing when Shadow Walk ends for {@code player}: a shadow inside a host leaves it. */
    public static void onWalkEnding(ServerPlayer player) {
        Link link = BY_POSSESSOR.get(player.getUUID());
        if (link == null) {
            return;
        }
        try {
            end(link, End.WALK_ENDED);
        } catch (RuntimeException e) {
            Lacuna.LOGGER.error("Could not end the Shadow Possession of {}", player.getGameProfile().getName(), e);
            BY_POSSESSOR.remove(player.getUUID());
            BY_HOST.values().remove(link);
        }
    }

    static void releaseAll() {
        for (Link link : new ArrayList<>(BY_POSSESSOR.values())) {
            try {
                end(link, End.LOST);
            } catch (RuntimeException e) {
                Lacuna.LOGGER.error("Could not end a Shadow Possession", e);
            }
        }
        BY_POSSESSOR.clear();
        BY_HOST.clear();
    }

    /** The cast-out shadow is thrown out of the host's back (never through a wall), facing it. */
    private static void throwOut(ServerPlayer p, LivingEntity host) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = host.getEyePosition();
        Vec3 back = Vec3.directionFromRotation(0.0F, host.getYHeadRot()).scale(-1.0);
        Vec3 dir = new Vec3(back.x, 0.35, back.z).normalize();
        Vec3 end = eye.add(dir.scale(Balance.POSSESS_EXPEL_DISTANCE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 to = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(dir.scale(0.45));
        double dx = eye.x - to.x;
        double dz = eye.z - to.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(eye.y - to.y, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        p.connection.teleport(to.x, to.y - p.getEyeHeight(), to.z, yaw, pitch);
    }

    // ------------------------------------------------------------------------------------------ tick

    private static void onServerTick(ServerTickEvent.Post event) {
        if (BY_POSSESSOR.isEmpty()) {
            adoptVanillaSpectators(event);
            return;
        }
        for (Link link : new ArrayList<>(BY_POSSESSOR.values())) {
            SafeRun.run(FEATURE, () -> tick(link), () -> {
                try {
                    end(link, End.LOST);
                } catch (RuntimeException ignored) {
                    BY_POSSESSOR.remove(link.possessor.getUUID());
                    BY_HOST.remove(link.host.getId());
                }
            });
        }
        adoptVanillaSpectators(event);
    }

    /**
     * A shadow walker whose camera went into a living body some other way (the vanilla spectator attack packet)
     * possesses it if allowed, or is pushed back into its own body (only while possession is enabled: otherwise
     * vanilla spectating is left alone).
     */
    private static void adoptVanillaSpectators(ServerTickEvent.Post event) {
        if (!enabled()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Entity camera = player.getCamera();
            if (camera == player || !(camera instanceof LivingEntity) || isPossessing(player) || !ShadowWalk.isActive(player)) {
                continue;
            }
            SafeRun.run(FEATURE, () -> {
                String refusal = refusal(player, camera);
                if (refusal == null && player.isSpectator()) {
                    begin(player, (LivingEntity) camera);
                } else {
                    player.setCamera(player);
                    if (refusal != null) {
                        refuse(player, refusal);
                    }
                }
            });
        }
    }

    private static void tick(Link link) {
        ServerPlayer p = link.possessor;
        LivingEntity host = link.host;
        if (p.hasDisconnected() || p.isRemoved() || !ShadowWalk.isActive(p) || !p.isSpectator()) {
            end(link, End.LOST);
            return;
        }
        if (!host.isAlive() || host.isRemoved() || host.level() != p.level() || Mummification.sealed(host)
                || host instanceof ServerPlayer hp && (hp.hasDisconnected() || hp.isCreative() || hp.isSpectator())
                || host instanceof Mob mob && (mob.isPassenger() || mob.getControllingPassenger() != null)) {
            end(link, End.LOST);
            return;
        }
        if (p.getCamera() != host) {
            end(link, End.LEFT); // sneaked out (vanilla), teleported, or the camera went elsewhere
            return;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        link.ticks++;
        if (now - link.lastInput > 10) {
            link.forward = 0.0F;
            link.strafe = 0.0F;
            link.jump = false;
            link.sprint = false;
        }

        // resistance
        float gain;
        if (host instanceof Player) {
            gain = Math.min(link.pending, Balance.POSSESS_PLAYER_STRUGGLE_MAX);
            link.pending -= gain;
            link.struggled = gain > 0.05F || link.pain > 0.0F;
            gain += link.pain;
            link.struggle = Math.max(0.0F, link.struggle + gain - Balance.POSSESS_STRUGGLE_DECAY);
        } else {
            float will = HostControl.will((Mob) host, p);
            float roll = level.random.nextFloat();
            gain = will * (0.5F + roll) + link.pain;
            link.struggled = link.pain > 0.0F || roll > 0.9F;
            link.struggle += gain;
        }
        link.pain = 0.0F;
        if (!link.fightingBack()) {
            if (link.struggle >= Balance.POSSESS_FIGHT_BACK || link.ticks >= Balance.POSSESS_MAX_TICKS) {
                link.countdownMax = host instanceof Player ? Balance.POSSESS_EXPEL_TICKS_PLAYER : Balance.POSSESS_EXPEL_TICKS_MOB;
                link.countdown = link.countdownMax;
                SafeRun.run(FX_FEATURE, () -> fightBackFx(level, host, p));
            }
        } else {
            link.countdown -= link.struggled ? 2 : 1;
            if (link.countdown <= 0) {
                end(link, End.EXPELLED);
                return;
            }
        }

        // keep the shadow's own client player in loaded chunks near its host
        if (link.ticks % 20 == 0 && link.anchor.distanceToSqr(host.position()) > 24 * 24) {
            p.connection.teleport(host.getX(), host.getY(), host.getZ(), 0.0F, 0.0F, EnumSet.of(RelativeMovement.Y_ROT, RelativeMovement.X_ROT));
            link.anchor = host.position();
        }
        if (!SafeRun.isDisabled(FX_FEATURE)) {
            SafeRun.run(FX_FEATURE, () -> ambientFx(level, link, now));
        }
        sync(link);
    }

    private static void sync(Link link) {
        byte struggling = link.struggled ? PossessSyncPayload.STRUGGLING : 0;
        PacketDistributor.sendToPlayer(link.possessor, new PossessSyncPayload(PossessSyncPayload.POSSESSOR, link.host.getId(), 0, 0,
                link.yaw, link.pitch, struggling, link.hold(), link.countdownFraction(), 0.0F));
        if (link.host instanceof ServerPlayer host) {
            byte flags = (byte) (struggling | (link.jump ? PossessSyncPayload.JUMP : 0) | (link.sprint ? PossessSyncPayload.SPRINT : 0));
            PacketDistributor.sendToPlayer(host, new PossessSyncPayload(PossessSyncPayload.HOST, host.getId(), link.forward, link.strafe,
                    link.yaw, link.pitch, flags, link.hold(), link.countdownFraction(), Balance.POSSESS_PLAYER_OWN_INPUT));
        }
    }

    // ------------------------------------------------------------------------------------------ fx

    /** Particles for everyone near {@code at} except {@code skip} (and anyone looking out of the host's eyes). */
    private static void particles(ServerLevel level, ParticleOptions type, Vec3 at, int count, double spread, double spreadY, double speed,
                                  @Nullable Entity skip) {
        for (ServerPlayer other : level.players()) {
            if (other != skip && other.getCamera() == other && other.distanceToSqr(at) < 48 * 48) {
                level.sendParticles(other, type, false, at.x, at.y, at.z, count, spread, spreadY, spread, speed);
            }
        }
    }

    /** Umbral wisps glow at the host's eyes for onlookers, and the shadow whispers now and then. */
    private static void ambientFx(ServerLevel level, Link link, long now) {
        LivingEntity host = link.host;
        if (now % 3 == 0) {
            Vec3 eye = host.getEyePosition();
            Vec3 fwd = Vec3.directionFromRotation(0.0F, host.getYHeadRot());
            Vec3 side = new Vec3(-fwd.z, 0.0, fwd.x).scale(Math.max(0.08, host.getBbWidth() * 0.2));
            Vec3 front = eye.add(fwd.scale(host.getBbWidth() * 0.5 + 0.02));
            for (ServerPlayer other : level.players()) {
                if (other != link.possessor && other != host && other.getCamera() == other && other.distanceToSqr(eye) < 40 * 40) {
                    level.sendParticles(other, ModParticles.UMBRAL_WISP.get(), false, front.x + side.x, front.y, front.z + side.z, 1, 0.01, 0.01, 0.01, 0.0);
                    level.sendParticles(other, ModParticles.UMBRAL_WISP.get(), false, front.x - side.x, front.y, front.z - side.z, 1, 0.01, 0.01, 0.01, 0.0);
                    if (now % 6 == 0) {
                        level.sendParticles(other, ModParticles.SHADOW_MOTE.get(), false, eye.x, eye.y + 0.1, eye.z, 1, 0.25, 0.15, 0.25, 0.01);
                    }
                    if (link.fightingBack() && now % 6 == 3) {
                        level.sendParticles(other, ModParticles.CRIMSON_SPARK.get(), false, eye.x, eye.y, eye.z, 1, 0.3, 0.3, 0.3, 0.03);
                    }
                }
            }
        }
        if (link.ticks % 60 == 30) {
            Sfx.at(level, host.getEyePosition(), SoundEvents.VEX_AMBIENT, 0.3F, 0.45F + level.random.nextFloat() * 0.1F);
        }
    }

    private static void fightBackFx(ServerLevel level, LivingEntity host, ServerPlayer possessor) {
        Vec3 eye = host.getEyePosition();
        particles(level, ModParticles.CRIMSON_SPARK.get(), eye, 12, 0.35, 0.35, 0.06, possessor);
        Sfx.at(level, eye, SoundEvents.VEX_CHARGE, 0.8F, 0.6F);
        if (host instanceof ServerPlayer hostPlayer) {
            hostPlayer.displayClientMessage(Component.translatable("lacuna.possess.host.fighting").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    private static void leaveFx(ServerLevel level, LivingEntity host, boolean expelled) {
        Vec3 eye = host.getEyePosition();
        if (expelled) {
            particles(level, ModParticles.SHADOW_MOTE.get(), eye, 40, 0.5, 0.5, 0.08, null);
            particles(level, ModParticles.CRIMSON_SPARK.get(), eye, 12, 0.4, 0.4, 0.08, null);
            particles(level, ModParticles.UMBRAL_WISP.get(), eye, 10, 0.4, 0.4, 0.06, null);
            Sfx.at(level, eye, SoundEvents.SOUL_ESCAPE, 1.0F, 1.25F);
            Sfx.at(level, eye, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9F, 0.7F);
            Sfx.at(level, eye, SoundEvents.VEX_HURT, 0.7F, 0.55F);
        } else {
            particles(level, ModParticles.SHADOW_MOTE.get(), eye, 18, 0.35, 0.35, 0.04, null);
            Sfx.at(level, eye, SoundEvents.SOUL_ESCAPE, 0.8F, 0.9F);
            Sfx.at(level, eye, SoundEvents.VEX_AMBIENT, 0.5F, 0.8F);
        }
    }

    // ------------------------------------------------------------------------------------------ events

    private static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob) || BY_HOST.isEmpty()) {
            return;
        }
        Link link = BY_HOST.get(mob.getId());
        if (link != null && link.host == mob) {
            SafeRun.run(FEATURE, () -> HostControl.preTick(link, mob), () -> HostControl.free(mob));
        }
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob) || BY_HOST.isEmpty()) {
            return;
        }
        Link link = BY_HOST.get(mob.getId());
        if (link != null && link.host == mob) {
            SafeRun.run(FEATURE, () -> HostControl.postTick(link, mob), () -> HostControl.free(mob));
        }
    }

    @Nullable
    private static Link hostLink(Entity entity) {
        if (entity.level().isClientSide() || BY_HOST.isEmpty()) {
            return null;
        }
        Link link = BY_HOST.get(entity.getId());
        return link != null && link.host == entity ? link : null;
    }

    /** Pain helps a host break free. */
    private static void onDamage(LivingDamageEvent.Post event) {
        Link link = hostLink(event.getEntity());
        if (link != null && event.getNewDamage() > 0.0F) {
            link.pain += event.getNewDamage() * Balance.POSSESS_PAIN_STRUGGLE;
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        Link link = hostLink(event.getEntity());
        if (link != null) {
            SafeRun.run(FEATURE, () -> end(link, End.LOST));
        }
    }

    /** A possessed player's own strikes and item uses are refused; each one is a struggle against the shadow. */
    private static void resist(Player player, ICancellableEvent event) {
        Link link = hostLink(player);
        if (link != null && !forcing) {
            event.setCanceled(true);
            link.pending = Math.min(link.pending + Balance.POSSESS_ACTION_STRUGGLE, Balance.POSSESS_PLAYER_STRUGGLE_MAX * 4);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        resist(event.getEntity(), event);
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        resist(event.getEntity(), event);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        resist(event.getEntity(), event);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        resist(event.getEntity(), event);
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        resist(event.getEntity(), event);
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        resist(event.getEntity(), event);
    }

    /** A possessed creature does not pick targets of its own (clearing one is always allowed). */
    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() != null && hostLink(event.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    /** A host about to change dimension leaves its shadow behind (it never crosses with it). */
    private static void onTravel(EntityTravelToDimensionEvent event) {
        Link link = hostLink(event.getEntity());
        if (link != null) {
            SafeRun.run(FEATURE, () -> end(link, End.LOST));
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Link link = hostLink(player);
            if (link != null) {
                SafeRun.run(FEATURE, () -> end(link, End.LOST));
            }
            COOLDOWN.remove(player.getUUID());
        }
    }

    /** A possessed creature never despawns (its shadow counts as no player for vanilla). */
    private static void onDespawn(MobDespawnEvent event) {
        if (hostLink(event.getEntity()) != null) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        releaseAll();
        COOLDOWN.clear();
        IMMUNE.clear();
    }
}
