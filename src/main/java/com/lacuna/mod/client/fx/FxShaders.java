package com.lacuna.mod.client.fx;

import com.lacuna.mod.Lacuna;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Lacuna core shaders ({@code assets/lacuna/shaders/core/}). A shader that fails to load or compile (old drivers,
 * shader-pack mods) is logged and left null; {@link FxRenderTypes} then falls back to vanilla textured glow.
 */
public final class FxShaders {
    @Nullable
    private static ShaderInstance beam;
    @Nullable
    private static ShaderInstance glow;

    private FxShaders() {}

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id("beam"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), s -> beam = s);
        } catch (IOException | RuntimeException e) {
            beam = null;
            Lacuna.LOGGER.warn("Lacuna beam shader unavailable, using the textured fallback: {}", e.toString());
        }
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Lacuna.id("glow"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), s -> glow = s);
        } catch (IOException | RuntimeException e) {
            glow = null;
            Lacuna.LOGGER.warn("Lacuna glow shader unavailable, using the textured fallback: {}", e.toString());
        }
    }

    /** Procedural energy beam: u across the beam (0..1), v along it in blocks; colour = tint, alpha = intensity. */
    @Nullable
    public static ShaderInstance beam() {
        return beam;
    }

    /** Procedural soft glow / flare: u,v in 0..1 over the quad; colour = tint, alpha = intensity. */
    @Nullable
    public static ShaderInstance glow() {
        return glow;
    }
}
