// Lacuna volumetric effects with depth (client/fx/FxDepth, FxTubes): helpers for the shaders drawn after the level,
// once clouds, weather and the Fabulous composite are done. They read the scene depth instead of testing against it:
// SceneDepthSampler holds terrain, entities, water and glass (and in Fast / Fancy also the clouds), CloudDepthSampler
// the Fabulous cloud layer (the same texture otherwise). Distances are along the view ray from the eye, in blocks.
// The light is additive: nothing here ever mixes in the fog colour.

uniform sampler2D SceneDepthSampler;
uniform sampler2D CloudDepthSampler;
uniform mat4 InvProjMat;
uniform vec2 DepthSize;
uniform float FogStart;
uniform float FogEnd;

float fx_eye_distance(vec2 fragXY, float depth) {
    vec4 ndc = vec4(fragXY / DepthSize * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * ndc;
    return length(view.xyz / view.w);
}

// distance to the nearest surface or cloud behind this pixel (1e6 through open sky)
float fx_scene_distance() {
    vec2 uv = gl_FragCoord.xy / DepthSize;
    float d = min(texture(SceneDepthSampler, uv).r, texture(CloudDepthSampler, uv).r);
    return d >= 1.0 ? 1.0e6 : fx_eye_distance(gl_FragCoord.xy, d);
}

// Thick fogs (water, lava, blindness, powder snow: a short fog end) swallow the light like vanilla; the terrain fog
// does not.
float fx_thick_fog(float d) {
    return FogEnd < 40.0 ? 1.0 - smoothstep(FogStart, FogEnd, d) : 1.0;
}

// Distance attenuation (a multiplier, never a blend toward the fog colour): under the terrain fog the light only
// dims gently beyond the fog start, thick fogs swallow it.
float fx_fade_at(float d) {
    if (FogEnd < 40.0) {
        return fx_thick_fog(d);
    }
    return 1.0 - 0.6 * smoothstep(max(FogStart, 0.5 * FogEnd), 2.2 * FogEnd, d);
}
