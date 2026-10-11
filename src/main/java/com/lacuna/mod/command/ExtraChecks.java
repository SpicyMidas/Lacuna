package com.lacuna.mod.command;

import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Milestone-specific selftest checks; grows with each milestone. */
final class ExtraChecks {
    private ExtraChecks() {}

    static void registries(List<String> failures, int[] checks) {}

    static void tags(List<String> failures, int[] checks) {}

    static void world(MinecraftServer server, List<String> failures, int[] checks) {}
}
