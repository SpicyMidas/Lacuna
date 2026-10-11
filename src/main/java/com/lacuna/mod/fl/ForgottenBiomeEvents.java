package com.lacuna.mod.fl;

import com.lacuna.mod.LacunaConfig;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/** Game-bus rules of the Forgotten Land biomes (server side). */
public final class ForgottenBiomeEvents {
    private ForgottenBiomeEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(ForgottenBiomeEvents::onCropGrow);
    }

    /** Clockwork Dunes "slow time": half of all crop growth steps are cancelled. */
    private static void onCropGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || !LacunaConfig.get(LacunaConfig.DUNES_SLOW_CROPS)) {
            return;
        }
        if (level.getRandom().nextBoolean() && ForgottenBiomes.in(level, event.getPos(), ForgottenBiomes.CLOCKWORK_DUNES)) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }
}
