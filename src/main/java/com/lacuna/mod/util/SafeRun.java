package com.lacuna.mod.util;

import com.lacuna.mod.Lacuna;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Crash shield for risky runtime systems (spec section 3, "Defensive code").
 * <p>
 * A failing feature is logged once with its full stack trace, its partial state is restored through the
 * optional {@code restore} callback, and the feature is disabled for the rest of the session. Registration
 * code must never be wrapped in this helper.
 */
public final class SafeRun {
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> REASONS = new ConcurrentHashMap<>();

    private SafeRun() {}

    public static boolean isDisabled(String feature) {
        return DISABLED.contains(feature);
    }

    @Nullable
    public static String reason(String feature) {
        return REASONS.get(feature);
    }

    /** Re-enables every feature (used by /lacuna selftest reset and on server stop). */
    public static void resetAll() {
        DISABLED.clear();
        REASONS.clear();
    }

    public static void run(String feature, Runnable action) {
        run(feature, action, null);
    }

    public static void run(String feature, Runnable action, @Nullable Runnable restore) {
        if (DISABLED.contains(feature)) {
            return;
        }
        try {
            action.run();
        } catch (Throwable t) {
            fail(feature, t, restore);
        }
    }

    public static <T> T call(String feature, Supplier<T> action, T fallback) {
        return call(feature, action, fallback, null);
    }

    public static <T> T call(String feature, Supplier<T> action, T fallback, @Nullable Runnable restore) {
        if (DISABLED.contains(feature)) {
            return fallback;
        }
        try {
            return action.get();
        } catch (Throwable t) {
            fail(feature, t, restore);
            return fallback;
        }
    }

    private static void fail(String feature, Throwable t, @Nullable Runnable restore) {
        if (t instanceof VirtualMachineError vmError) {
            // OutOfMemoryError / StackOverflowError: never swallow
            throw vmError;
        }
        boolean first = DISABLED.add(feature);
        if (first) {
            REASONS.put(feature, t.getClass().getSimpleName() + ": " + t.getMessage());
            Lacuna.LOGGER.warn("[Lacuna SafeRun] feature '{}' failed and is disabled for this session. Context: thread={}",
                    feature, Thread.currentThread().getName(), t);
        }
        if (restore != null) {
            try {
                restore.run();
            } catch (Throwable restoreFailure) {
                if (restoreFailure instanceof VirtualMachineError vmError) {
                    throw vmError;
                }
                Lacuna.LOGGER.warn("[Lacuna SafeRun] restore step of feature '{}' also failed", feature, restoreFailure);
            }
        }
    }
}
