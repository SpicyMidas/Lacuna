#version 150

// Lacuna Forgotten Portal: the portal surface (block entity faces, client/portal/ForgottenPortalRenderer) and the void
// of its travel transition (one full-screen GUI quad, client/portal/PortalTransition).
//
// Modelled on vanilla rendertype_end_portal: every layer is projected from the screen position (texProj0), never from
// the block, so the four blocks of the opening are one continuous field with no seams. Instead of the End's starfield:
//   * a base of two copies of the fog texture (Sampler0, tiling grey-violet noise with soft swirls) at different
//     scales and turns, the second warped by the first, slowly drifting and turning against each other;
//   * MOTE_LAYERS parallax layers of the mote texture (Sampler1, sparse soft pale dots on black): far layers small,
//     nearly still and faint lavender (stars), near layers large, quicker and pale (motes); each one turned, scaled
//     and drifting with time, glimmering where the fog is thick.
// Swirl (0..1) is 0 on the portal surface. The transition raises it: the field winds into a turning vortex round the
// screen centre with a pale heart and a violet-black vignette, timed by its own clock (VoidTime, GameTime units) so a
// dimension change never makes it jump. ColorModulator.a is the transition's fade (the surface itself is opaque).
// Every time term turns over a whole number of times per GameTime unit, so its 20-minute wrap is seamless.

#moj_import <matrix.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec2 ScreenSize;
uniform float Swirl;
uniform float VoidTime;

in vec4 texProj0;

out vec4 fragColor;

const float TAU = 6.28318531;
const int MOTE_LAYERS = 8;

// layer tints, far to near: faint lavender stars to pale grey-white motes
const vec3[] LAYER_COLORS = vec3[](
    vec3(0.19, 0.16, 0.31),
    vec3(0.22, 0.19, 0.36),
    vec3(0.26, 0.22, 0.40),
    vec3(0.30, 0.26, 0.43),
    vec3(0.34, 0.31, 0.45),
    vec3(0.39, 0.36, 0.47),
    vec3(0.45, 0.43, 0.51),
    vec3(0.53, 0.51, 0.57)
);

// drift of each layer in whole texture repeats per GameTime unit (whole numbers: seamless at the wrap)
const vec2[] LAYER_DRIFT = vec2[](
    vec2(2.0, 7.0),
    vec2(-3.0, 9.0),
    vec2(4.0, 12.0),
    vec2(-5.0, 15.0),
    vec2(6.0, 19.0),
    vec2(-7.0, 24.0),
    vec2(8.0, 30.0),
    vec2(-9.0, 38.0)
);

void main() {
    vec2 screen = texProj0.xy / texProj0.w;
    // centred on the screen, in screen heights (no stretching with the aspect ratio)
    vec2 p = (screen - 0.5) * vec2(ScreenSize.x / max(ScreenSize.y, 1.0), 1.0);
    float r = length(p);
    float t = Swirl > 0.0 ? VoidTime : GameTime;

    if (Swirl > 0.0) {
        // the vortex: a spiral twist that tightens toward the centre, the whole of it turning (one turn in ~2 s)
        p = mat2_rotate_z(Swirl * (1.15 / (r + 0.22) + t * TAU * 600.0)) * p;
    }

    // swirling fog: the second copy is pushed around by the first, and they turn and drift against each other
    vec2 fa = mat2_rotate_z(0.6 + t * TAU * 3.0) * p * 0.42 + vec2(5.0, -3.0) * t;
    vec3 fogA = texture(Sampler0, fa).rgb;
    vec2 fb = mat2_rotate_z(-1.9 - t * TAU * 5.0) * p * 0.71 + vec2(-4.0, 7.0) * t + (fogA.rb - 0.22) * 0.45;
    vec3 fogB = texture(Sampler0, fb).rgb;
    vec3 fog = fogA * 0.55 + fogB * 0.6;
    float lum = dot(fog, vec3(0.3, 0.4, 0.3));
    // deep violet-black hollows, pale grey-violet drifts
    vec3 color = fog * (0.25 + 0.9 * smoothstep(0.05, 0.45, lum)) * 0.85 + vec3(0.012, 0.008, 0.022);

    float thick = smoothstep(0.08, 0.5, lum);
    for (int i = 0; i < MOTE_LAYERS; i++) {
        float layer = float(i);
        float closeness = layer / float(MOTE_LAYERS - 1);
        mat2 turn = mat2_rotate_z(layer * layer * 2.39996 + layer * 1.7);
        vec2 uv = turn * p * mix(3.4, 0.75, closeness) + LAYER_DRIFT[i] * t + vec2(0.37, 0.61) * layer;
        vec3 dots = texture(Sampler1, uv).rgb;
        float glimmer = 0.65 + 0.35 * sin(t * TAU * (53.0 + 17.0 * layer) + layer * 2.1 + (uv.x + uv.y) * TAU);
        color += dots * LAYER_COLORS[i] * glimmer * (0.75 + 0.5 * thick) * 1.6;
    }

    if (Swirl > 0.0) {
        color += vec3(0.16, 0.13, 0.24) * Swirl * exp(-r * r * 9.0);
        color = mix(color, vec3(0.03, 0.018, 0.05), smoothstep(0.2, 0.95, r) * Swirl * 0.9);
    }

    fragColor = vec4(color, 1.0) * ColorModulator;
}
