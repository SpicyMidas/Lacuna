package com.lacuna.mod.client;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fl.ForgottenBiomes;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Whispering Marsh atmosphere (spec M10, optional, client only): fog closes in to ~28 blocks and sounds from beyond
 * 16 blocks play at 40 % volume while the camera is in the marsh. Blends in and out over ~1 s.
 */
public final class MarshAtmosphere {
    private static final String FEATURE = "marsh_atmosphere";
    private static final float FOG_FAR = 28.0F;
    private static float blend;

    private MarshAtmosphere() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        SafeRun.run(FEATURE, () -> {
            Minecraft mc = Minecraft.getInstance();
            Entity camera = mc.getCameraEntity();
            boolean inMarsh = camera != null && mc.level != null && LacunaConfig.get(LacunaConfig.MARSH_FOG_ENABLED)
                    && ForgottenBiomes.in(mc.level, camera.blockPosition(), ForgottenBiomes.WHISPERING_MARSH);
            blend = inMarsh ? Math.min(1.0F, blend + 0.05F) : Math.max(0.0F, blend - 0.05F);
        });
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        if (blend <= 0.0F || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        float far = event.getFarPlaneDistance();
        if (far <= FOG_FAR) {
            return;
        }
        float target = far + (FOG_FAR - far) * blend;
        event.setFarPlaneDistance(target);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), target * 0.1F));
        event.setCanceled(true); // required for the plane changes to apply
    }

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (blend < 0.5F || sound == null || sound.isRelative() || sound instanceof TickableSoundInstance || SafeRun.isDisabled(FEATURE)) {
            return;
        }
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera != null && camera.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) > 16.0 * 16.0) {
            event.setSound(new MuffledSound(sound, 0.4F));
        }
    }
}
