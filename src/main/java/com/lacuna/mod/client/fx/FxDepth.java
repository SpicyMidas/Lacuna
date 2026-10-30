package com.lacuna.mod.client.fx;

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
 * The scene depth read by the effect shaders drawn after the level ({@link FxTubes},
 * {@code shaders/include/fx_depth.glsl}), prepared only on frames that queued such effects:
 * <ul>
 *   <li><b>Fast / Fancy</b>: everything (terrain, entities, water, glass, clouds) is in the main target's depth, which
 *       cannot be sampled while it is the depth attachment being drawn to: one blit into a private depth texture.</li>
 *   <li><b>Fabulous</b>: no copy. The translucent layer's depth (the opaque scene copied in, plus water and glass) and
 *       the cloud layer's depth are separate textures of the transparency targets, read directly (both are complete
 *       once the level is drawn).</li>
 * </ul>
 * Render thread only.
 */
public final class FxDepth {
    @Nullable
    private static TextureTarget copy;
    private static int scene = -1;
    private static int cloud = -1;
    private static float width = 1.0F;
    private static float height = 1.0F;
    private static final Matrix4f INV_PROJ = new Matrix4f();

    private FxDepth() {}

    /** Makes the depth textures and the inverse projection current; false if no depth is available. */
    public static boolean prepare(Minecraft mc) {
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

    /** Binds the depth samplers and uniforms ({@code fx_depth.glsl}) on a late shader; returns it. */
    @Nullable
    public static ShaderInstance bind(@Nullable ShaderInstance shader) {
        if (shader != null) {
            shader.setSampler("SceneDepthSampler", scene);
            shader.setSampler("CloudDepthSampler", cloud);
            shader.safeGetUniform("InvProjMat").set(INV_PROJ);
            shader.safeGetUniform("DepthSize").set(width, height);
        }
        return shader;
    }
}
