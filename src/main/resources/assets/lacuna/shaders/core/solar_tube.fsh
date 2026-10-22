#version 150

#moj_import <lacuna:solar_depth.glsl>

// Lacuna 3D light tubes (client/solar/SolarTubes): a shaft of light as a real round volume. Drawn on the back faces
// of a closed tube mesh (they cover the tube's footprint exactly once, whether the eye is outside or inside it), each
// pixel intersects its view ray with the tube (the local axis and radius come from the vertex stage) and integrates
// the light's radial profile along the part of the chord that lies in front of the scene and of the eye, in closed
// form (Gaussian profiles: erf). So the light is as thick as the volume really is from any angle, brightest where the
// eye looks along it (up to a soft saturation), still there when the eye is inside it looking up, and it melts into
// the ground, clouds and walls it enters (the chord shortens smoothly). Additive output only: it never darkens or
// greys what is behind it, and it dims with distance instead of taking the fog colour.
// Same layers, colours and motion as solar_column.fsh / solar_volume.fsh: TubeMode.x 0 = the pillar (white-hot
// core, striated golden body, bands pouring down, heat shimmer, soft halo), 1 = the soft hazy sunbeam. TubeMode.y
// flags: 2 = sunlight from the sky's sun disc (no distance dimming), 4 = fades out where it passes close to the eye.

uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec3 TubeA;
uniform vec3 TubeK;
uniform vec3 TubeB;
uniform vec2 TubeMode;

in vec3 worldPos;
in vec3 axisPos;
in vec3 axisDir;
in float radius;
in float along;
in vec4 vertexColor;

out vec4 fragColor;

const float PI = 3.14159265;
/** Soft saturation of the light seen along a shaft: at most this many times its brightness seen across it. */
const float GLOW_LIMIT = 2.5;

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

float erf_approx(float x) {
    x = clamp(x, -6.0, 6.0);
    float x2 = x * x;
    float e = 1.0 - exp(-x2 * (1.2732395 + 0.147 * x2) / (1.0 + 0.147 * x2));
    return sign(x) * sqrt(max(e, 0.0));
}

// Optical depth of the radial profile exp(-k x^2) (x: distance from the axis in radii) along the ray between ua and
// ub (distances along the ray from its closest approach to the axis, in radii), where the ray passes b2 (squared
// radii) from the axis at sin(angle to the axis) = dn. Normalised: a ray crossing the axis at right angles gets 1.
float path(float k, float b2, float ua, float ub, float dn) {
    float base = exp(-k * b2);
    if (base < 1.0e-5) {
        return 0.0;
    }
    float s = sqrt(k) * dn;
    if (s * (ub - ua) < 1.0e-3) {
        return base * (ub - ua) * sqrt(k / PI);
    }
    return base * (erf_approx(s * ub) - erf_approx(s * ua)) * 0.5 / dn;
}

float glow(float depth) {
    return GLOW_LIMIT * (1.0 - exp(-depth / GLOW_LIMIT));
}

// keeps the part of the ray [t0, t1] on the inner side (n) of the plane through o
void clip_plane(vec3 o, vec3 n, vec3 dir, inout float t0, inout float t1) {
    float s0 = -dot(o, n);
    float dn = dot(dir, n);
    if (abs(dn) < 1.0e-6) {
        if (s0 < 0.0) {
            t1 = -1.0;
        }
        return;
    }
    float tc = -s0 / dn;
    if (dn > 0.0) {
        t0 = max(t0, tc);
    } else {
        t1 = min(t1, tc);
    }
}

void main() {
    float dist = length(worldPos);
    vec3 dir = worldPos / max(dist, 1.0e-5);
    vec3 axis = normalize(axisDir);
    float r = max(radius, 1.0e-4);
    int flags = int(TubeMode.y + 0.5);

    // the ray from the eye (the origin) against the round tube around the local axis
    vec3 mp = -axisPos - dot(-axisPos, axis) * axis;
    vec3 dp = dir - dot(dir, axis) * axis;
    float dd = dot(dp, dp);
    float t0;
    float t1;
    float tStar;
    float b2;
    vec3 closest;
    if (dd < 1.0e-7) {
        // looking straight along the axis
        closest = mp;
        b2 = dot(mp, mp);
        tStar = 0.0;
        t0 = -1.0e7;
        t1 = 1.0e7;
    } else {
        tStar = -dot(mp, dp) / dd;
        closest = mp + dp * tStar;
        b2 = dot(closest, closest);
        float h2 = (r * r - b2) / dd;
        if (h2 <= 0.0) {
            discard;
        }
        float h = sqrt(h2);
        t0 = tStar - h;
        t1 = tStar + h;
    }
    if (b2 >= r * r) {
        discard;
    }
    // the tube's ends, then the eye and the scene (terrain, entities, water, glass, clouds)
    vec3 endA = TubeK - TubeA;
    vec3 endB = TubeB - TubeK;
    if (dot(endA, endA) < 1.0e-10) {
        endA = TubeB - TubeA;
    }
    if (dot(endB, endB) < 1.0e-10) {
        endB = TubeB - TubeA;
    }
    clip_plane(TubeA, normalize(endA), dir, t0, t1);
    clip_plane(TubeB, -normalize(endB), dir, t0, t1);
    t0 = max(t0, 0.05);
    t1 = min(t1, lacuna_scene_distance());
    if (t1 <= t0) {
        discard;
    }

    float dn = sqrt(dd);
    float b2n = b2 / (r * r);
    float ua = (t0 - tStar) / r;
    float ub = (t1 - tStar) / r;
    // where the light seen through this pixel is: its closest approach within the visible chord
    float seen = clamp(tStar, t0, t1);
    float fade = (flags & 2) != 0 ? 1.0 : lacuna_fade_at(seen);
    if ((flags & 4) != 0) {
        fade *= smoothstep(1.5, 7.5, seen);
    }
    float intensity = vertexColor.a * fade;
    if (intensity <= 0.001) {
        discard;
    }

    // signed distance from the axis across the view (left / right of it), for the motion of the light
    float x = sqrt(b2n);
    vec3 side = cross(axis, mp);
    float xs = dot(side, side) > 1.0e-10 && dot(closest, side) < 0.0 ? -x : x;
    float t = GameTime * 1200.0;
    float h = along;
    vec3 tint = vertexColor.rgb;
    vec3 col;
    if (TubeMode.x > 0.5) {
        // soft sunbeam: no core line, a broad hazy body
        float drift = fbm(vec2(xs * 3.0, h * 0.05 + t * 1.2));
        float motes = noise(vec2(xs * 11.0 + t * 0.3, h * 0.6 + t * 3.0));
        float shimmer = (0.55 + 0.6 * drift) * (0.9 + 0.2 * motes);
        float wide = glow(path(4.0, b2n, ua, ub, dn));
        float inner = glow(path(16.0, b2n, ua, ub, dn));
        col = max(tint * wide + (vec3(1.0, 0.97, 0.9) - tint) * 0.25 * inner, 0.0) * shimmer;
    } else {
        // heat shimmer: the outer part ripples, the core stays straight
        float ripple = (noise(vec2(h * 0.11 + t * 1.7, 4.3)) - 0.5) * 0.16 + (noise(vec2(h * 0.37 - t * 2.9, 9.1)) - 0.5) * 0.08;
        float xn = xs + ripple * smoothstep(0.15, 0.9, x);
        // long striations sliding along the pillar, gently swaying; bands of light pouring down from the sky
        float sway = 0.25 * sin(h * 0.045 + t * 0.6);
        float streak = fbm(vec2((xn + sway * 0.2) * 6.0, h * 0.04 + t * 2.4));
        float fine = noise(vec2(xn * 19.0 + t * 0.8, h * 0.11 + t * 5.0));
        float band = 0.5 + 0.5 * sin(h * 0.16 + t * 8.0);
        band = mix(0.78, 1.2, band * band * band);
        float surge = 0.5 + 0.5 * sin(h * 0.035 + t * 3.1);

        float core = glow(path(70.0, b2n, ua, ub, dn)) * (0.92 + 0.08 * surge);
        float body = glow(path(7.5, b2n, ua, ub, dn)) * (0.5 + 0.8 * streak) * band;
        float halo = glow(path(2.2, b2n, ua, ub, dn)) * (0.38 + 0.35 * fine) * (0.85 + 0.15 * band);
        vec3 white = vec3(1.0, 0.985, 0.94);
        col = white * core * 1.75 + mix(tint, white, 0.3) * body * 1.05 + tint * halo * 0.5;
    }
    // a soft rim (no outline where the round volume meets the sky)
    intensity *= 1.0 - smoothstep(0.8, 1.0, x);
    fragColor = vec4(col * intensity * ColorModulator.rgb, 0.0);
}
