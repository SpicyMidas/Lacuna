#version 150

// Lacuna solar ground/sky marks, drawn on flat quads. vertexColor.rgb: tint; vertexColor.a: intensity.
// Output is premultiplied (blend ONE, ONE or ONE, ONE_MINUS_SRC_ALPHA).
// The quad's u selects the mark: u in [0,1] = mode 0, [2,3] = mode 1, [4,5] = mode 2, [6,7] = mode 3
// (mode = floor(u / 2), local u = u - 2 * mode); v runs 0..1.
//   0: focus reticle   - a crisp ring with slowly turning dashes, an inner ring and a faint concentrated fill
//   1: shockwave       - a bright leading edge with a soft, broken trailing wake
//   2: light pool      - a soft disc of light with slowly turning radial streaks (ground glow, sky aperture)
//   3: thin ring       - a plain soft ring (pull-in pulses, descending column rings)

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float PI = 3.14159265;

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

float band(float d, float r, float w) {
    float x = (d - r) / w;
    return exp(-x * x);
}

void main() {
    float t = GameTime * 1200.0;
    float mode = floor(texCoord0.x * 0.5);
    vec2 uv = vec2(texCoord0.x - 2.0 * mode, texCoord0.y);
    vec2 p = uv * 2.0 - 1.0;
    float d = length(p);
    if (d > 1.0) {
        discard;
    }
    float a = atan(p.y, p.x);
    vec3 tint = vertexColor.rgb;
    vec3 hot = mix(tint, vec3(1.0), 0.65);
    vec3 col;
    float lum;

    if (mode < 0.5) {
        // focus reticle
        float outer = band(d, 0.9, 0.035);
        float dashes = smoothstep(0.15, 0.6, sin(a * 18.0 + t * 1.6)) * band(d, 0.8, 0.02);
        float inner = band(d, 0.62, 0.012) * (0.6 + 0.4 * sin(a * 3.0 - t * 2.0));
        float fill = (1.0 - smoothstep(0.0, 0.9, d)) * 0.22;
        float glow = band(d, 0.9, 0.16) * 0.35;
        lum = outer * 1.2 + dashes * 0.9 + inner * 0.7 + fill + glow;
        col = hot * (outer + dashes) + tint * (inner + fill + glow);
    } else if (mode < 1.5) {
        // shockwave
        float n = noise(vec2(a * 6.0, t * 3.0)) * 0.5 + noise(vec2(a * 17.0, -t * 5.0)) * 0.5;
        float edge = band(d, 0.95, 0.03);
        float wake = smoothstep(0.45, 0.95, d) * (1.0 - smoothstep(0.95, 1.0, d)) * (0.35 + 0.5 * n);
        lum = edge * 1.3 + wake * 0.7;
        col = hot * edge * 1.2 + tint * wake;
    } else if (mode < 2.5) {
        // light pool
        float core = exp(-d * d * 9.0);
        float body = exp(-d * d * 3.0);
        float streaks = pow(0.5 + 0.5 * sin(a * 9.0 + t * 0.35 + 2.0 * noise(vec2(d * 3.0, t * 0.4))), 4.0)
                * (1.0 - d) * 0.45;
        float edge = 1.0 - smoothstep(0.6, 1.0, d);
        lum = (core * 0.9 + body * 0.55 + streaks) * edge;
        col = (hot * core * 0.9 + tint * (body * 0.55 + streaks)) * edge;
    } else {
        // thin ring: a soft golden band with a brighter, slightly broken crest
        float n = 0.75 + 0.25 * noise(vec2(a * 5.0 + t * 0.7, t * 0.5));
        float ring = band(d, 0.9, 0.04) * n;
        float glow = band(d, 0.88, 0.14) * 0.45;
        lum = ring * 0.8 + glow;
        col = mix(tint, vec3(1.0), 0.35) * ring * 0.8 + tint * glow;
    }

    float intensity = vertexColor.a;
    fragColor = vec4(col * intensity, clamp(lum * intensity, 0.0, 1.0)) * ColorModulator;
}
