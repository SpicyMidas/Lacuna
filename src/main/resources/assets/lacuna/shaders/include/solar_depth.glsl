// Lacuna light with depth (client/solar/SolarVolume): helpers shared by the shaders drawn after the level, once
// clouds, weather and the Fabulous composite are done. They read the scene depth instead of testing against it:
// SceneDepthSampler holds terrain, entities, water and glass (and in Fast / Fancy also the clouds), CloudDepthSampler
// the Fabulous cloud layer (the same texture otherwise). Distances are along the view ray from the eye, in blocks.
// The light is additive: nothing here ever mixes in the fog colour.

uniform sampler2D SceneDepthSampler;
uniform sampler2D CloudDepthSampler;
uniform mat4 InvProjMat;
uniform vec2 DepthSize;
uniform float FogStart;
uniform float FogEnd;

float lacuna_eye_distance(vec2 fragXY, float depth) {
    vec4 ndc = vec4(fragXY / DepthSize * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * ndc;
    return length(view.xyz / view.w);
}

// distance to the nearest surface or cloud behind this pixel (1e6 through open sky)
float lacuna_scene_distance() {
    vec2 uv = gl_FragCoord.xy / DepthSize;
    float d = min(texture(SceneDepthSampler, uv).r, texture(CloudDepthSampler, uv).r);
    return d >= 1.0 ? 1.0e6 : lacuna_eye_distance(gl_FragCoord.xy, d);
}

// Distance attenuation of the light (a multiplier, never a blend toward the fog colour: the light only ever adds).
// Under the terrain fog the light is bright enough to carry well past it: it only dims gently beyond the fog start;
// thick fogs (water, lava, blindness, powder snow: a short fog end) still swallow it like vanilla.
float lacuna_fade_at(float d) {
    if (FogEnd < 40.0) {
        return 1.0 - smoothstep(FogStart, FogEnd, d);
    }
    return 1.0 - 0.6 * smoothstep(max(FogStart, 0.5 * FogEnd), 2.2 * FogEnd, d);
}

float lacuna_fade(vec3 pos) {
    return lacuna_fade_at(length(pos));
}

// A camera-facing ribbon read as a round shaft of light around its axis. pos: camera-relative position on the ribbon
// (the ribbon contains the axis and faces the eye), lateral: signed distance from the axis in blocks, r: radius there.
// The ray through this pixel crosses the shaft along a chord centred on the ribbon, 2 sqrt(r^2 - lateral^2) / sin(a)
// long (a: angle between the ray and the axis). Returns x: path gain (a shaft seen along its length glows brighter),
// y: the part of the light in front of the scene and of the eye (soft intersection: the shaft melts into the ground,
// a cloud or a wall instead of being cut by a hard line, and fades smoothly when the eye is inside it).
// Must run before any discard (screen derivatives).
vec2 lacuna_shaft(vec3 pos, float lateral, float r) {
    vec3 px = dFdx(pos);
    vec3 py = dFdy(pos);
    float lx = dFdx(lateral);
    float ly = dFdy(lateral);
    // world direction of the axis: d pos / d along at constant lateral (unnormalised)
    vec3 axis = py * lx - px * ly;
    float al = length(axis);
    float dist = length(pos);
    float sinA = 1.0;
    if (al > 1.0e-12 && dist > 1.0e-4) {
        sinA = length(cross(pos / dist, axis / al));
    }
    sinA = max(sinA, 0.05);
    float halfChord = sqrt(max(r * r - lateral * lateral, 0.0)) / sinA;
    float t = lacuna_eye_distance(gl_FragCoord.xy, gl_FragCoord.z);
    float soft = max(halfChord, 0.3 + 0.003 * t);
    float scene = lacuna_scene_distance();
    // only what stands in front of the axis hides the light: a surface just behind it (sky, terrain, clouds beyond the
    // shaft) never thins it, so it looks the same over the sky and over the landscape
    float visible = smoothstep(t - soft, t + 0.15 * soft, scene) - smoothstep(t - soft, t + soft, 0.0);
    return vec2(min(1.0 / sinA, 1.6), max(visible, 0.0));
}

// A billboard (u,v 0..1 over the quad) that fades out softly behind the surfaces it intersects instead of being cut
// by a hard line; everything in front of the scene stays as bright as before. Must run before any discard.
float lacuna_soft_quad(vec3 pos, vec2 uv) {
    vec3 px = dFdx(pos);
    vec3 py = dFdy(pos);
    float ux = dFdx(uv.x);
    float uy = dFdy(uv.x);
    float vx = dFdx(uv.y);
    float vy = dFdy(uv.y);
    float det = ux * vy - uy * vx;
    // world length of the quad's u side
    float size = abs(det) > 1.0e-14 ? length(px * vy - py * vx) / abs(det) : 1.0;
    float soft = clamp(size * 0.15, 0.08, 3.0);
    float t = lacuna_eye_distance(gl_FragCoord.xy, gl_FragCoord.z);
    return smoothstep(-soft, 0.0, lacuna_scene_distance() - t);
}
