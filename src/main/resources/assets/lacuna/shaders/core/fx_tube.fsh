#version 150

#moj_import <lacuna:fx_depth.glsl>

// Lacuna volumetric rays (client/fx/FxTubes): a beam, bolt or trail of light as a real round volume. Drawn on the
// back faces of a closed tube mesh (they cover the tube's footprint exactly once, whether the eye is outside or
// inside it), each pixel intersects its view ray with the tube and integrates the light's radial profile along the
// part of the chord that lies between the tube's end cuts, beyond the eye and in front of the scene, in closed form
// (Gaussian profiles: erf). So the ray is as thick as the volume really is from any angle, seen from the side it
// has the profile of the old flat ribbon, seen along its length it glows brighter (up to a soft saturation), and
// it melts into the ground, walls and clouds it enters: only what is in front of it hides it. Premultiplied output
// (ONE, ONE_MINUS_SRC_ALPHA): the light covers what is behind it as much as it shines, in its own colour.
// Layers (normalised radius x = distance from the axis / radius): a hot core line (core colour, strength core.a), a
// body and a wide halo in the rim colour (intensity rim.a). style.x softness 0 (crisp rod) .. 1 (soft haze),
// style.y flags (1 flowing turbulence and ripple rings, 2 no distance dimming, 4 fades near the eye), style.z flow
// speed.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec3 worldPos;
flat in vec4 tubeA;
flat in vec4 tubeB;
flat in vec4 rimA;
flat in vec4 rimB;
flat in vec4 coreA;
flat in vec4 coreB;
flat in vec4 planeA;
flat in vec4 planeB;
flat in vec4 style;

out vec4 fragColor;

const float PI = 3.14159265;
/** Soft saturation of the light seen along a ray: at most this many times its brightness seen across it. */
const float GLOW_LIMIT = 2.5;
/** 1 / (GLOW_LIMIT (1 - exp(-1 / GLOW_LIMIT))): a ray seen across its axis is exactly as bright as the flat ribbon. */
const float GLOW_NORM = 1.2133;

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

float erf_approx(float x) {
    x = clamp(x, -6.0, 6.0);
    float x2 = x * x;
    float e = 1.0 - exp(-x2 * (1.2732395 + 0.147 * x2) / (1.0 + 0.147 * x2));
    return sign(x) * sqrt(max(e, 0.0));
}

// Optical depth of the radial profile exp(-k x^2) along the ray between ua and ub (distances along the ray from its
// closest approach to the axis, in radii), where the ray passes b2 (squared radii) from the axis at sin(angle to the
// axis) = dn. Normalised: a ray crossing the axis at right angles gets 1.
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
    return GLOW_NORM * GLOW_LIMIT * (1.0 - exp(-depth / GLOW_LIMIT));
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

// The visible part [t0, t1] of the view ray inside the round tube of radius r around the axis: between the two end
// cuts, beyond the eye and in front of the scene. False when there is none.
bool span(float r, vec3 dir, float dd, float tStar, float b2, float far, out float t0, out float t1) {
    t0 = 0.0;
    t1 = -1.0;
    if (b2 >= r * r) {
        return false;
    }
    if (dd < 1.0e-7) {
        // looking straight along the axis
        t0 = -1.0e7;
        t1 = 1.0e7;
    } else {
        float h = sqrt((r * r - b2) / dd);
        t0 = tStar - h;
        t1 = tStar + h;
    }
    clip_plane(tubeA.xyz, planeA.xyz, dir, t0, t1);
    clip_plane(tubeB.xyz, -planeB.xyz, dir, t0, t1);
    t0 = max(t0, 0.05);
    t1 = min(t1, far);
    return t1 > t0;
}

void main() {
    float dist = length(worldPos);
    vec3 dir = worldPos / max(dist, 1.0e-5);
    vec3 a = tubeA.xyz;
    vec3 ab = tubeB.xyz - a;
    float len = max(length(ab), 1.0e-5);
    vec3 axis = ab / len;

    // the ray from the eye (the origin) against the axis line
    vec3 mp = -a - dot(-a, axis) * axis;
    vec3 dp = dir - dot(dir, axis) * axis;
    float dd = dot(dp, dp);
    float tStar = dd < 1.0e-7 ? 0.0 : -dot(mp, dp) / dd;
    vec3 closest = mp + dp * tStar;
    float b2 = dot(closest, closest);
    float far = fx_scene_distance();
    float t0;
    float t1;
    // where along the tube this pixel looks: the closest approach within the visible chord of the widest end, then
    // the chord of the tapered tube at that point
    if (!span(max(tubeA.w, tubeB.w), dir, dd, tStar, b2, far, t0, t1)) {
        discard;
    }
    float s = clamp(dot(dir * clamp(tStar, t0, t1) - a, axis) / len, 0.0, 1.0);
    float r = max(mix(tubeA.w, tubeB.w, s), 1.0e-4);
    if (!span(r, dir, dd, tStar, b2, far, t0, t1)) {
        discard;
    }
    float seen = clamp(tStar, t0, t1);
    s = clamp(dot(dir * seen - a, axis) / len, 0.0, 1.0);

    vec4 rim = mix(rimA, rimB, s);
    vec4 core = mix(coreA, coreB, s);
    float along = mix(planeA.w, planeB.w, s);
    float softness = clamp(style.x, 0.0, 1.0);
    int flags = int(style.y + 0.5);
    float b2n = b2 / (r * r);
    float x = sqrt(b2n);
    float intensity = rim.a * ((flags & 2) != 0 ? fx_thick_fog(seen) : fx_fade_at(seen));
    if ((flags & 4) != 0) {
        intensity *= smoothstep(0.35, 1.6, seen);
    }
    // a soft rim: no outline where the round volume ends
    intensity *= 1.0 - smoothstep(1.0 - (0.18 + 0.5 * softness), 1.0, x);
    if (intensity <= 0.002) {
        discard;
    }

    float dn = sqrt(dd);
    float ua = (t0 - tStar) / r;
    float ub = (t1 - tStar) / r;
    // signed distance from the axis across the view, for the motion of the light
    vec3 side = cross(axis, mp);
    float xs = dot(side, side) > 1.0e-10 && dot(closest, side) < 0.0 ? -x : x;
    float n1 = 0.5;
    float flow = 0.8;
    float ripple = 1.0;
    if ((flags & 1) != 0) {
        // two layers of flowing turbulence and shimmering ripple rings travelling along the ray
        float seconds = GameTime * 1200.0;
        float speed = style.z;
        n1 = noise(vec2(along * 1.3 - seconds * 6.0 * speed, xs * 4.0 + seconds * 0.7));
        float n2 = noise(vec2(along * 3.1 - seconds * 11.0 * speed, xs * 9.0 - seconds * 1.3));
        flow = 0.55 + 0.3 * n1 + 0.25 * n2;
        ripple = 0.85 + 0.15 * sin(along * 2.2 - seconds * 14.0 * speed);
    }
    float kCore = mix(55.0, 20.0, softness);
    float kBody = mix(9.0, 4.0, softness);
    float kHalo = mix(2.2, 1.3, softness);
    float hot = core.a > 0.001 ? glow(path(kCore, b2n, ua, ub, dn)) * core.a : 0.0;
    float body = glow(path(kBody, b2n, ua, ub, dn)) * flow * ripple;
    float halo = glow(path(kHalo, b2n, ua, ub, dn)) * (0.35 + 0.25 * n1);
    vec3 col = core.rgb * hot * 1.4 + rim.rgb * (body * 1.1 + halo * 0.6);
    vec3 light = col * intensity * ColorModulator.rgb;
    // the light as a luminous medium (as in solar_tube.fsh): it covers what is behind it as much as it shines, in its
    // own colour at full brightness (it never greys anything), so a ray looks the same width and strength over the
    // bright sky and over dark terrain instead of blooming over the one and thinning over the other
    float lum = max(max(light.r, light.g), light.b);
    // (the faintest haze stays a plain addition, so it never washes a dark sky in a dim tint)
    float shine = clamp((1.0 - exp(-2.5 * lum)) * smoothstep(0.04, 0.35, lum), 0.0, 0.985);
    fragColor = vec4(max(light / max(lum, 1.0e-4) * shine, light), shine);
}
