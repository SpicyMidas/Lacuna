package com.lacuna.mod.client.solar;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The scene depth read by the late solar light shaders ({@link SolarVolume}, {@code shaders/include/solar_depth.glsl}),
 * prepared at most once per frame and only when late light was queued:
 * <ul>
 *   <li><b>Fast / Fancy</b>: everything (terrain, entities, water, glass, clouds) is in the main target's depth, which
 *       cannot be sampled while it is the depth attachment being drawn to: one blit into a private depth texture.</li>
 *   <li><b>Fabulous</b>: no copy. The translucent layer's depth (the opaque scene copied in, plus water and glass) and
 *       the cloud layer's depth are separate textures of the transparency targets, read directly (both are complete
 *       once the level is drawn).</li>
 * </ul>
 */
final class SolarDepth {
    @Nullable
    private static TextureTarget copy;
    private static int scene = -1;
    private static int cloud = -1;
    private static float width = 1.0F;
    private static float height = 1.0F;
    private static final Matrix4f INV_PROJ = new Matrix4f();

    private SolarDepth() {}

    /** Makes the depth textures and the inverse projection current; false if no depth is available. */
    static boolean prepare(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        LevelRenderer level = mc.levelRenderer;
        RenderTarget translucent = level.getTranslucentTarget();
        RenderTarget clouds = level.getCloudsTarget();
        // the transparency targets exist exactly while vanilla runs the Fabulous composite (its own test); the main
        // depth is then overwritten by the composite's final blit
        if (translucent != null && clouds != null) {
            scene = translucent.getDepthTextureId();
            // the cloud target is only cleared (and drawn) this frame while clouds are on
            cloud = mc.options.getCloudsType() != CloudStatus.OFF ? clouds.getDepthTextureId() : scene;
        } else {
            if (copy == null) {
                copy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            } else if (copy.width != main.width || copy.height != main.height) {
                copy.resize(main.width, main.height, Minecraft.ON_OSX);
            }
            if (main.isStencilEnabled() && !copy.isStencilEnabled()) {
                copy.enableStencil(); // the blit needs matching depth formats
            }
            copy.copyDepthFrom(main);
            main.bindWrite(false);
            scene = copy.getDepthTextureId();
            cloud = scene;
        }
        width = main.width;
        height = main.height;
        INV_PROJ.set(RenderSystem.getProjectionMatrix()).invert();
        return scene > 0 && cloud > 0;
    }

    /** Binds the depth samplers and uniforms on a late shader (render type setup); returns it. */
    @Nullable
    static ShaderInstance bind(@Nullable ShaderInstance shader) {
        if (shader != null) {
            shader.setSampler("SceneDepthSampler", scene);
            shader.setSampler("CloudDepthSampler", cloud);
            shader.safeGetUniform("InvProjMat").set(INV_PROJ);
            shader.safeGetUniform("DepthSize").set(width, height);
        }
        return shader;
    }
}
