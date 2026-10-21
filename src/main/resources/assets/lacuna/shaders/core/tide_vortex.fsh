#version 150

#moj_import <fog.glsl>

// Lacuna tide vortex: the Tidecaller's waterspout, whirlpool, storm canopy, spray and geyser.
// texCoord0.x = mode * 2 + u, with u 0..1 round the ring (turns); texCoord0.y = v (meaning per mode, below).
// vertexColor.rgb = the local water's body colour, already lit (its luminance is 0.46 in full light, so the shader
// derives the deep / light / pale / foam tones and the white level from it); vertexColor.a = density.
// worldNormal = surface normal (world axes); viewPos = camera-relative position, i.e. the view ray.
// Output is premultiplied (blend ONE, ONE_MINUS_SRC_ALPHA). Fragments at or under AlphaCut are discarded (the
// Fabulous depth pass draws the same geometry with a high cut so only the solid water writes depth).
// The noise is pixel-snapped and posterised so the water reads like Minecraft's, not like a blur.
//   0 CORE   funnel core, v = height in blocks: helical streaks rushing up, faster near the top, foam bands, fresnel rim
//   1 SHEATH outer spray sheath, v = height: slower, torn into wisps
//   2 CANOPY storm cloud, v = radius in blocks: dark churning lumps spiralling in, density breaks up into lumps
//   3 POOL   whirlpool surface, v = 16 * radius fraction: dark eye, log-spiral arms, foam flecks, crest at the swell
//   4 SKIRT  spray cascade at the base, v = 8 * profile: bushy, torn, boiling outward
//   5 FLAT   droplets and debris: vertex colour as is
//   6 PUFF   mist billboard, u,v local 0..1 (v + 2 * seed): a pixel cloud puff
//   7 JET    geyser column, v = height: streaks blasting straight up
//   8 FALL   falling curtain / crown spray, v = height: torn streaks falling

uniform vec4 ColorModulator;
uniform float GameTime;
uniform float AlphaCut;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int FogShape;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 viewPos;
in vec3 worldNormal;

out vec4 fragColor;

const float TAU = 6.28318531;

float hash(vec2 cell) {
    uvec2 q = uvec2(ivec2(cell) + ivec2(1073741824));
    uint h = (q.x * 1597334677u) ^ (q.y * 3812015801u);
    h = (h ^ (h >> 16u)) * 2246822519u;
    h = (h ^ (h >> 13u)) * 3266489917u;
    h ^= h >> 16u;
    return float(h & 16777215u) * (1.0 / 16777216.0);
}

// value noise; x wraps every px cells, so a pattern closes seamlessly round a ring
float vnoise(vec2 p, float px) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 w = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, px);
    float x1 = mod(i.x + 1.0, px);
    float a = hash(vec2(x0, i.y));
    float b = hash(vec2(x1, i.y));
    float c = hash(vec2(x0, i.y + 1.0));
    float d = hash(vec2(x1, i.y + 1.0));
    return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
}

float fbm(vec2 p, float px) {
    float s = 0.0;
    float amp = 0.5;
    for (int k = 0; k < 3; k++) {
        s += amp * vnoise(p, px);
        p = p * 2.0 + vec2(0.0, 19.7);
        px *= 2.0;
        amp *= 0.5;
    }
    return s * (1.0 / 0.875);
}

// pixel grid: res pixels per noise cell
vec2 snap(vec2 p, vec2 res) {
    return (floor(p * res) + 0.5) / res;
}

float steps(float x, float n) {
    return floor(clamp(x, 0.0, 1.0) * n + 0.5) / n;
}

// density used as a coverage threshold: thin parts tear into lumps instead of fading evenly
float cover(float n, float density) {
    return steps((n - (1.0 - density)) * 4.0 + 0.5, 3.0);
}

void main() {
    float mode = floor(texCoord0.x * 0.5);
    float u = texCoord0.x - mode * 2.0;
    float v = texCoord0.y;
    float t = GameTime * 24000.0;
    float a = vertexColor.a;

    vec3 body = vertexColor.rgb;
    float lum = dot(body, vec3(0.2126, 0.7152, 0.0722));
    vec3 white = vec3(clamp(lum / 0.46, 0.15, 1.2));
    vec3 deep = max(mix(vec3(lum), body, 1.3), 0.0) * 0.42;
    vec3 lightc = mix(body * 1.4, white, 0.24);
    vec3 pale = mix(body * 1.3, white, 0.68);
    vec3 foam = mix(body * 1.25, white, 0.9);

    vec3 ray = normalize(viewPos);
    float nl = length(worldNormal);
    vec3 n3 = nl > 1.0e-4 ? worldNormal / nl : -ray;
    float rim = pow(1.0 - abs(dot(ray, n3)), 1.5);

    vec3 col;
    float alpha;
    if (mode < 0.5 || (mode > 6.5 && mode < 7.5)) {
        bool jet = mode > 0.5;
        // stretched height: a feature rises faster the higher it gets
        float g = log(1.0 + 0.11 * v) / 0.11;
        float px = 10.0;
        float x = u * px + v * (jet ? 0.3 : 0.8) + t * (jet ? 0.05 : 0.06);
        float y = g - t * (jet ? 0.75 : 0.32);
        float n = fbm(snap(vec2(x, y * 0.32), vec2(4.0, 5.0)), px);
        float m = vnoise(vec2(x * 0.5, y * 0.1 + 7.0), px * 0.5);
        float streak = steps(smoothstep(jet ? 0.5 : 0.56, jet ? 0.64 : 0.7, n), 3.0);
        float b = fract(g * 0.16 - t * (jet ? 0.12 : 0.05) + 0.12 * sin(u * TAU));
        float band = smoothstep(0.86, 0.92, b) * (1.0 - smoothstep(0.95, 1.0, b));
        col = mix(deep, body, 0.2 + 0.7 * steps(m, 4.0));
        col = mix(col, lightc, rim * 0.75);
        col = mix(col, foam, streak * (0.55 + 0.45 * rim));
        col = mix(col, pale, band * 0.35);
        alpha = a * clamp(0.34 + 0.52 * rim + 0.38 * streak + 0.15 * band + (jet ? 0.12 : 0.0), 0.0, 1.0);
    } else if (mode < 1.5) {
        float g = log(1.0 + 0.11 * v) / 0.11;
        float px = 8.0;
        float x = u * px + v * 0.45 + t * 0.025;
        float y = g * 0.8 - t * 0.15;
        float n = fbm(snap(vec2(x, y * 0.42), vec2(3.0, 5.0)), px);
        float keep = steps(smoothstep(0.47, 0.6, n), 3.0);
        col = mix(lightc, foam, smoothstep(0.5, 0.85, n));
        col = mix(col, pale, rim * 0.5);
        alpha = a * keep * (0.28 + 0.6 * rim);
    } else if (mode < 2.5) {
        float px = 10.0;
        float lr = log(max(v, 0.3));
        float x = u * px - lr * px * 0.3 + t * 0.04;
        float y = v * 0.55 + t * 0.02;
        x += 1.3 * (vnoise(vec2(x * 0.5, y * 0.6 + t * 0.004), px * 0.5) - 0.5);
        float n = fbm(snap(vec2(x, y), vec2(3.0, 3.0)), px);
        vec3 dark = mix(vec3(lum * 0.5), deep, 0.45);
        vec3 bright = mix(vec3(lum * 1.25), lightc, 0.25);
        col = mix(dark, bright, steps(smoothstep(0.35, 0.85, n), 4.0) * (0.45 + 0.55 * rim));
        alpha = cover(n, a) * 0.94;
    } else if (mode < 3.5) {
        float f = v / 16.0;
        float lf = log(max(f, 0.01));
        float turn = u + 0.255 * lf + t * 0.007;
        float warp = vnoise(vec2(turn * 10.0, lf * 3.0), 10.0) - 0.5;
        float arm = 0.5 + 0.5 * cos(TAU * turn * 5.0 + warp * 1.6);
        arm = arm * arm * arm;
        float fl = vnoise(snap(vec2(turn * 30.0, lf * 6.0 + t * 0.05), vec2(2.0, 2.0)), 30.0);
        float fleck = step(0.74, fl) * (0.4 + 0.6 * arm);
        float eye = 1.0 - smoothstep(0.02, 0.3, f);
        float crest = smoothstep(0.78, 0.85, f) * (1.0 - smoothstep(0.88, 0.95, f));
        crest *= 0.55 + 0.45 * vnoise(snap(vec2(u * 40.0 - t * 0.2, f * 10.0), vec2(2.0, 1.0)), 40.0);
        col = mix(body, deep, eye * 0.85);
        col = mix(col, lightc, steps(arm, 3.0) * 0.4 * (0.3 + 0.7 * f));
        col = mix(col, foam, clamp(fleck * 0.9 + steps(crest, 3.0), 0.0, 1.0));
        col = mix(col, lightc, rim * 0.3);
        alpha = a * clamp(0.6 + 0.25 * arm + 0.35 * fleck + 0.4 * crest, 0.0, 1.0);
    } else if (mode < 4.5) {
        float px = 14.0;
        float x = u * px + v * 0.9 + t * 0.08;
        float y = v * 0.9 - t * 0.28;
        float n = fbm(snap(vec2(x, y), vec2(3.0, 3.0)), px);
        col = mix(pale, foam, smoothstep(0.45, 0.85, n));
        col = mix(col, lightc, (1.0 - rim) * 0.3);
        alpha = cover(n, a) * (0.5 + 0.4 * rim) * min(1.0, a * 2.0);
    } else if (mode < 5.5) {
        col = body;
        alpha = a;
    } else if (mode < 6.5) {
        float seed = floor(v * 0.5);
        vec2 q = (floor(vec2(u, v - seed * 2.0) * 10.0) + 0.5) / 10.0;
        float d = length(q - 0.5) * 2.0;
        float n = vnoise(q * 4.0 + vec2(seed * 7.13, seed * 3.71 + t * 0.02), 100000.0);
        float edge = 0.5 + 0.4 * n;
        float mask = 1.0 - smoothstep(edge - 0.05, edge + 0.05, d);
        col = body * (0.82 + 0.36 * n);
        alpha = a * mask * (1.0 - 0.45 * d);
    } else {
        float px = 16.0;
        float x = u * px + t * 0.01;
        float y = v * 0.8 + t * 0.45;
        float n = fbm(snap(vec2(x, y * 0.3), vec2(3.0, 6.0)), px);
        col = mix(lightc, foam, smoothstep(0.45, 0.85, n));
        col = mix(col, pale, rim * 0.4);
        alpha = cover(n, a) * (0.4 + 0.45 * rim);
    }

    alpha = clamp(alpha, 0.0, 1.0);
    if (alpha <= AlphaCut) {
        discard;
    }
    vec4 fogged = linear_fog(vec4(col, 1.0), fog_distance(viewPos, FogShape), FogStart, FogEnd, FogColor);
    fragColor = vec4(fogged.rgb * alpha, alpha) * ColorModulator;
}
