package com.lacuna.mod.client;

import com.lacuna.mod.client.render.OutlineRenderer;
import com.lacuna.mod.client.render.OutlineSet;
import com.lacuna.mod.network.RevealOresPayload;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Client-side payload handlers (run on the main thread by NeoForge's default handler thread). */
public final class ClientPayloads {
    private static final int REVEAL_COLOR = 0xFFE7B54A;

    private ClientPayloads() {}

    public static void reveal(RevealOresPayload payload) {
        SafeRun.run("sunken_bell_reveal", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            OutlineSet set = new OutlineSet(payload.positions().size(), 0).expiresAt(mc.level.getGameTime() + payload.durationTicks());
            for (BlockPos pos : payload.positions()) {
                set.addBox(new AABB(pos).inflate(0.002), REVEAL_COLOR);
            }
            OutlineRenderer.put("sunken_bell", set);
        });
    }
}
