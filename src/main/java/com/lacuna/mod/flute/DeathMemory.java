package com.lacuna.mod.flute;

import com.lacuna.mod.Balance;
import com.lacuna.mod.registry.ModTags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Volatile per-level ring buffer (max 64) of recent non-player, non-boss deaths, used by the Whisperbone Flute.
 * Cleared on server stop; never saved.
 */
public final class DeathMemory {
    public record Death(Vec3 pos, long gameTime, float maxHealth, float attack) {}

    private static final Map<ResourceKey<Level>, Deque<Death>> RINGS = new ConcurrentHashMap<>();

    private DeathMemory() {}

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead instanceof Player || dead instanceof EchoEntity
                || dead.getType().is(ModTags.Entities.CANNOT_BE_BANISHED)) {
            return;
        }
        float attack = dead.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? (float) dead.getAttributeValue(Attributes.ATTACK_DAMAGE) : 2.0F;
        Deque<Death> ring = RINGS.computeIfAbsent(dead.level().dimension(), k -> new ArrayDeque<>());
        synchronized (ring) {
            ring.addLast(new Death(dead.position(), dead.level().getGameTime(), dead.getMaxHealth(), attack));
            while (ring.size() > Balance.FLUTE_RING_SIZE) {
                ring.removeFirst();
            }
        }
    }

    /** Removes and returns up to {@code max} most recent deaths within radius/time of the given point. */
    public static List<Death> take(Level level, Vec3 at, int max) {
        Deque<Death> ring = RINGS.get(level.dimension());
        List<Death> out = new ArrayList<>();
        if (ring == null) {
            return out;
        }
        long now = level.getGameTime();
        double r2 = Balance.FLUTE_DEATH_RADIUS * (double) Balance.FLUTE_DEATH_RADIUS;
        synchronized (ring) {
            Iterator<Death> it = ring.descendingIterator();
            while (it.hasNext() && out.size() < max) {
                Death d = it.next();
                if (now - d.gameTime() <= Balance.FLUTE_DEATH_WINDOW && d.pos().distanceToSqr(at) <= r2) {
                    out.add(d);
                    it.remove();
                }
            }
        }
        return out;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        RINGS.clear();
    }
}
