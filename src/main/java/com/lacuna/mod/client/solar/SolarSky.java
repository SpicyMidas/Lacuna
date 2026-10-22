package com.lacuna.mod.client.solar;

import com.lacuna.mod.Balance;
import com.lacuna.mod.client.LacunaClientConfig;
import com.lacuna.mod.client.fx.FxDraw;
import com.lacuna.mod.client.fx.ScreenFx;
import com.lacuna.mod.logic.SolarMath;
import com.lacuna.mod.solar.SolarRayEntity;
import com.lacuna.mod.solar.SunsFuryEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * "The sun gets brighter": while a Sun's Fury focuses or strikes nearby, the sun itself flares up — a blazing
 * corona with turning rays drawn over the sun right after the sky (it follows the celestial angle, so it climbs with
 * the sun during the time sweep) plus a vast warm wash across the sky — the fog/horizon colour warms, and players
 * close to the target get a rising warm vignette.
 */
public final class SolarSky {
    private static float boost;
    private static float boostO;

    private SolarSky() {}

    /**
     * Every frame while a nearby Sun's Fury sweeps the time, the sun moves along the very schedule the server follows
     * (instead of jumping once per tick when the time packets arrive), so the sun, the sky colours and the sunbeams that
     * lean towards it glide smoothly. Never moves the time backwards; costs one pass over the rendered entities.
     */
    public static void onFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof SunsFuryEntity spear && spear.isFocusing()) {
                long t = spear.smoothDayTime(pt);
                if (t > mc.level.getDayTime()) {
                    // the level data directly: ClientLevel#setDayTime would also flip the client's daylight rule
                    mc.level.getLevelData().setDayTime(t);
                }
                return;
            }
        }
    }

    static void reset() {
        boost = 0.0F;
        boostO = 0.0F;
    }

    /** Client tick: how strongly the sun flares right now (strongest nearby effect wins), and the vignettes. */
    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        boostO = boost;
        if (level == null) {
            boost = 0.0F;
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float target = 0.0F;
        float focusVignette = 0.0F;
        float strikeVignette = 0.0F;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof SunsFuryEntity spear && spear.isFocusing()) {
                double d = horizontal(cam, e.position());
                float p = (float) SolarMath.ease(spear.focusProgress(0.0F));
                target = Math.max(target, 0.9F * p * (float) SolarMath.attenuation(d, 160, 520));
                focusVignette = Math.max(focusVignette, 0.55F * p * (float) SolarMath.attenuation(cam.distanceTo(e.position()), 14, 80));
            } else if (e instanceof SolarRayEntity ray) {
                double d = horizontal(cam, e.position());
                float i = (float) SolarMath.columnIntensity(ray.age(0.0F), Balance.SOLAR_RAY_TICKS, Balance.SOLAR_FADE_TICKS);
                target = Math.max(target, i * (float) SolarMath.attenuation(d, 160, 520));
                strikeVignette = Math.max(strikeVignette, 0.45F * i * (float) SolarMath.attenuation(cam.distanceTo(e.position()), 16, 110));
            }
        }
        boost += (target - boost) * (target > boost ? 0.2F : 0.08F);
        if (boost < 0.002F && target == 0.0F) {
            boost = 0.0F;
        }
        if (focusVignette > 0.01F) {
            // the sun's colour by day, a softer white light by night (SunLight as of the last frame)
            ScreenFx.hold("solar_focus", SunLight.lit(0xFFA030, 0.0F), focusVignette * (0.5F + 0.5F * SunLight.up), true);
        }
        if (strikeVignette > 0.01F) {
            ScreenFx.hold("solar_strike", 0xFFE2A0, strikeVignette, true);
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float current(float partialTick) {
        return Mth.lerp(partialTick, boostO, boost);
    }

    private static boolean skyVisible(Minecraft mc, Camera camera) {
        return mc.level != null && mc.level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL
                && camera.getFluidInCamera() == FogType.NONE && LacunaClientConfig.get(LacunaClientConfig.SOLAR_SKY);
    }

    /** AFTER_SKY: the flaring sun. Vertices are camera-relative world positions, rotated by the view matrix here. */
    static void renderSky(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float b = current(pt);
        if (b <= 0.003F || !skyVisible(mc, event.getCamera())) {
            return;
        }
        ClientLevel level = mc.level;
        // the flare sits on the sun as drawn (SunLight: vanilla's sun direction), takes its sunrise / sunset colour and
        // sinks out of sight with it
        SunLight.update(level, pt);
        float clear = (1.0F - level.getRainLevel(pt)) * SunLight.up;
        if (clear <= 0.003F) {
            return;
        }
        Vec3 sun = new Vec3(SunLight.dirX * 100.0, SunLight.dirY * 100.0, 0.0);
        float t = (level.getGameTime() % 24000L) + pt;

        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Camera camera = event.getCamera();
        // a vast golden wash of light around the sun (additive: never greys the sky)
        VertexConsumer wash = buffers.getBuffer(SolarRenderTypes.skyWash());
        FxDraw.billboard(pose.last(), wash, camera, sun, 70.0F + 50.0F * b, 0.0F, FxDraw.argb(0.22F * b * clear, SunLight.shade(0xFFA640, 0.0F)));
        buffers.endBatch();
        // the flaring sun: a crown of turning rays and a white-hot disc
        VertexConsumer corona = buffers.getBuffer(SolarRenderTypes.skyCorona());
        FxDraw.billboard(pose.last(), corona, camera, sun, 18.0F + 26.0F * b, t * 0.0015F,
                FxDraw.argb(0.85F * b * clear, SunLight.shade(0xFFC860, 0.2F)));
        FxDraw.billboard(pose.last(), corona, camera, sun, 9.0F + 7.0F * b, -t * 0.002F,
                FxDraw.argb(0.8F * b * clear, SunLight.shade(0xFFF2D0, 0.45F)));
        buffers.endBatch();
        RenderSystem.defaultBlendFunc();
    }

    /** Warms the fog and horizon while the sun flares. */
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        float b = current((float) event.getPartialTick());
        if (b <= 0.003F || !skyVisible(mc, event.getCamera())) {
            return;
        }
        // by night a warm horizon would only look muddy: the warming follows the daylight
        float w = b * (0.35F + 0.65F * SolarVisuals.daylight(mc.level, (float) event.getPartialTick()));
        event.setRed(Mth.lerp(0.35F * w, event.getRed(), 1.0F));
        event.setGreen(Mth.lerp(0.3F * w, event.getGreen(), 0.86F));
        event.setBlue(Mth.lerp(0.25F * w, event.getBlue(), 0.58F));
    }
}
