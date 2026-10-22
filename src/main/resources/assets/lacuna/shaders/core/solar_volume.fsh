#version 150

#moj_import <lacuna:solar_depth.glsl>

// Lacuna solar volume: the solar_column light (pillars and shafts of sunlight) as round shafts with depth, drawn after
// the level (client/solar/SolarVolume). Same layers, colours and motion as solar_column.fsh (white-hot core,
// striated golden body, bands pouring down, heat-shimmer edges, soft halo; or the hazy sunbeam profile), read across
// a cylinder instead of a flat ribbon: brighter where the eye looks along the shaft, melting softly into the ground,
// clouds and walls it meets, hidden by near clouds and drawn over far ones, and fogged like the terrain.
// Output is premultiplied (blend ONE, ONE or ONE, ONE_MINUS_SRC_ALPHA).

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 worldPos;
in float radius;
flat in int flags;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.55;
    for (int i = 0; i < 3; i++) {
        sum += amp * noise(p);
        p = p * 2.07 + vec2(11.7, 3.1);
        amp *= 0.5;
    }
    return sum;
}

void main() {
    vec2 shaft = lacuna_shaft(worldPos, texCoord0.x, radius);
    float fog = (flags & 2) != 0 ? 0.0 : lacuna_fog(worldPos);
    float depth = shaft.x * shaft.y * (1.0 - fog);
    if (depth <= 0.001) {
        discard;
    }
    float t = GameTime * 1200.0;
    float h = texCoord0.y;
    float xs = clamp(texCoord0.x / max(radius, 1.0e-4), -1.0, 1.0);
    if ((flags & 1) != 0) {
        // soft sunbeam: no core line, a broad hazy body
        float x1 = abs(xs);
        float drift = fbm(vec2(xs * 3.0, h * 0.05 + t * 1.2));
        float motes = noise(vec2(xs * 11.0 + t * 0.3, h * 0.6 + t * 3.0));
        float shaft1 = exp(-x1 * x1 * 4.0) * (0.55 + 0.6 * drift) * (0.9 + 0.2 * motes);
        float edge1 = 1.0 - smoothstep(0.7, 1.0, x1);
        vec3 tint1 = vertexColor.rgb;
        vec3 col1 = mix(tint1, vec3(1.0, 0.97, 0.9), 0.25 * exp(-x1 * x1 * 12.0)) * shaft1;
        float k = vertexColor.a * edge1 * depth;
        fragColor = vec4(col1 * k, clamp(shaft1 * k, 0.0, 1.0)) * ColorModulator;
        return;
    }

    // heat shimmer: the outer part of the shaft ripples sideways, the core stays straight
    float ripple = (noise(vec2(h * 0.11 + t * 1.7, 4.3)) - 0.5) * 0.16 + (noise(vec2(h * 0.37 - t * 2.9, 9.1)) - 0.5) * 0.08;
    xs += ripple * smoothstep(0.15, 0.9, abs(xs));
    float x = abs(xs);

    // long striations sliding along the pillar, gently swaying
    float sway = 0.25 * sin(h * 0.045 + t * 0.6);
    float streak = fbm(vec2((xs + sway * 0.2) * 6.0, h * 0.04 + t * 2.4));
    float fine = noise(vec2(xs * 19.0 + t * 0.8, h * 0.11 + t * 5.0));
    // bands of light pouring down from the sky
    float band = 0.5 + 0.5 * sin(h * 0.16 + t * 8.0);
    band = mix(0.78, 1.2, band * band * band);
    float surge = 0.5 + 0.5 * sin(h * 0.035 + t * 3.1);

    float core = exp(-x * x * 70.0) * (0.92 + 0.08 * surge);
    float body = exp(-x * x * 7.5) * (0.5 + 0.8 * streak) * band;
    float halo = exp(-x * x * 2.2) * (0.38 + 0.35 * fine) * (0.85 + 0.15 * band);
    float edge = 1.0 - smoothstep(0.78, 1.0, x);

    vec3 tint = vertexColor.rgb;
    vec3 white = vec3(1.0, 0.985, 0.94);
    vec3 col = white * core * 1.75 + mix(tint, white, 0.3) * body * 1.05 + tint * halo * 0.5;
    float intensity = vertexColor.a * edge * depth;

    fragColor = vec4(col * intensity, clamp((core + body + halo) * intensity, 0.0, 1.0)) * ColorModulator;
}
