package com.lacuna.mod.util;

import com.lacuna.mod.Lacuna;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Awards advancements whose criterion is {@code minecraft:impossible} (granted from code); never throws. */
public final class LacunaAdvancements {
    private LacunaAdvancements() {}

    public static void award(Player player, String path) {
        if (!(player instanceof ServerPlayer sp) || sp.getServer() == null) {
            return;
        }
        SafeRun.run("advancements", () -> {
            AdvancementHolder holder = sp.getServer().getAdvancements().get(Lacuna.id(path));
            if (holder != null) {
                sp.getAdvancements().award(holder, "code");
            }
        });
    }
}
