package com.lacuna.mod.debug;

import com.lacuna.mod.Lacuna;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Scripted dedicated-server session used by {@code tools/smoke.sh} (spec section 4, gate 6).
 * <p>
 * Only active when the JVM is started with {@code -Dlacuna.smoke=phase1|phase2}. Reads console commands from
 * {@code lacuna_smoke_<phase>.txt} in the server directory, runs one command every few ticks ({@code @wait N}
 * pauses), then saves and stops the server. Phase 1 writes a persistence marker that phase 2 verifies.
 */
public final class SmokeRunner {
    private static final String MARKER = "lacuna_smoke_marker";
    private static final int COMMAND_INTERVAL = 5;
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static String phase = "";
    private static int wait;
    private static boolean finished;

    private SmokeRunner() {}

    public static boolean isActive() {
        String p = System.getProperty("lacuna.smoke", "");
        return p.equals("phase1") || p.equals("phase2");
    }

    public static void register(IEventBus gameBus) {
        phase = System.getProperty("lacuna.smoke", "");
        gameBus.addListener(SmokeRunner::onStarted);
        gameBus.addListener(SmokeRunner::onTick);
    }

    private static void onStarted(ServerStartedEvent event) {
        Path file = FMLPaths.GAMEDIR.get().resolve("lacuna_smoke_" + phase + ".txt");
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    QUEUE.add(trimmed);
                }
            }
            Lacuna.LOGGER.info("[SMOKE] {} started with {} commands", phase, QUEUE.size());
        } catch (IOException e) {
            Lacuna.LOGGER.info("[SMOKE] no command file {}, running persistence check only", file);
        }
        wait = 40;
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (finished) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (wait > 0) {
            wait--;
            return;
        }
        String next = QUEUE.poll();
        if (next != null) {
            if (next.startsWith("@wait")) {
                try {
                    wait = Integer.parseInt(next.substring(5).trim());
                } catch (NumberFormatException e) {
                    wait = 20;
                }
                return;
            }
            Lacuna.LOGGER.info("[SMOKE] > {}", next);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), next);
            wait = COMMAND_INTERVAL;
            return;
        }
        finished = true;
        Marker marker = server.overworld().getDataStorage().computeIfAbsent(Marker.factory(), MARKER);
        if (phase.equals("phase1")) {
            marker.value = "persisted";
            marker.setDirty();
            Lacuna.LOGGER.info("[SMOKE] phase1 marker written");
        } else {
            boolean ok = "persisted".equals(marker.value);
            Lacuna.LOGGER.info("[SMOKE] persistence check {}", ok ? "PASS" : "FAIL (marker missing)");
        }
        server.saveEverything(true, true, true);
        Lacuna.LOGGER.info("[SMOKE] {} complete, stopping server", phase);
        server.halt(false);
    }

    private static final class Marker extends SavedData {
        private String value = "";

        static SavedData.Factory<Marker> factory() {
            return new SavedData.Factory<>(Marker::new, Marker::load);
        }

        static Marker load(CompoundTag tag, HolderLookup.Provider provider) {
            Marker m = new Marker();
            m.value = tag.getString("value");
            return m;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putString("value", value);
            return tag;
        }
    }
}
