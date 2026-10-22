#version 150

#moj_import <lacuna:solar_depth.glsl>

// Lacuna soft flare with depth: glow.fsh (soft core, halo, four light streaks, slow twinkle) drawn after the level
// (client/solar/SolarVolume): it fades softly behind the surfaces it touches instead of being cut by a hard line,
// sits correctly in front of or behind clouds and dims with distance.
// texCoord0: 0..1 over the quad; vertexColor.rgb: tint; vertexColor.a: intensity. Premultiplied (blend ONE, ONE).

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 worldPos;

out vec4 fragColor;

void main() {
    float depth = lacuna_soft_quad(worldPos, texCoord0) * lacuna_fade(worldPos);
    float seconds = GameTime * 1200.0;
    vec2 p = texCoord0 * 2.0 - 1.0;
    float d = length(p);
    if (d > 1.0 || depth <= 0.001) {
        discard;
    }
    float core = exp(-d * d * 22.0);
    float halo = exp(-d * d * 4.5) * 0.55;
    float streaks = (exp(-abs(p.y) * 38.0) + exp(-abs(p.x) * 38.0)) * (1.0 - d) * 0.6;
    float twinkle = 0.88 + 0.12 * sin(seconds * 9.0 + d * 6.0);
    float fade = 1.0 - smoothstep(0.75, 1.0, d);

    vec3 tint = vertexColor.rgb;
    vec3 col = mix(tint, vec3(1.0), 0.7) * core * 1.5 + tint * (halo + streaks) * twinkle;
    float intensity = vertexColor.a * fade * depth;
    fragColor = vec4(col * intensity, clamp((core + halo + streaks) * intensity, 0.0, 1.0)) * ColorModulator;
}
