package com.lacuna.mod.client.sight;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.client.render.OutlineRenderer;
import com.lacuna.mod.client.render.OutlineSet;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Divine Sight (spec M1), outline mode: when the camera ray passes through Divine Glass within 6 blocks, targets
 * behind the glass inside a 50 degree cone get outlines drawn through walls. Client-only, throttled and bounded; any
 * failure disables the feature for the session (SafeRun). The default X-ray window mode lives in {@link DivineXray};
 * this mode is used when the client config picks it or when the window is unavailable (no stencil, or it failed).
 */
public final class DivineSight {
    public static final String FEATURE = "divine_sight";
    private static final String SET_KEY = "divine_sight";
    private static final int ORE_COLOR = 0xFFD23C;
    private static final int CONTAINER_COLOR = 0xFFFFFF;
    private static final int SPAWNER_COLOR = 0xB06BFF;
    static final int HOSTILE_COLOR = 0xFF4040;
    static final int PASSIVE_COLOR = 0x40FF60;
    private static final double SAMPLE_STEP = 0.8;

    private static int ticker;

    private DivineSight() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!LacunaConfig.get(LacunaConfig.DIVINE_SIGHT_ENABLED) || SafeRun.isDisabled(FEATURE) || DivineXray.isActive()) {
            // disabled, or the X-ray window (DivineXray) replaces the outlines
            OutlineRenderer.clear(SET_KEY);
            return;
        }
        if (++ticker % Balance.DIVINE_SIGHT_SCAN_INTERVAL != 0) {
            return;
        }
        SafeRun.run(FEATURE, DivineSight::scan, () -> OutlineRenderer.clear(SET_KEY));
    }

    private static void scan() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            OutlineRenderer.clear(SET_KEY);
            return;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).normalize();
        double glassDist = findGlass(level, eye, look);
        if (glassDist < 0) {
            OutlineRenderer.clear(SET_KEY);
            return;
        }
        int range = LacunaConfig.get(LacunaConfig.DIVINE_SIGHT_RANGE);
        int maxBoxes = LacunaConfig.get(LacunaConfig.DIVINE_SIGHT_MAX_BOXES);
        double tan = Math.tan(Math.toRadians(Balance.DIVINE_SIGHT_CONE_DEGREES / 2.0));
        double cosHalf = Math.cos(Math.toRadians(Balance.DIVINE_SIGHT_CONE_DEGREES / 2.0));
        OutlineSet set = new OutlineSet(maxBoxes, Balance.DIVINE_SIGHT_MAX_ENTITY_BOXES);

        // Orthonormal basis around the view direction.
        Vec3 helper = Math.abs(look.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = look.cross(helper).normalize();
        Vec3 v = look.cross(u).normalize();

        Set<BlockPos> visited = new LinkedHashSet<>();
        double start = glassDist + 0.5;
        double end = glassDist + range;
        outer:
        for (double t = start; t <= end; t += SAMPLE_STEP) {
            double r = t * tan;
            for (double i = -r; i <= r; i += SAMPLE_STEP) {
                for (double j = -r; j <= r; j += SAMPLE_STEP) {
                    if (i * i + j * j > r * r) {
                        continue;
                    }
                    if (visited.size() >= Balance.DIVINE_SIGHT_MAX_POSITIONS) {
                        break outer;
                    }
                    BlockPos pos = BlockPos.containing(eye.add(look.scale(t)).add(u.scale(i)).add(v.scale(j)));
                    if (!visited.add(pos) || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.is(ModTags.Blocks.DIVINE_SIGHT_TARGETS)) {
                        continue;
                    }
                    if (!set.addBox(new AABB(pos), colorFor(state))) {
                        break outer;
                    }
                }
            }
        }

        // Living entities inside the cone (bounded AABB query, side <= 64).
        double reach = Math.min(end, Balance.MAX_ENTITY_QUERY_SIDE / 2.0);
        Vec3 far = eye.add(look.scale(reach));
        AABB query = new AABB(eye, far).inflate(Math.min(reach * tan, 16.0));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, query, e -> e != player && e.isAlive())) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along < glassDist || along > end) {
                continue;
            }
            if (to.normalize().dot(look) < cosHalf) {
                continue;
            }
            if (!set.addEntity(e, e instanceof Enemy ? HOSTILE_COLOR : PASSIVE_COLOR)) {
                break;
            }
        }

        if (set.isEmpty()) {
            OutlineRenderer.clear(SET_KEY);
        } else {
            OutlineRenderer.put(SET_KEY, set);
        }
    }

    /** Distance along the view ray to the first Divine Glass within 6 blocks, or -1 (stops at opaque blocks). */
    private static double findGlass(ClientLevel level, Vec3 eye, Vec3 look) {
        BlockPos last = null;
        for (double t = 0.0; t <= Balance.DIVINE_SIGHT_GLASS_DISTANCE; t += 0.1) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(t)));
            if (pos.equals(last)) {
                continue;
            }
            last = pos;
            if (!level.hasChunkAt(pos)) {
                return -1;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(ModTags.Blocks.DIVINE_GLASS)) {
                return t;
            }
            if (state.canOcclude()) {
                return -1;
            }
        }
        return -1;
    }

    static int colorFor(BlockState state) {
        if (state.is(ModTags.Blocks.C_ORES)) {
            return ORE_COLOR;
        }
        if (state.is(Blocks.SPAWNER) || state.is(Blocks.TRIAL_SPAWNER)) {
            return SPAWNER_COLOR;
        }
        if (state.hasBlockEntity()) {
            return CONTAINER_COLOR;
        }
        return ORE_COLOR;
    }
}
