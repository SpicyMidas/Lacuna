package com.lacuna.mod.client.debug;

import com.lacuna.mod.Lacuna;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.apache.commons.io.FileUtils;

/**
 * Development-only scripted client used to screenshot visual effects on a headless machine
 * ({@code ./gradlew runClient -PclientDemo=path/to/script.txt} under xvfb). Never active in normal play:
 * {@link #register} does nothing unless the {@code lacuna.clientDemo} system property names a script.
 * <p>
 * Script lines: {@code world <name> flat|normal|biome:<biome id> [seed]}, {@code wait <ticks>}, {@code cmd <command>},
 * {@code shot <name>}, {@code hud on|off}, {@code look <yaw> <pitch>}, {@code use on|off}, {@code attack},
 * {@code sneak on|off}, {@code forward on|off}, {@code select <0-8>}, {@code camera first|back|front},
 * {@code log <text>}, {@code clearchat}, {@code guiscale <0-4>}, {@code quit},
 * {@code fx <kind> x y z [x2 y2 z2] [p0 p1 p2]} (client effect handler, ~ offsets allowed),
 * {@code particle <id> x y z [count]}, {@code key <glfw key code>} (to the open screen).
 * Blank lines and lines starting with # are ignored. Screenshots land in run/screenshots/.
 */
public final class ClientDemo {
    private static final List<String> SCRIPT = new ArrayList<>();
    private static int line;
    private static int waitTicks;
    private static boolean worldRequested;
    private static String lastScreen = "";

    private ClientDemo() {}

    public static void register(IEventBus forgeBus) {
        String path = System.getProperty("lacuna.clientDemo", "");
        if (path.isBlank()) {
            return;
        }
        try {
            for (String raw : Files.readAllLines(Path.of(path))) {
                String l = raw.strip();
                if (!l.isEmpty() && !l.startsWith("#")) {
                    SCRIPT.add(l);
                }
            }
        } catch (IOException e) {
            Lacuna.LOGGER.error("[DEMO] cannot read script {}", path, e);
            return;
        }
        Lacuna.LOGGER.info("[DEMO] loaded {} script lines from {}", SCRIPT.size(), path);
        forgeBus.addListener(ClientDemo::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        String screen = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
        if (!screen.equals(lastScreen)) {
            Lacuna.LOGGER.info("[DEMO] screen: {}", screen);
            lastScreen = screen;
        }
        if (line >= SCRIPT.size()) {
            return;
        }
        String[] parts = SCRIPT.get(line).split(" ", 2);
        String op = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : "";
        if (op.equals("world")) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                createWorld(mc, arg.split(" "));
            }
            if (mc.level != null && mc.player != null && mc.screen == null) {
                Lacuna.LOGGER.info("[DEMO] in world");
                line++;
                waitTicks = 40;
            }
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            // a pause/death screen would freeze the script: close it
            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                mc.setScreen(null);
            }
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        line++;
        try {
            switch (op) {
                case "wait" -> waitTicks = Integer.parseInt(arg.trim());
                case "cmd" -> mc.player.connection.sendCommand(arg.startsWith("/") ? arg.substring(1) : arg);
                case "shot" -> Screenshot.grab(mc.gameDirectory, arg.trim() + ".png", mc.getMainRenderTarget(),
                        msg -> Lacuna.LOGGER.info("[DEMO] shot {}: {}", arg, msg.getString()));
                case "hud" -> mc.options.hideGui = arg.trim().equals("off");
                case "look" -> {
                    String[] a = arg.trim().split(" ");
                    mc.player.setYRot(Float.parseFloat(a[0]));
                    mc.player.setXRot(Float.parseFloat(a[1]));
                    mc.player.yRotO = mc.player.getYRot();
                    mc.player.xRotO = mc.player.getXRot();
                }
                case "use" -> mc.options.keyUse.setDown(arg.trim().equals("on"));
                case "sneak" -> mc.options.keyShift.setDown(arg.trim().equals("on"));
                case "forward" -> mc.options.keyUp.setDown(arg.trim().equals("on"));
                case "camera" -> mc.options.setCameraType(switch (arg.trim()) {
                    case "back" -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                    case "front" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                    default -> net.minecraft.client.CameraType.FIRST_PERSON;
                });
                case "attack" -> KeyMapping.click(mc.options.keyAttack.getKey());
                // body <degrees>: turn the body that far from the head (up to 50: an oblique view of a pose in third person)
                case "body" -> {
                    mc.player.yBodyRot = mc.player.getYRot() + Float.parseFloat(arg.trim());
                    mc.player.yBodyRotO = mc.player.yBodyRot;
                }
                case "select" -> mc.player.getInventory().selected = Integer.parseInt(arg.trim());
                // guiscale <0-4>: change the GUI scale (screens re-lay themselves out), e.g. to check a screen at scale 4
                case "guiscale" -> {
                    mc.options.guiScale().set(Integer.parseInt(arg.trim()));
                    mc.resizeDisplay();
                }
                case "log" -> Lacuna.LOGGER.info("[DEMO] {}", arg);
                case "clearchat" -> mc.gui.getChat().clearMessages(false);
                // key <glfw code>: a key press sent to the open screen (Tab/Enter drive its buttons, 256 = Escape)
                case "key" -> {
                    if (mc.screen != null) {
                        mc.screen.keyPressed(Integer.parseInt(arg.trim()), 0, 0);
                    }
                }
                case "fx" -> spawnFx(mc, arg.trim().split(" "));
                case "particle" -> {
                    // particle <id> <x> <y> <z> [count]: client-side burst of a registered particle type
                    String[] a = arg.trim().split(" ");
                    var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(net.minecraft.resources.ResourceLocation.parse(a[0]));
                    if (type instanceof net.minecraft.core.particles.SimpleParticleType simple) {
                        net.minecraft.world.phys.Vec3 p = pos(mc, a, 1);
                        int n = a.length > 4 ? Integer.parseInt(a[4]) : 20;
                        for (int i = 0; i < n; i++) {
                            mc.level.addParticle(simple, p.x + (Math.random() - 0.5), p.y + (Math.random() - 0.5), p.z + (Math.random() - 0.5),
                                    (Math.random() - 0.5) * 0.1, Math.random() * 0.1, (Math.random() - 0.5) * 0.1);
                        }
                    }
                }
                case "quit" -> {
                    Lacuna.LOGGER.info("[DEMO] script complete");
                    mc.stop();
                }
                default -> Lacuna.LOGGER.warn("[DEMO] unknown op {}", op);
            }
        } catch (RuntimeException e) {
            Lacuna.LOGGER.error("[DEMO] line {} failed: {}", line, SCRIPT.get(line - 1), e);
        }
    }

    /** {@code fx <kind> x y z [x2 y2 z2] [p0 p1 p2]}: runs a client effect handler locally; coordinates accept ~ offsets. */
    private static void spawnFx(Minecraft mc, String[] a) {
        net.minecraft.world.phys.Vec3 p1 = pos(mc, a, 1);
        net.minecraft.world.phys.Vec3 p2 = a.length >= 7 ? pos(mc, a, 4) : p1;
        float q0 = a.length > 7 ? Float.parseFloat(a[7]) : 0.0F;
        float q1 = a.length > 8 ? Float.parseFloat(a[8]) : 0.0F;
        float q2 = a.length > 9 ? Float.parseFloat(a[9]) : 0.0F;
        int entity = mc.player != null ? mc.player.getId() : -1;
        com.lacuna.mod.client.fx.FxHandlers.handle(new com.lacuna.mod.network.FxPayload(a[0], entity, p1, p2, q0, q1, q2, 0xFFFFFFFF));
    }

    private static net.minecraft.world.phys.Vec3 pos(Minecraft mc, String[] a, int i) {
        double[] base = {mc.player.getX(), mc.player.getY(), mc.player.getZ()};
        double[] out = new double[3];
        for (int k = 0; k < 3; k++) {
            String s = a[i + k];
            out[k] = s.startsWith("~") ? base[k] + (s.length() > 1 ? Double.parseDouble(s.substring(1)) : 0.0) : Double.parseDouble(s);
        }
        return new net.minecraft.world.phys.Vec3(out[0], out[1], out[2]);
    }

    private static void createWorld(Minecraft mc, String[] a) {
        String name = a.length > 0 && !a[0].isBlank() ? a[0] : "lacuna_demo";
        boolean flat = a.length > 1 && a[1].equals("flat");
        long seed = a.length > 2 ? a[2].hashCode() : 12345L;
        try {
            FileUtils.deleteDirectory(mc.gameDirectory.toPath().resolve("saves").resolve(name).toFile());
        } catch (IOException e) {
            Lacuna.LOGGER.warn("[DEMO] could not delete old world {}", name, e);
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(seed, !flat, false);
        // "biome:<id>" instead of flat|normal: a normal overworld made of that one biome (e.g. biome:minecraft:taiga)
        String biome = a.length > 1 && a[1].startsWith("biome:") ? a[1].substring("biome:".length()) : null;
        Lacuna.LOGGER.info("[DEMO] creating world {} (flat={}, biome={})", name, flat, biome);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, options, registries -> {
            var dimensions = registries.registryOrThrow(Registries.WORLD_PRESET)
                    .getHolderOrThrow(flat ? WorldPresets.FLAT : WorldPresets.NORMAL).value().createWorldDimensions();
            if (biome == null) {
                return dimensions;
            }
            var holder = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(
                    net.minecraft.resources.ResourceKey.create(Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(biome)));
            var noise = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
            return dimensions.replaceOverworldGenerator(registries, new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                    new net.minecraft.world.level.biome.FixedBiomeSource(holder), noise));
        }, new TitleScreen());
    }
}
