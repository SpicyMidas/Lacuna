#version 150

#moj_import <lacuna:solar_depth.glsl>

// Lacuna 3D light tubes (client/solar/SolarTubes): a shaft of light as a real round volume. Drawn on the back faces
// of a closed tube mesh (they cover the tube's footprint exactly once, whether the eye is outside or inside it), each
// pixel intersects its view ray with the tube (the local axis and radius are evaluated on the shaft's curve) and integrates
// the light's radial profile along the visible part of the chord in closed form (Gaussian profiles: erf). So the light
// is as thick as the volume really is from any angle, brightest where the eye looks along it (up to a soft
// saturation), and still there when the eye is inside it looking up.
// Occlusion: only what stands IN FRONT of the light hides it. Ground, walls, clouds and hills in front of the axis cut
// the chord where they are (the shaft melts into the ground it enters); whatever lies BEHIND the axis (sky, terrain,
// trees or clouds beyond the shaft, even inside its wide outer layers) never thins it, so a shaft looks equally wide and
// strong against the sky and against the landscape.
// Output is premultiplied for ONE, ONE_MINUS_SRC_ALPHA: the light adds (alpha 0: it never darkens or greys what is
// behind it, and it dims with distance instead of taking the fog colour); TubeFx.x > 0 also lays a bright, opaque-ish
// body of its own colour under that light (bright gold or white, never darker than a bright sky), so the shaft reads
// with the same body over the blue sky and over dark terrain.
// Same layers, colours and motion as solar_column.fsh / beam_volume.fsh: TubeMode.x 0 = the pillar (white-hot core,
// striated golden body, bands pouring down, heat shimmer, soft halo), 1 = the soft hazy sunbeam, 2 = the energy beam of
// the Divine Magnifying Glass (flowing turbulence, ripple rings, hot core). TubeMode.y flags: 2 = sunlight from the
// sky's sun disc (no distance dimming), 4 = fades out where it passes close to the eye, 8 = fades out only right at the
// eye (within about two blocks).
// TubeFx: x = body cover (0..1, the body's opacity where the light is densest; independent of the light's own
// intensity), y = rotating light streaks winding up the shaft, z = rising embers (both scaled by the intensity).

uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec3 TubeA;
uniform vec3 TubeK;
uniform vec3 TubeB;
uniform vec2 TubeMode;
uniform vec2 TubeRadius;
uniform vec4 TubeColorA;
uniform vec4 TubeColorB;
uniform vec2 TubeV;
uniform vec3 TubeFx;

in vec3 worldPos;
in float param;

out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;
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

vec3 bright(vec3 c) {
    return c / max(max(c.r, c.g), max(c.b, 1.0e-3));
}

void main() {
    float dist = length(worldPos);
    vec3 dir = worldPos / max(dist, 1.0e-5);
    // the local axis, radius and colour where this fragment lies on the tube
    float q = clamp(param, 0.0, 1.0);
    float p = 1.0 - q;
    vec3 axisPos = p * p * TubeA + 2.0 * q * p * TubeK + q * q * TubeB;
    vec3 axisDir = p * (TubeK - TubeA) + q * (TubeB - TubeK);
    if (dot(axisDir, axisDir) < 1.0e-12) {
        axisDir = TubeB - TubeA;
    }
    vec3 axis = normalize(axisDir);
    float r = max(mix(TubeRadius.x, TubeRadius.y, q), 1.0e-4);
    vec4 vertexColor = mix(TubeColorA, TubeColorB, q);
    float along = mix(TubeV.x, TubeV.y, q);
    int flags = int(TubeMode.y + 0.5);
    int profile = int(TubeMode.x + 0.5);

    // the ray from the eye (the origin) against the round tube around the local axis
    vec3 mp = -axisPos - dot(-axisPos, axis) * axis;
    vec3 dp = dir - dot(dir, axis) * axis;
    float dd = dot(dp, dp);
    float t0;
    float t1;
    float tStar;
    float b2;
    vec3 closest;
    bool alongAxis = dd < 1.0e-7;
    if (alongAxis) {
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
    // the tube's ends and the eye
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
    if (t1 <= t0) {
        discard;
    }
    // the scene (terrain, entities, water, glass, clouds): it cuts the light where it stands in front of the axis
    // (where the light seen through this pixel is), and not at all once it lies behind it; a short soft ramp in
    // between keeps the shaft's foot melting into the ground without a seam. Eye inside the volume looking away from
    // the axis: plain physical cut (there is no "behind the light" there).
    float scene = lacuna_scene_distance();
    float tc = clamp(tStar, t0, t1);
    float soft = clamp(0.3 * r, 0.02, 3.0);
    float relax = alongAxis ? 0.0
            : smoothstep(0.0, 0.35 * r, tStar - t0) * smoothstep(max(tc - soft, t0), tc + 0.25 * soft, scene);
    float tEnd = mix(min(t1, scene), t1, relax);
    if (tEnd <= t0) {
        discard;
    }

    float dn = sqrt(max(dd, 1.0e-7));
    float b2n = b2 / (r * r);
    float ua = (t0 - tStar) / r;
    float ub = (tEnd - tStar) / r;
    // where the light seen through this pixel is: its closest approach within the visible chord
    float seen = clamp(tStar, t0, tEnd);
    float fade = (flags & 2) != 0 ? 1.0 : lacuna_fade_at(seen);
    if ((flags & 4) != 0) {
        fade *= smoothstep(1.5, 7.5, seen);
    }
    if ((flags & 8) != 0) {
        fade *= smoothstep(0.4, 2.2, seen);
    }
    float intensity = vertexColor.a * fade;
    if (intensity <= 0.001 && TubeFx.x * fade <= 0.001) {
        discard;
    }

    // signed distance from the axis across the view (left / right of it), for the motion of the light
    float x = sqrt(b2n);
    vec3 side = cross(axis, mp);
    float xs = dot(side, side) > 1.0e-10 && dot(closest, side) < 0.0 ? -x : x;
    float t = GameTime * 1200.0;
    float h = along;
    vec3 tint = vertexColor.rgb;
    vec3 white = vec3(1.0, 0.985, 0.94);
    vec3 col;
    float body;
    float hot;
    if (profile == 1) {
        // soft sunbeam: no core line, a broad hazy body
        float drift = fbm(vec2(xs * 3.0, h * 0.05 + t * 1.2));
        float motes = noise(vec2(xs * 11.0 + t * 0.3, h * 0.6 + t * 3.0));
        float shimmer = (0.55 + 0.6 * drift) * (0.9 + 0.2 * motes);
        float wide = glow(path(4.0, b2n, ua, ub, dn));
        float inner = glow(path(16.0, b2n, ua, ub, dn));
        col = max(tint * wide + (vec3(1.0, 0.97, 0.9) - tint) * 0.25 * inner, 0.0) * shimmer;
        body = wide;
        hot = inner * 0.3;
    } else if (profile == 2) {
        // energy beam: two layers of flowing turbulence travelling along it, ripple rings, a hot core
        float n1 = noise(vec2(h * 1.3 - t * 6.0, xs * 4.0 + t * 0.7));
        float n2 = noise(vec2(h * 3.1 - t * 11.0, xs * 9.0 - t * 1.3));
        float flow = 0.55 + 0.3 * n1 + 0.25 * n2;
        float ripple = 0.85 + 0.15 * sin(h * 2.2 - t * 14.0);
        float core = glow(path(55.0, b2n, ua, ub, dn));
        float inner = glow(path(9.0, b2n, ua, ub, dn)) * flow * ripple;
        float outer = glow(path(2.2, b2n, ua, ub, dn)) * (0.35 + 0.25 * n1);
        vec3 hotTint = mix(tint, vec3(1.0), 0.75);
        col = hotTint * core * 1.4 + tint * inner * 1.1 + tint * outer * 0.6;
        body = core;
        hot = 1.0;
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
        float mid = glow(path(7.5, b2n, ua, ub, dn));
        float bodyLight = mid * (0.5 + 0.8 * streak) * band;
        float halo = glow(path(2.2, b2n, ua, ub, dn)) * (0.38 + 0.35 * fine) * (0.85 + 0.15 * band);
        col = white * core * 1.75 + mix(tint, white, 0.3) * bodyLight * 1.05 + tint * halo * 0.5;
        body = mid * (0.8 + 0.25 * band) + core;
        hot = core / max(mid + core, 1.0e-3);
    }

    // rotating light streaks winding up the shaft and rising embers, both on inner cylinders of the volume: seen on
    // its near and far side (the far side dimmer), wherever that point is visible
    float streaks = 0.0;
    float embers = 0.0;
    if ((TubeFx.y > 0.001 || TubeFx.z > 0.001) && !alongAxis) {
        vec3 ref = abs(normalize(TubeB - TubeA).y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        vec3 en = normalize(cross(axis, ref));
        vec3 eb = cross(axis, en);
        for (int layer = 0; layer < 2; layer++) {
            float strength = layer == 0 ? TubeFx.y : TubeFx.z;
            if (strength <= 0.001) {
                continue;
            }
            float rs = (layer == 0 ? 0.5 : 0.72) * r;
            float hs2 = (rs * rs - b2) / dd;
            if (hs2 <= 0.0) {
                continue;
            }
            float hs = sqrt(hs2);
            for (int k = 0; k < 2; k++) {
                float ts = k == 0 ? tStar - hs : tStar + hs;
                if (ts <= t0 || ts >= tEnd) {
                    continue;
                }
                vec3 point = dir * ts - axisPos;
                float sAx = dot(point, axis);
                vec3 lat = point - sAx * axis;
                float ang = atan(dot(lat, eb), dot(lat, en));
                float hh = along + sAx;
                float weight = k == 0 ? 1.0 : 0.55;
                if (layer == 0) {
                    // three slender helical streaks turning around the shaft and climbing it
                    float ph = ang * 3.0 + hh * (TAU / 26.0) - t * 2.6;
                    float s = pow(0.5 + 0.5 * cos(ph), 22.0);
                    s *= 0.55 + 0.45 * sin(hh * 0.06 - t * 1.7 + ang);
                    streaks += s * weight;
                } else {
                    // sparse embers rising through the outer light, twinkling
                    float cells = 22.0;
                    vec2 g = vec2((ang / TAU + 0.5) * cells, (hh - t * 7.0) / 2.6);
                    vec2 cell = floor(g);
                    cell.x = mod(cell.x, cells);
                    vec2 f = fract(g);
                    float chance = hash(cell + vec2(7.1, 3.3));
                    if (chance > 0.7) {
                        vec2 c = vec2(0.25 + 0.5 * hash(cell + vec2(1.3, 9.2)), 0.25 + 0.5 * hash(cell + vec2(5.9, 2.4)));
                        vec2 d = (f - c) * vec2(TAU * rs / cells, 2.6);
                        d.y *= 0.45;
                        float size = 0.1 + 0.08 * hash(cell + vec2(3.7, 8.8));
                        float twinkle = 0.55 + 0.45 * sin(t * 9.0 + chance * 40.0);
                        embers += exp(-dot(d, d) / (size * size)) * twinkle * weight * (1.0 - smoothstep(70.0, 170.0, ts));
                    }
                }
            }
        }
        streaks *= TubeFx.y;
        embers *= TubeFx.z;
    }
    col += mix(tint, white, 0.65) * streaks * 1.6 + vec3(1.0, 0.72, 0.32) * embers * 2.2;

    // a soft rim (no outline where the round volume meets the sky)
    float rim = 1.0 - smoothstep(0.8, 1.0, x);
    intensity *= rim;
    vec3 light = col * intensity * ColorModulator.rgb;
    // the body: a bright cover of the light's own colour (white towards the core), as opaque as the light is dense
    float cover = 0.0;
    vec3 paint = vec3(0.0);
    if (TubeFx.x > 0.001) {
        cover = clamp(TubeFx.x * fade * rim * (1.0 - exp(-1.6 * body)), 0.0, 0.97);
        paint = bright(mix(tint, white, clamp(0.25 + 0.7 * hot, 0.0, 1.0))) * ColorModulator.rgb * cover;
    }
    fragColor = vec4(paint + light, cover);
}
