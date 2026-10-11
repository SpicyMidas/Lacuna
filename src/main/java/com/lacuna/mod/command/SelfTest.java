package com.lacuna.mod.command;

import com.lacuna.mod.Balance;
import com.lacuna.mod.Lacuna;
import com.lacuna.mod.logic.CoordMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Internal invariants checked by {@code /lacuna selftest}; logs a PASS/FAIL summary. */
public final class SelfTest {
    private SelfTest() {}

    public static int run(CommandSourceStack source) {
        List<String> failures = new ArrayList<>();
        int checks = 0;

        // Coordinate math round trips (section 6)
        for (int x = -500; x <= 500; x += 7) {
            checks++;
            int back = CoordMath.toOverworld(CoordMath.toForgotten(x, Balance.FL_SCALE), Balance.FL_SCALE);
            int error = x - back;
            if (error < 0 || error > (int) Balance.FL_SCALE - 1) {
                failures.add("coord round trip " + x + " -> " + back);
            }
        }

        String summary = failures.isEmpty()
                ? "Lacuna selftest PASS (" + checks + " checks)"
                : "Lacuna selftest FAIL (" + failures.size() + "/" + checks + "): " + String.join("; ", failures);
        Lacuna.LOGGER.info(summary);
        if (failures.isEmpty()) {
            source.sendSuccess(() -> Component.literal(summary), true);
            return 1;
        }
        source.sendFailure(Component.literal(summary));
        return 0;
    }
}
