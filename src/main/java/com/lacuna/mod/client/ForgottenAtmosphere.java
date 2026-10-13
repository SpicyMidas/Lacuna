package com.lacuna.mod.client;

import com.lacuna.mod.LacunaConfig;
import com.lacuna.mod.fl.ForgottenBiomes;
import com.lacuna.mod.registry.ModDimensions;
import com.lacuna.mod.registry.ModParticles;
import com.lacuna.mod.util.SafeRun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Forgotten Land atmosphere (client only): a dense fog in every Forgotten Land biome (terrain and sky, far plane
 * {@code forgottenLand.fogDistance}, thicker still in the Whispering Marsh) and drifting mist wisps near the ground.
 * Leaves underwater, lava, powder snow, blindness and darkness fog alone. Blends in over ~1 s.
 */
public final class ForgottenAtmosphere {
    private static final String FEATURE = "forgotten_atmosphere";
    private static float blend;
    private static float marsh;

    private ForgottenAtmosphere() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        SafeRun.run(FEATURE, () -> {
            Minecraft mc = Minecraft.getInstance();
            Entity camera = mc.getCameraEntity();
            ClientLevel level = mc.level;
            boolean inFl = camera != null && level != null && level.dimension() == ModDimensions.FORGOTTEN_LAND;
            boolean inMarsh = inFl && LacunaConfig.get(LacunaConfig.MARSH_FOG_ENABLED)
                    && ForgottenBiomes.in(level, camera.blockPosition(), ForgottenBiomes.WHISPERING_MARSH);
            blend = inFl ? Math.min(1.0F, blend + 0.05F) : Math.max(0.0F, blend - 0.05F);
            marsh = inMarsh ? Math.min(1.0F, marsh + 0.03F) : Math.max(0.0F, marsh - 0.03F);
            if (inFl && !mc.isPaused() && LacunaClientConfig.get(LacunaClientConfig.FL_MIST)) {
                spawnMist(mc, level, camera);
            }
        });
    }

    private static void spawnMist(Minecraft mc, ClientLevel level, Entity camera) {
        ParticleStatus status = mc.options.particles().get();
        int count = status == ParticleStatus.ALL ? 2 : status == ParticleStatus.DECREASED ? 1 : 0;
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            double x = camera.getX() + (r.nextDouble() - 0.5) * 40.0;
            double z = camera.getZ() + (r.nextDouble() - 0.5) * 40.0;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double y = ground + 0.3 + r.nextDouble() * 2.2;
            if (Math.abs(y - camera.getY()) > 24.0) {
                continue;
            }
            level.addParticle(ModParticles.MIST.get(), x, y, z, (r.nextDouble() - 0.5) * 0.02, 0.0, (r.nextDouble() - 0.5) * 0.02);
        }
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        if (blend <= 0.0F || SafeRun.isDisabled(FEATURE) || event.getType() != FogType.NONE) {
            return;
        }
        if (event.getCamera().getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }
        float vanillaFar = event.getFarPlaneDistance();
        float dense = LacunaClientConfig.get(LacunaClientConfig.FL_FOG_DISTANCE);
        float marshFar = LacunaClientConfig.get(LacunaClientConfig.FL_MARSH_FOG_DISTANCE);
        float targetFar = Mth.lerp(marsh, dense, Math.min(dense, marshFar));
        if (targetFar >= vanillaFar) {
            return;
        }
        float far = Mth.lerp(blend, vanillaFar, targetFar);
        event.setFarPlaneDistance(far);
        // the fog starts almost at the player: everything beyond a few blocks fades into grey
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.04F));
        event.setCanceled(true); // required for the plane changes to apply
    }
}
