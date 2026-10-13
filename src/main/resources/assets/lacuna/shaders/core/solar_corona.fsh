#version 150

// Lacuna sun corona: a blazing disc of light with a slowly turning crown of rays, drawn over the sun in the sky.
// texCoord0: 0..1 over the quad; vertexColor.rgb: tint; vertexColor.a: intensity. Premultiplied (blend ONE, ONE).

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float t = GameTime * 1200.0;
    vec2 p = texCoord0 * 2.0 - 1.0;
    float d = length(p);
    if (d > 1.0) {
        discard;
    }
    float a = atan(p.y, p.x);

    float core = exp(-d * d * 60.0);
    float disc = exp(-d * d * 14.0);
    float glow = exp(-d * d * 3.5);
    float wide = exp(-d * 2.2) * (1.0 - d);
    // three crowns of rays turning at different speeds, flickering softly
    float r1 = pow(0.5 + 0.5 * sin(a * 12.0 + t * 0.25), 7.0);
    float r2 = pow(0.5 + 0.5 * sin(a * 7.0 - t * 0.18 + 1.7), 9.0);
    float r3 = pow(0.5 + 0.5 * sin(a * 19.0 + t * 0.4 + 0.6), 12.0);
    float rays = (r1 * 0.55 + r2 * 0.5 + r3 * 0.35) * exp(-d * 2.4) * (0.9 + 0.1 * sin(t * 3.0 + a * 3.0));
    float fade = 1.0 - smoothstep(0.7, 1.0, d);

    vec3 tint = vertexColor.rgb;
    vec3 white = vec3(1.0, 0.99, 0.95);
    vec3 col = white * (core * 1.8 + disc * 0.9) + tint * (glow * 0.7 + wide * 0.45 + rays * 0.9);
    float intensity = vertexColor.a * fade;
    fragColor = vec4(col * intensity, clamp((core + disc + glow + rays) * intensity, 0.0, 1.0)) * ColorModulator;
}
