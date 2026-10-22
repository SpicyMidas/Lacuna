#version 150

#moj_import <lacuna:solar_depth.glsl>

// Lacuna energy beam with depth: beam.fsh (flowing turbulence, ripple rings, hot core) read across a round beam
// instead of a flat ribbon, drawn after the level (client/solar/SolarVolume): brighter where the eye looks along it,
// melting softly into what it hits, correctly in front of or behind clouds, fogged like the terrain.
// Output is premultiplied (blend ONE, ONE).

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

void main() {
    vec2 shaft = lacuna_shaft(worldPos, texCoord0.x, radius);
    float fog = (flags & 2) != 0 ? 0.0 : lacuna_fog(worldPos);
    float depth = shaft.x * shaft.y * (1.0 - fog);
    if (depth <= 0.001) {
        discard;
    }
    float seconds = GameTime * 1200.0;
    float x = min(abs(texCoord0.x) / max(radius, 1.0e-4), 1.0);
    float along = texCoord0.y;

    // two layers of flowing turbulence travelling along the beam
    float n1 = noise(vec2(along * 1.3 - seconds * 6.0, x * 4.0 + seconds * 0.7));
    float n2 = noise(vec2(along * 3.1 - seconds * 11.0, x * 9.0 - seconds * 1.3));
    float flow = 0.55 + 0.3 * n1 + 0.25 * n2;

    // shimmering ripple rings sliding along the beam
    float ripple = 0.85 + 0.15 * sin(along * 2.2 - seconds * 14.0);

    float core = exp(-x * x * 55.0);
    float inner = exp(-x * x * 9.0) * flow * ripple;
    float outer = exp(-x * x * 2.2) * (0.35 + 0.25 * n1);
    float edge = 1.0 - smoothstep(0.82, 1.0, x);

    vec3 tint = vertexColor.rgb;
    vec3 hot = mix(tint, vec3(1.0), 0.75);
    vec3 col = hot * core * 1.4 + tint * inner * 1.1 + tint * outer * 0.6;
    float intensity = vertexColor.a * edge * depth;

    fragColor = vec4(col * intensity, clamp((core + inner + outer) * intensity, 0.0, 1.0)) * ColorModulator;
}
