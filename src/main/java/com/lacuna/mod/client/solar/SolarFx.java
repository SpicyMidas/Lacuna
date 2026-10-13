package com.lacuna.mod.client.solar;

import com.lacuna.mod.client.fx.FxHandlers;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.network.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Effect kinds sent by the server for Sun's Fury ({@code solar_strike}: flash and camera shake scaled by distance). */
public final class SolarFx {
    private SolarFx() {}

    public static void register() {
        FxHandlers.register("solar_strike", SolarFx::strike);
    }

    private static void strike(FxPayload payload) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = cam.distanceTo(payload.a());
        float flash = (float) SolarMath.attenuation(distance, 24.0, 240.0);
        if (flash > 0.0F) {
            ScreenFx.flash(0xFFF4D8, 0.9F * flash, 28);
        }
        float shake = (float) SolarMath.attenuation(distance, 10.0, 150.0);
        if (shake > 0.0F) {
            ScreenFx.shake(5.5F * shake, 36);
        }
    }
}
