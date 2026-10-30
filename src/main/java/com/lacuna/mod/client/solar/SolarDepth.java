package com.lacuna.mod.client.solar;

import com.lacuna.mod.client.fx.FxDepth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

/**
 * The scene depth read by the late solar light shaders ({@link SolarVolume}, {@link SolarTubes},
 * {@code shaders/include/solar_depth.glsl}): the effects' shared {@link FxDepth} (one private depth texture for all the
 * light drawn after the level; Fast / Fancy: a blit of the main depth, Fabulous: the transparency targets' own depth
 * textures, no copy), prepared only on frames that queued late light. Same sampler and uniform names as
 * {@code fx_depth.glsl}.
 */
final class SolarDepth {
    private SolarDepth() {}

    /** Makes the depth textures and the inverse projection current; false if no depth is available. */
    static boolean prepare(Minecraft mc) {
        return FxDepth.prepare(mc);
    }

    /** Binds the depth samplers and uniforms on a late shader (render type setup); returns it. */
    @Nullable
    static ShaderInstance bind(@Nullable ShaderInstance shader) {
        return FxDepth.bind(shader);
    }
}
