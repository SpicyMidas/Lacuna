package com.lacuna.mod.confusion;

import com.lacuna.mod.fx.LacunaFx;
import com.lacuna.mod.network.FxPayload;
import com.lacuna.mod.registry.ModEntities;
import com.lacuna.mod.util.LacunaAdvancements;
import com.lacuna.mod.util.SafeSpot;
import com.lacuna.mod.util.Sfx;
import com.lacuna.mod.util.Teleporter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Totem of Confusion: raising the totem conjures {@link #COPIES} copies of the player ({@link ConfusionCopy}) on free
 * ground {@link #MIN_RADIUS}-{@link #MAX_RADIUS} blocks around them. A coin flip ({@link #SWAP_CHANCE}) decides the
 * rest: either the player slips into one of the copies' places and a copy appears where they stood ("swap"), or the
 * player stays put with the copies around them ("stay"). Both look the same to everyone else; only the owner is told
 * which one happened (action bar).
 * <p>
 * Each copy carries the owner's skin, name tag, armour and held items and mirrors the owner's movement input every
 * tick, turned by its own heading offset ({@link #HEADINGS}), so the crowd walks, sprints, jumps, sneaks and swings
 * together but scatters in different directions. Two hits pop a copy; they fade after {@link #LIFETIME} ticks.
 * Hostile mobs hunting the owner are sometimes drawn to the nearest copy. Tuning lives here (not in the shared
 * Balance) so the feature stays self-contained.
 */
public final class Confusion {
    /** SafeRun feature name (server and client). */
    public static final String FEATURE = "confusion";
    /** FxPayload kind sent to the owner: plays the vanilla totem "item activation" animation with our totem. */
    public static final String FX_RAISE = "confusion_raise";
    public static final String ADVANCEMENT = "lacuna/which_one_is_real";

    // ------------------------------------------------------------------ the totem
    /** Three raises per totem. */
    public static final int USES = 3;
    /** 45 s between raises. */
    public static final int COOLDOWN = 900;
    /** The totem is held up this long (goat-horn style pose) before the copies appear. */
    public static final int RAISE_TICKS = 12;
    /** Chance that the player swaps places with one of the copies instead of staying among them. */
    public static final float SWAP_CHANCE = 0.5F;

    // ------------------------------------------------------------------ the copies
    public static final int COPIES = 5;
    public static final double MIN_RADIUS = 3.0;
    public static final double MAX_RADIUS = 5.0;
    /** Copies never stand closer than this to each other (or to the player). */
    public static final double MIN_SPACING = 1.6;
    /** Heading offsets in degrees: the copy furthest behind the player turns around, the others fan out to the sides. */
    public static final float REAR_HEADING = 180.0F;
    public static final float[] SIDE_HEADINGS = {-80.0F, -40.0F, 40.0F, 80.0F};
    /** About 20 s. */
    public static final int LIFETIME = 400;
    /** Hits (any damage instance, 10 tick invulnerability between them like players) that pop a copy. */
    public static final int HITS = 2;
    /** A copy this far from its owner (or in another dimension) fades. */
    public static final double LEASH = 64.0;
    /** Ticks a movement input from the owner's client stays valid; older input counts as standing still. */
    public static final int INPUT_STALE_TICKS = 5;

    // ------------------------------------------------------------------ distracting hostiles
    public static final double LURE_RADIUS = 16.0;
    /** Chance, when the copies appear, that a hostile mob hunting the owner switches to the nearest copy. */
    public static final float LURE_CHANCE_START = 0.6F;
    /** Chance, re-rolled every {@link #LURE_INTERVAL} ticks while the copies stand, for mobs still on the owner. */
    public static final float LURE_CHANCE = 0.3F;
    public static final int LURE_INTERVAL = 20;

    private Confusion() {}

    /** Common wiring (called from the mod constructor): attributes, the input payload, cleanup. */
    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(Confusion::onAttributes);
        modBus.addListener(Confusion::onRegisterPayloads);
        forgeBus.addListener(Confusion::onLogout);
        forgeBus.addListener(Confusion::onServerStopped);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CONFUSION_COPY.get(), ConfusionCopy.createAttributes().build());
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // client -> server, handled on the server thread: the values only steer the sender's own copies
        event.registrar("1").playToServer(MirrorInput.TYPE, MirrorInput.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                MirrorInput.receive(player, payload);
            }
        });
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MirrorInput.forget(event.getEntity().getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        MirrorInput.clear();
    }

    // ------------------------------------------------------------------ raising the totem

    /** Conjures the copies around {@code player}; true when it happened (the totem then wears and cools down). */
    public static boolean activate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        removeCopies(player);

        Vec3 origin = player.position();
        List<Spot> spots = findSpots(level, player, random);
        List<Vec3> positions = new ArrayList<>();
        spots.forEach(s -> positions.add(s.pos()));
        while (positions.size() < COPIES) {
            positions.add(origin); // cramped: the rest step out of the player and walk off on their own headings
        }

        boolean swap = false;
        List<Integer> safe = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).safe()) {
                safe.add(i);
            }
        }
        if (!safe.isEmpty() && !player.isPassenger() && !player.isSleeping() && random.nextFloat() < SWAP_CHANCE) {
            int pick = safe.get(random.nextInt(safe.size()));
            Vec3 target = positions.get(pick);
            if (Teleporter.teleport(player, level, target, player.getYRot(), player.getXRot()) == player) {
                positions.set(pick, origin);
                swap = true;
            }
        }

        Vec3 center = player.position();
        float[] headings = headings(center, player.getYRot(), positions);
        List<ConfusionCopy> copies = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            ConfusionCopy copy = ModEntities.CONFUSION_COPY.get().create(level);
            if (copy == null) {
                continue;
            }
            Vec3 p = positions.get(i);
            copy.setup(player, headings[i], p);
            if (level.addFreshEntity(copy)) {
                copies.add(copy);
            }
        }
        if (copies.isEmpty()) {
            return false;
        }

        // identical bursts and sounds at every body (the player's included), so nothing tells the real one apart
        List<Vec3> bodies = new ArrayList<>(positions);
        bodies.add(center);
        for (Vec3 p : bodies) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.x, p.y + 1.0, p.z, 26, 0.3, 0.6, 0.3, 0.32);
            level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.8, p.z, 6, 0.25, 0.4, 0.25, 0.02);
            Sfx.at(level, p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 0.9F + random.nextFloat() * 0.2F);
        }
        Sfx.at(level, origin, SoundEvents.TOTEM_USE, 0.45F, 1.45F);
        Sfx.at(level, origin, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F, 1.2F);

        LacunaFx.sendTo(player, FxPayload.at(FX_RAISE, center).withEntity(player.getId()));
        player.displayClientMessage(Component.translatable(swap ? "lacuna.confusion.swap" : "lacuna.confusion.stay"), true);
        lure(level, player, copies, LURE_CHANCE_START, random);
        LacunaAdvancements.award(player, ADVANCEMENT);
        return true;
    }

    /** A place for a copy; {@code safe} spots have solid dry footing and are fit to teleport the player to. */
    record Spot(Vec3 pos, boolean safe) {}

    /**
     * Up to {@link #COPIES} standable spots in a ring around the player, evenly spread with some jitter, each reachable
     * in a straight line from the player's chest (no copies, and no swap, through walls). Dry solid ground is tried
     * first; water is accepted as a fallback (using the totem while swimming).
     */
    static List<Spot> findSpots(ServerLevel level, ServerPlayer player, RandomSource random) {
        List<Spot> out = new ArrayList<>();
        Vec3 origin = player.position();
        Vec3 chest = origin.add(0.0, 1.0, 0.0);
        float base = random.nextFloat() * 360.0F;
        for (int k = 0; k < COPIES; k++) {
            Spot found = null;
            for (int pass = 0; pass < 2 && found == null; pass++) {
                boolean strict = pass == 0;
                for (int attempt = 0; attempt < 8 && found == null; attempt++) {
                    double angle = Math.toRadians(base + k * (360.0 / COPIES) + (attempt == 0 ? 0.0 : (random.nextDouble() - 0.5) * 60.0));
                    double r = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
                    found = spotAt(level, player, chest, origin.x + Math.cos(angle) * r, origin.y, origin.z + Math.sin(angle) * r,
                            out, strict);
                }
            }
            if (found != null) {
                out.add(found);
            }
        }
        return out;
    }

    @Nullable
    private static Spot spotAt(ServerLevel level, ServerPlayer player, Vec3 chest, double x, double y, double z, List<Spot> taken,
                               boolean strict) {
        int baseY = Mth.floor(y + 0.01);
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            BlockPos feet = BlockPos.containing(x, baseY + dy, z);
            if (!(strict ? SafeSpot.isSafe(level, feet) : isSwimmable(level, feet))) {
                continue;
            }
            Vec3 pos = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            if (pos.distanceTo(player.position()) < MIN_SPACING) {
                continue;
            }
            boolean crowded = false;
            for (Spot s : taken) {
                crowded |= s.pos().distanceTo(pos) < MIN_SPACING;
            }
            if (crowded) {
                continue;
            }
            Vec3 aim = pos.add(0.0, 1.0, 0.0);
            if (level.clip(new ClipContext(chest, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
                    != HitResult.Type.MISS) {
                continue;
            }
            return new Spot(pos, strict);
        }
        return null;
    }

    /** Fallback footing: a 2 high gap without collision, lava or fire, either in water or above a solid top face. */
    private static boolean isSwimmable(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet) || !level.getWorldBorder().isWithinBounds(feet)
                || feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        for (BlockPos p : new BlockPos[] {feet, feet.above()}) {
            BlockState state = level.getBlockState(p);
            if (!state.getCollisionShape(level, p).isEmpty() || state.getFluidState().is(FluidTags.LAVA) || state.isBurning(level, p)) {
                return false;
            }
        }
        BlockPos floor = feet.below();
        return level.getFluidState(feet).is(FluidTags.WATER) || level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    /**
     * Heading offsets for copies at {@code positions} around a player at {@code center} facing {@code yaw}: the copy
     * furthest behind turns around ({@link #REAR_HEADING}); the others fan out ({@link #SIDE_HEADINGS}) in the order
     * they stand from left to right, so each one walks away on its own side.
     */
    static float[] headings(Vec3 center, float yaw, List<Vec3> positions) {
        int n = positions.size();
        float[] bearing = new float[n];
        for (int i = 0; i < n; i++) {
            Vec3 d = positions.get(i).subtract(center);
            float to = d.horizontalDistanceSqr() < 1.0E-4 ? yaw + 180.0F : (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
            bearing[i] = Mth.wrapDegrees(to - yaw);
        }
        float[] out = new float[n];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        int rear = order.stream().max(Comparator.comparingDouble(i -> Math.abs(bearing[i]))).orElse(0);
        out[rear] = REAR_HEADING;
        order.remove(Integer.valueOf(rear));
        order.sort(Comparator.comparingDouble(i -> bearing[i]));
        for (int j = 0; j < order.size(); j++) {
            out[order.get(j)] = SIDE_HEADINGS[j % SIDE_HEADINGS.length] + (j >= SIDE_HEADINGS.length ? 20.0F : 0.0F);
        }
        return out;
    }

    /** Removes {@code owner}'s copies at once (a fresh raise replaces the old crowd). */
    static void removeCopies(ServerPlayer owner) {
        for (ConfusionCopy copy : copiesOf(owner)) {
            copy.vanish(false);
        }
    }

    static List<ConfusionCopy> copiesOf(ServerPlayer owner) {
        return owner.serverLevel().getEntitiesOfClass(ConfusionCopy.class, owner.getBoundingBox().inflate(LEASH),
                c -> c.isAlive() && owner.getUUID().equals(c.ownerId()));
    }

    // ------------------------------------------------------------------ distracting hostiles

    /**
     * Each hostile mob within {@link #LURE_RADIUS} of the copies that is hunting {@code owner} switches, with
     * {@code chance}, to the copy nearest to it.
     */
    static void lure(ServerLevel level, ServerPlayer owner, List<ConfusionCopy> copies, float chance, RandomSource random) {
        if (copies.isEmpty()) {
            return;
        }
        var area = owner.getBoundingBox().inflate(LURE_RADIUS);
        for (ConfusionCopy c : copies) {
            area = area.minmax(c.getBoundingBox().inflate(LURE_RADIUS));
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m instanceof Enemy && m.isAlive() && m.getTarget() == owner)) {
            ConfusionCopy nearest = nearest(mob, copies);
            if (nearest != null && nearest.distanceToSqr(mob) <= LURE_RADIUS * LURE_RADIUS && random.nextFloat() < chance) {
                mob.setTarget(nearest);
            }
        }
    }

    /** The periodic re-roll, run by each copy for the hunters of its owner that are nearest to it. */
    static void lureNear(ServerLevel level, ServerPlayer owner, ConfusionCopy copy, float chance, RandomSource random) {
        List<ConfusionCopy> siblings = level.getEntitiesOfClass(ConfusionCopy.class, copy.getBoundingBox().inflate(LURE_RADIUS * 2.0),
                c -> c.isAlive() && owner.getUUID().equals(c.ownerId()));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, copy.getBoundingBox().inflate(LURE_RADIUS),
                m -> m instanceof Enemy && m.isAlive() && m.getTarget() == owner)) {
            if (nearest(mob, siblings) == copy && random.nextFloat() < chance) {
                mob.setTarget(copy);
            }
        }
    }

    @Nullable
    static ConfusionCopy nearest(LivingEntity from, List<ConfusionCopy> copies) {
        ConfusionCopy best = null;
        double bestD = Double.MAX_VALUE;
        for (ConfusionCopy c : copies) {
            double d = c.distanceToSqr(from);
            if (c.isAlive() && d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ fading

    /** The puff a copy leaves: smoke and a small burst of totem sparks ({@code popped}: broken by hits, else faded). */
    static void poof(ServerLevel level, Vec3 p, boolean popped) {
        level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.9, p.z, popped ? 16 : 10, 0.25, 0.5, 0.25, 0.02);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.x, p.y + 1.0, p.z, popped ? 14 : 8, 0.25, 0.45, 0.25, 0.2);
        Sfx.at(level, p, SoundEvents.ILLUSIONER_MIRROR_MOVE, popped ? 0.8F : 0.45F, 1.55F);
        if (popped) {
            Sfx.at(level, p, SoundEvents.FIRE_EXTINGUISH, 0.35F, 1.8F);
        }
    }
}
