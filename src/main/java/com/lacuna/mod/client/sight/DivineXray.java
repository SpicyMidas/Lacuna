package com.lacuna.mod.client.sight;

import com.lacuna.mod.Balance;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.compat.ShaderPackCompat;
import com.lacuna.mod.client.render.SectionSweep;
import com.lacuna.mod.client.render.StencilSupport;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Divine Sight, X-ray mode: the client state behind {@link DivineXrayRenderer}.
 * <p>
 * Two progressive sweeps run over the loaded chunk sections around the camera, nearest sections first and with a
 * bounded number of block reads per tick: one finds Divine Glass (cheap: sections whose palette cannot contain glass
 * are skipped), the other finds {@code lacuna:divine_sight_targets} and only runs while some glass is actually on
 * screen. Finished sweeps replace the cached results; living entities near the camera are refreshed every few ticks.
 * The stencil buffer of the main render target is enabled here ({@link StencilSupport}), outside any frame. Any
 * failure disables the {@value #FEATURE} feature and {@link DivineSight} falls back to the outline mode.
 */
public final class DivineXray {
    public static final String FEATURE = "divine_xray";
    /** Divine Glass farther than this from the camera is not turned into a window. */
    static final int GLASS_RANGE = 24;
    static final int MAX_GLASS = 1024;
    static final int MAX_ENTITIES = Balance.DIVINE_SIGHT_MAX_ENTITY_BOXES;
    /** Block reads per client tick shared by both sweeps (about a quarter of a millisecond). */
    private static final int BLOCK_BUDGET = 24_576;
    private static final int GLASS_SWEEP_EVERY = 4;
    private static final int ENTITY_REFRESH_EVERY = 2;
    /** Targets are forgotten when no glass has been on screen for this long. */
    private static final int IDLE_TICKS = 60;

    private static List<BlockPos> glass = List.of();
    private static List<BlockPos> targets = List.of();
    private static List<LivingEntity> entities = List.of();

    private static SectionSweep glassSweep;
    private static SectionSweep targetSweep;
    private static int ticks;
    private static int glassSweepStart;
    private static int lastVisibleTick = -IDLE_TICKS;

    private DivineXray() {}

    public static void register(IEventBus forgeBus) {
        forgeBus.addListener(DivineXray::onClientTick);
        forgeBus.addListener(DivineXrayRenderer::onRenderLevel);
        forgeBus.addListener(DivineXray::onLogout);
    }

    /**
     * True when the X-ray window replaces the outline mode (config, feature health and stencil availability; off while
     * an Iris / Oculus shader pack is in use, whose buffers the stencil window cannot draw into).
     */
    public static boolean isActive() {
        return LacunaConfig.get(LacunaConfig.DIVINE_SIGHT_ENABLED)
                && LacunaClientConfig.get(LacunaClientConfig.SIGHT_MODE) == LacunaClientConfig.SightMode.XRAY
                && !SafeRun.isDisabled(FEATURE)
                && !StencilSupport.failed()
                && !ShaderPackCompat.active();
    }

    static List<BlockPos> glass() {
        return glass;
    }

    static List<BlockPos> targets() {
        return targets;
    }

    static List<LivingEntity> entities() {
        return entities;
    }

    /** Stencil mask reserved for the window, or 0 when the main target has no stencil yet. */
    static int stencilMask() {
        return StencilSupport.mask();
    }

    static int range() {
        return LacunaClientConfig.get(LacunaClientConfig.XRAY_RANGE);
    }

    /** Called by the renderer each frame a glass window is inside the view frustum. */
    static void markVisible() {
        if (ticks - lastVisibleTick > 100) {
            // the glass "opens": a faint crystalline chime when a window comes back into view after a while
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 1.8F);
            }
        }
        lastVisibleTick = ticks;
    }

    static void reset() {
        glass = List.of();
        targets = List.of();
        entities = List.of();
        glassSweep = null;
        targetSweep = null;
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ticks++;
        if (!isActive()) {
            if (!glass.isEmpty() || !targets.isEmpty() || !entities.isEmpty()) {
                reset();
            }
            return;
        }
        SafeRun.run(FEATURE, DivineXray::tick, DivineXray::reset);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (level == null || mc.player == null || !camera.isInitialized()) {
            reset();
            return;
        }
        if (!StencilSupport.ensure(mc)) {
            return;
        }
        Vec3 eye = camera.getPosition();
        int budget = BLOCK_BUDGET;

        // 1) Divine Glass around the camera (restarted every few ticks; usually finishes within one tick)
        if (glassSweep == null && ticks - glassSweepStart >= GLASS_SWEEP_EVERY) {
            glassSweep = new SectionSweep(level, eye, GLASS_RANGE, s -> s.is(ModTags.Blocks.DIVINE_GLASS));
            glassSweepStart = ticks;
        }
        if (glassSweep != null) {
            budget = glassSweep.run(level, budget);
            if (glassSweep.done()) {
                glass = glassSweep.result(MAX_GLASS);
                glassSweep = null;
            }
        }

        // 2) targets, only while a window is (or just was) on screen
        boolean wanted = !glass.isEmpty() && ticks - lastVisibleTick < IDLE_TICKS;
        if (!wanted) {
            targets = List.of();
            entities = List.of();
            targetSweep = null;
            return;
        }
        if (targetSweep == null) {
            targetSweep = new SectionSweep(level, eye, range(), s -> s.is(ModTags.Blocks.DIVINE_SIGHT_TARGETS));
        }
        targetSweep.run(level, Math.max(budget, BLOCK_BUDGET / 4));
        if (targetSweep.done()) {
            targets = targetSweep.result(LacunaClientConfig.get(LacunaClientConfig.XRAY_MAX_BLOCKS));
            targetSweep = null;
        }

        // 3) living entities (bounded AABB query, side <= 64)
        if (ticks % ENTITY_REFRESH_EVERY == 0) {
            double r = Math.min(range(), Balance.MAX_ENTITY_QUERY_SIDE / 2.0);
            Entity viewer = camera.getEntity();
            boolean detached = camera.isDetached();
            List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(r),
                    e -> e.isAlive() && !e.isSpectator() && (detached || e != viewer)
                            && e.distanceToSqr(eye) <= r * r));
            found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
            entities = found.size() > MAX_ENTITIES ? List.copyOf(found.subList(0, MAX_ENTITIES)) : List.copyOf(found);
        }
    }
}
