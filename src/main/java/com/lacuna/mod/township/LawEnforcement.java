package com.lacuna.mod.township;

import com.lacuna.mod.logic.ReputationMath;
import com.lacuna.mod.logic.TownshipLaws;
import com.lacuna.mod.registry.ModTags;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;

/**
 * Law enforcement (spec 18.2), run from the ledger every 20 ticks: hostile players are attacked by the township's
 * golems; a law violation gets a 3 s action-bar warning, then −1 reputation every 10 s (5 s under Martial Law) while
 * the nearest guard walks over. Never instant death. Timers are volatile.
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

    static void tick(ServerLevel level, Township t) {
        long now = level.getGameTime();
        boolean martial = "martial_law".equals(t.policy);
        int period = martial ? 100 : 200;
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
            String key = t.id + "|" + p.getUUID();
            ReputationMath.Change violation = null;
            String warning = null;
            boolean inside = t.insideWalls(p.blockPosition());
            if (inside && t.hasLaw(TownshipLaws.NO_DRAWN_WEAPONS) && p.getMainHandItem().is(ModTags.Items.DRAWN_WEAPONS)) {
                violation = ReputationMath.Change.WEAPON_LAW;
                warning = "lacuna.township.warn.weapon";
            } else if (inside && t.hasLaw(TownshipLaws.GATES_CLOSE_AT_DUSK) && !TownshipLaws.gatesOpen(level.getDayTime()) && rep < 10) {
                violation = ReputationMath.Change.CURFEW_LAW;
                warning = "lacuna.township.warn.curfew";
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
                TownshipService.change(level, t, p, violation, 1);
                guards(level, p, 32.0).stream().findFirst().ifPresent(g -> g.getNavigation().moveTo(p, 0.7));
            }
        }
    }

    /** At most 40 of the township's golems near a player (bounded query). */
    static List<IronGolem> guards(ServerLevel level, ServerPlayer p, double range) {
        List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, new AABB(p.blockPosition()).inflate(range),
                g -> g.isAlive() && !g.isPlayerCreated());
        golems.sort(java.util.Comparator.comparingDouble(g -> g.distanceToSqr(p)));
        return golems.size() > 40 ? golems.subList(0, 40) : golems;
    }
}
