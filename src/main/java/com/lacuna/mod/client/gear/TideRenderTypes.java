package com.lacuna.mod.client.gear;

import com.lacuna.mod.Lacuna;
import com.lacuna.mod.client.fx.FxRenderTypes;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Tidecaller's water shader ({@code lacuna:tide_vortex}, see its .fsh for the modes) and its render type: one
 * depth-sorted, premultiplied-alpha batch ({@code POSITION_TEX_COLOR_NORMAL}) that holds a whole maelstrom - funnel,
 * sheath, storm canopy, whirlpool, spray skirt, mist and droplets - so its layers sort against each other. Under
 * Fabulous graphics its depth twin runs the same shader with a high alpha cut, so only solid water writes depth.
 * If the shader does not load the effects fall back to the wave's {@code shade(BEAM)} with CPU shading ({@link TideMesh}).
 */
public final class TideRenderTypes {
    @Nullable
    private static ShaderInstance vortex;

    /** Premultiplied "over": src + dst * (1 - src.a). */
    private static final RenderStateShard.TransparencyStateShard OVER = new RenderStateShard.TransparencyStateShard("lacuna_tide_over",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });
    private static final RenderStateShard.ShaderStateShard COLOUR_SHADER = new RenderStateShard.ShaderStateShard(() -> withCut(0.004F));
    private static final RenderStateShard.ShaderStateShard DEPTH_SHADER = new RenderStateShard.ShaderStateShard(() -> withCut(0.32F));

    private static final RenderType VORTEX = RenderType.create("lacuna_tide_vortex", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
            VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(COLOUR_SHADER)
                    .setTransparencyState(OVER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));
    private static final RenderType VORTEX_DEPTH = RenderType.create("lacuna_tide_vortex_depth",
            DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(DEPTH_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    static {
        FxRenderTypes.withDepthTwin(VORTEX, VORTEX_DEPTH);
    }

    private TideRenderTypes() {}

    @Nullable
    private static ShaderInstance withCut(float cut) {
        ShaderInstance s = vortex;
        if (s != null) {
            s.safeGetUniform("AlphaCut").set(cut);
        }
        return s;
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id("tide_vortex"),
                    DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL), s -> vortex = s);
        } catch (IOException | RuntimeException e) {
            vortex = null;
            Lacuna.LOGGER.warn("Lacuna tide_vortex shader unavailable, using the wave's fallback: {}", e.toString());
        }
    }

    /** True when the vortex shader is in use (else the meshes shade themselves for the fallback type). */
    static boolean shaded() {
        return vortex != null;
    }

    /** The tide's water batch: the vortex shader, or the wave's translucent fill. */
    static RenderType vortex() {
        return vortex != null ? VORTEX : FxRenderTypes.shade(FxRenderTypes.BEAM);
    }
}
