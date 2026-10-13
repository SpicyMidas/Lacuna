#version 150

// Lacuna solar column: a pillar of sunlight poured from the sky.
// texCoord0.x: 0..1 across the (camera-facing) ribbon, 0.5 = axis; texCoord0.y: height in blocks (grows upward).
// vertexColor.rgb: tint; vertexColor.a: intensity. Output is premultiplied (blend ONE, ONE).
// Layers: a white-hot core, a striated golden body whose streaks slide downward, bands of light descending from
// the sky, and a soft shimmering halo.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;

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
    float t = GameTime * 1200.0;
    float xs = texCoord0.x * 2.0 - 1.0;
    float x = abs(xs);
    float h = texCoord0.y;

    // long vertical striations sliding down the pillar, gently swaying
    float sway = 0.25 * sin(h * 0.045 + t * 0.6);
    float streak = fbm(vec2((xs + sway * 0.2) * 6.0, h * 0.04 + t * 2.4));
    float fine = noise(vec2(xs * 19.0 + t * 0.8, h * 0.11 + t * 5.0));
    // bands of light descending from the sky
    float band = 0.5 + 0.5 * sin(h * 0.16 + t * 8.0);
    band = mix(0.8, 1.15, band * band * band);

    float core = exp(-x * x * 70.0);
    float body = exp(-x * x * 7.5) * (0.55 + 0.75 * streak) * band;
    float halo = exp(-x * x * 2.2) * (0.4 + 0.35 * fine) * (0.85 + 0.15 * band);
    float edge = 1.0 - smoothstep(0.8, 1.0, x);

    vec3 tint = vertexColor.rgb;
    vec3 white = vec3(1.0, 0.985, 0.94);
    vec3 col = white * core * 1.7 + mix(tint, white, 0.3) * body * 1.05 + tint * halo * 0.5;
    float intensity = vertexColor.a * edge;

    fragColor = vec4(col * intensity, clamp((core + body + halo) * intensity, 0.0, 1.0)) * ColorModulator;
}
