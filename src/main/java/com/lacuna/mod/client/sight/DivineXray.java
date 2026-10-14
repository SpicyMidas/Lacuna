package com.lacuna.mod.client.sight;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.registry.ModTags;
import com.lacuna.mod.util.SafeRun;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.StencilManager;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Divine Sight, X-ray mode: the client state behind {@link DivineXrayRenderer}.
 * <p>
 * Two progressive sweeps run over the loaded chunk sections around the camera, nearest sections first and with a
 * bounded number of block reads per tick: one finds Divine Glass (cheap: sections whose palette cannot contain glass
 * are skipped), the other finds {@code lacuna:divine_sight_targets} and only runs while some glass is actually on
 * screen. Finished sweeps replace the cached results; living entities near the camera are refreshed every few ticks.
 * The stencil buffer of the main render target is enabled here, outside any frame. Any failure disables the
 * {@value #FEATURE} feature and {@link DivineSight} falls back to the outline mode.
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

    private static Sweep glassSweep;
    private static Sweep targetSweep;
    private static int ticks;
    private static int glassSweepStart;
    private static int lastVisibleTick = -IDLE_TICKS;
    private static int stencilBit = -2;
    private static boolean chainsReloaded;

    private DivineXray() {}

    public static void register(IEventBus forgeBus) {
        forgeBus.addListener(DivineXray::onClientTick);
        forgeBus.addListener(DivineXrayRenderer::onRenderLevel);
        forgeBus.addListener(DivineXray::onLogout);
    }

    /** True when the X-ray window replaces the outline mode (config, feature health and stencil availability). */
    public static boolean isActive() {
        return LacunaConfig.get(LacunaConfig.DIVINE_SIGHT_ENABLED)
                && LacunaClientConfig.get(LacunaClientConfig.SIGHT_MODE) == LacunaClientConfig.SightMode.XRAY
                && !SafeRun.isDisabled(FEATURE)
                && stencilBit != -1;
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
        return stencilBit >= 0 && Minecraft.getInstance().getMainRenderTarget().isStencilEnabled() ? 1 << stencilBit : 0;
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
        if (!ensureStencil(mc)) {
            return;
        }
        Vec3 eye = camera.getPosition();
        int budget = BLOCK_BUDGET;

        // 1) Divine Glass around the camera (restarted every few ticks; usually finishes within one tick)
        if (glassSweep == null && ticks - glassSweepStart >= GLASS_SWEEP_EVERY) {
            glassSweep = new Sweep(level, eye, GLASS_RANGE, s -> s.is(ModTags.Blocks.DIVINE_GLASS));
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
            targetSweep = new Sweep(level, eye, range(), s -> s.is(ModTags.Blocks.DIVINE_SIGHT_TARGETS));
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

    /**
     * Enables the stencil buffer on the main render target (once, between frames). Fabulous graphics copies depth
     * from the main target into its own targets, which only works when both have the same depth-stencil format: the
     * post chains are rebuilt once so that their targets get a stencil too (NeoForge's PostChain mirrors the screen).
     */
    private static boolean ensureStencil(Minecraft mc) {
        if (stencilBit == -2) {
            stencilBit = StencilManager.reserveBit();
            if (stencilBit < 0) {
                Lacuna.LOGGER.warn("Divine Sight: no free stencil bit, using the outline mode");
                return false;
            }
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (!main.isStencilEnabled()) {
            main.enableStencil();
            Lacuna.LOGGER.info("Divine Sight: stencil buffer enabled on the main render target");
        }
        RenderTarget translucent = mc.levelRenderer.getTranslucentTarget();
        if (translucent != null && !translucent.isStencilEnabled() && !chainsReloaded) {
            chainsReloaded = true;
            mc.levelRenderer.onResourceManagerReload(mc.getResourceManager());
        }
        return main.isStencilEnabled();
    }

    /** Progressive, budgeted scan of the chunk sections inside a sphere, nearest sections first. */
    private static final class Sweep {
        private final long[] sections;
        private final Vec3 center;
        private final double radiusSq;
        private final Predicate<BlockState> match;
        private final List<BlockPos> found = new ArrayList<>();
        private int index;

        Sweep(ClientLevel level, Vec3 center, int radius, Predicate<BlockState> match) {
            this.center = center;
            this.radiusSq = (double) radius * radius;
            this.match = match;
            int cx = SectionPos.blockToSectionCoord(center.x);
            int cy = SectionPos.blockToSectionCoord(center.y);
            int cz = SectionPos.blockToSectionCoord(center.z);
            int r = (radius >> 4) + 1;
            int minY = Math.max(cy - r, level.getMinSection());
            int maxY = Math.min(cy + r, level.getMaxSection() - 1);
            List<long[]> list = new ArrayList<>();
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        double d = sectionDistanceSq(x, y, z);
                        if (d <= radiusSq) {
                            list.add(new long[] {SectionPos.asLong(x, y, z), (long) d});
                        }
                    }
                }
            }
            list.sort(Comparator.comparingLong(a -> a[1]));
            this.sections = new long[list.size()];
            for (int i = 0; i < sections.length; i++) {
                sections[i] = list.get(i)[0];
            }
        }

        /** Squared distance from the centre to the closest point of the section. */
        private double sectionDistanceSq(int sx, int sy, int sz) {
            double dx = axis(center.x, sx << 4);
            double dy = axis(center.y, sy << 4);
            double dz = axis(center.z, sz << 4);
            return dx * dx + dy * dy + dz * dz;
        }

        private static double axis(double c, int min) {
            return c < min ? min - c : c > min + 16 ? c - (min + 16) : 0.0;
        }

        boolean done() {
            return index >= sections.length;
        }

        /** Scans sections until the budget runs out; returns the budget left. */
        int run(ClientLevel level, int budget) {
            while (index < sections.length && budget > 0) {
                long packed = sections[index++];
                int sx = SectionPos.x(packed);
                int sy = SectionPos.y(packed);
                int sz = SectionPos.z(packed);
                budget -= 16;
                LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, false);
                if (chunk == null) {
                    continue;
                }
                int idx = level.getSectionIndexFromSectionY(sy);
                LevelChunkSection[] all = chunk.getSections();
                if (idx < 0 || idx >= all.length) {
                    continue;
                }
                LevelChunkSection section = all[idx];
                if (section == null || section.hasOnlyAir() || !section.maybeHas(match)) {
                    continue;
                }
                budget -= 4096;
                int bx = sx << 4;
                int by = sy << 4;
                int bz = sz << 4;
                for (int y = 0; y < 16; y++) {
                    double dy = by + y + 0.5 - center.y;
                    for (int z = 0; z < 16; z++) {
                        double dz = bz + z + 0.5 - center.z;
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.isAir() || !match.test(state)) {
                                continue;
                            }
                            double dx = bx + x + 0.5 - center.x;
                            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                                found.add(new BlockPos(bx + x, by + y, bz + z));
                            }
                        }
                    }
                }
            }
            return budget;
        }

        List<BlockPos> result(int max) {
            found.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(center)));
            return List.copyOf(found.size() > max ? found.subList(0, max) : found);
        }
    }
}
