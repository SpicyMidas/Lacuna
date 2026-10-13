package com.lacuna.mod.village;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.VillageLaws;
import com.lacuna.mod.registry.ModTags;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;

/**
 * Law enforcement (spec 18.2, revision 2), run every second for each village with a player inside: Hostile players
 * are attacked by the village's iron golems; a law violation (drawn weapon inside the village, or a stranger
 * outdoors during the curfew) gets a 3 s action-bar warning, then −1 reputation every 10 s (5 s under Martial Law)
 * while the nearest golem walks over. Never instant death. Timers are volatile.
 */
public final class LawEnforcement {
    private static final Map<String, Long> WARNED = new ConcurrentHashMap<>();
    private static final Map<String, Long> PENALIZED = new ConcurrentHashMap<>();
    private static final int WARNING_TICKS = 60;

    private LawEnforcement() {}

    public static void clearVolatile() {
        WARNED.clear();
        PENALIZED.clear();
    }

    static void tick(ServerLevel level, VillageRecord t) {
        long now = level.getGameTime();
        boolean martial = "martial_law".equals(t.policy);
        int period = martial ? 100 : 200;
        boolean curfew = t.hasLaw(VillageLaws.CURFEW) && VillageLaws.isCurfew(level.getDayTime());
        List<ServerPlayer> players = level.getPlayers(p -> !p.isCreative() && !p.isSpectator() && p.isAlive()
                && t.contains(level.dimension(), p.blockPosition()));
        for (ServerPlayer p : players) {
            int rep = t.reputation(p.getUUID());
            if (ReputationMath.tier(rep) == ReputationMath.Tier.HOSTILE) {
                guards(level, p, 16.0).forEach(g -> {
                    if (g.getTarget() == null) {
                        g.setTarget(p);
                    }
                });
            }
            String key = t.key + "|" + p.getUUID();
            ReputationMath.Change violation = null;
            String warning = null;
            if (t.hasLaw(VillageLaws.NO_DRAWN_WEAPONS) && p.getMainHandItem().is(ModTags.Items.DRAWN_WEAPONS) && !t.isFounder(p.getUUID())) {
                violation = ReputationMath.Change.WEAPON_LAW;
                warning = "lacuna.village.warn.weapon";
            } else if (curfew && VillageService.isStranger(t, p.getUUID()) && outdoors(level, p)) {
                violation = ReputationMath.Change.CURFEW_LAW;
                warning = "lacuna.village.warn.curfew";
            }
            if (violation == null) {
                WARNED.remove(key);
                continue;
            }
            Long since = WARNED.putIfAbsent(key, now);
            if (since == null || now - since < WARNING_TICKS) {
                p.displayClientMessage(Component.translatable(warning).withStyle(ChatFormatting.YELLOW), true);
                continue;
            }
            Long last = PENALIZED.get(key);
            if (last == null || now - last >= period) {
                PENALIZED.put(key, now);
                VillageService.change(level, t, p, violation, 1);
                guards(level, p, 32.0).stream().findFirst().ifPresent(g -> g.getNavigation().moveTo(p, 0.7));
            }
        }
    }

    /** Outdoors = under the open sky (a roof, even leaves, counts as shelter). */
    static boolean outdoors(ServerLevel level, ServerPlayer p) {
        return level.canSeeSky(BlockPos.containing(p.getEyePosition()));
    }

    /** At most 40 of the village's golems near a player (bounded query; player-built golems are not guards). */
    static List<IronGolem> guards(ServerLevel level, ServerPlayer p, double range) {
        List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, new AABB(p.blockPosition()).inflate(range),
                g -> g.isAlive() && !g.isPlayerCreated());
        golems.sort(java.util.Comparator.comparingDouble(g -> g.distanceToSqr(p)));
        return golems.size() > 40 ? golems.subList(0, 40) : golems;
    }
}
