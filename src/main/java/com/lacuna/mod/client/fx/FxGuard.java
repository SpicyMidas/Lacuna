package com.lacuna.mod.client.fx;

import com.lacuna.mod.Lacuna;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;

/**
 * Crash shield for purely cosmetic client code (effects, screen feedback, effect payload handlers).
 * <p>
 * Unlike {@link com.lacuna.mod.util.SafeRun} it never switches a feature off for the session: a failure only costs the
 * piece that failed (one effect, one payload, one frame). The first failure of each key is logged with its stack trace,
 * later ones are counted and summarised at most every {@value #SUMMARY_MS} ms. Visual code must not be able to take every
 * other effect down with it: a single bad effect in one world used to disable all Lacuna visuals until the game was
 * restarted, while the (server-side) gameplay kept working.
 * <p>
 * Client thread only.
 */
public final class FxGuard {
    private static final long SUMMARY_MS = 30_000L;
    private static final Map<String, Failure> FAILURES = new HashMap<>();

    private static final class Failure {
        int count;
        long lastLog;
    }

    private FxGuard() {}

    /** Runs {@code action}; a failure is reported under {@code key} and swallowed. Returns false when it failed. */
    public static boolean run(String key, Runnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable t) {
            report(key, t);
            return false;
        }
    }

    /** Logs (rate-limited) a failure of {@code key}; virtual machine errors are rethrown, never swallowed. */
    public static void report(String key, Throwable t) {
        if (t instanceof VirtualMachineError vmError) {
            throw vmError;
        }
        Failure f = FAILURES.computeIfAbsent(key, k -> new Failure());
        f.count++;
        long now = Util.getMillis();
        if (f.count == 1) {
            f.lastLog = now;
            Lacuna.LOGGER.warn("[Lacuna FX] '{}' failed: only this effect is dropped, every other visual keeps running", key, t);
        } else if (now - f.lastLog >= SUMMARY_MS) {
            f.lastLog = now;
            Lacuna.LOGGER.warn("[Lacuna FX] '{}' has failed {} times this session (latest: {})", key, f.count, t.toString());
        }
    }

    /** How often {@code key} failed this session (diagnostics). */
    public static int failures(String key) {
        Failure f = FAILURES.get(key);
        return f == null ? 0 : f.count;
    }
}
