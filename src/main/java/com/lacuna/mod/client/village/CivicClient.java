package com.lacuna.mod.client.village;

import com.lacuna.mod.util.SafeRun;
import com.lacuna.mod.village.CivicNet;
import net.minecraft.client.Minecraft;

/** Client end of the civic screens: opens the screen a right click asked for, or refreshes the one already open. */
public final class CivicClient {
    private CivicClient() {}

    public static void onSnapshot(CivicNet.Snapshot snapshot) {
        SafeRun.run("villages", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            if (mc.screen instanceof CivicScreen open && open.pos.equals(snapshot.pos()) && open.kind.equals(snapshot.kind())) {
                open.apply(snapshot);
            } else if (snapshot.open()) {
                mc.setScreen(new CivicScreen(snapshot));
            }
        });
    }
}
