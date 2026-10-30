#version 150

// Lacuna volumetric rays (client/fx/FxTubes): one static mesh holds a batch of closed unit tubes (16 sides, rings at
// both cuts and both ends of the axis, end caps). Position.xy: unit circle (0, 0 at a cap's centre); Position.z:
// slot * 8 + ring (0: the extension behind A, 1: A, 2: B, 3: the extension past B). Each slot's tube is read from the
// data texture (row TubeBase + slot, 9 RGBA32F texels) and the unit tube is placed on its axis A -> B, radius
// tapering linearly from A to B, extended past the ends just enough to hold the slanted cuts that join a trail's
// segments seamlessly. The fragment stage integrates the light through the round volume.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D TubeData;
uniform int TubeBase;
uniform int TubeCount;

out vec3 worldPos;
flat out vec4 tubeA;
flat out vec4 tubeB;
flat out vec4 rimA;
flat out vec4 rimB;
flat out vec4 coreA;
flat out vec4 coreB;
flat out vec4 planeA;
flat out vec4 planeB;
flat out vec4 style;

// how far a cut slanted by n reaches past the end of a tube of radius r along axis
float reach(vec3 n, vec3 axis, float r) {
    float c = clamp(abs(dot(n, axis)), 0.5, 1.0);
    return r * sqrt(1.0 - c * c) / c;
}

void main() {
    int slot = int(floor(Position.z / 8.0 + 0.0625));
    float ring = Position.z - float(slot) * 8.0;
    if (slot >= TubeCount) {
        // an unused slot of the batch: every vertex on one point outside the view, no fragment
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        worldPos = vec3(0.0);
        return;
    }
    int row = TubeBase + slot;
    tubeA = texelFetch(TubeData, ivec2(0, row), 0);
    tubeB = texelFetch(TubeData, ivec2(1, row), 0);
    rimA = texelFetch(TubeData, ivec2(2, row), 0);
    rimB = texelFetch(TubeData, ivec2(3, row), 0);
    coreA = texelFetch(TubeData, ivec2(4, row), 0);
    coreB = texelFetch(TubeData, ivec2(5, row), 0);
    planeA = texelFetch(TubeData, ivec2(6, row), 0);
    planeB = texelFetch(TubeData, ivec2(7, row), 0);
    style = texelFetch(TubeData, ivec2(8, row), 0);

    vec3 a = tubeA.xyz;
    vec3 ab = tubeB.xyz - a;
    float len = length(ab);
    vec3 axis = len > 1.0e-5 ? ab / len : vec3(0.0, 1.0, 0.0);
    float along;
    float r;
    if (ring < 0.5) {
        r = tubeA.w;
        along = -reach(planeA.xyz, axis, r);
    } else if (ring < 1.5) {
        r = tubeA.w;
        along = 0.0;
    } else if (ring < 2.5) {
        r = tubeB.w;
        along = len;
    } else {
        r = tubeB.w;
        along = len + reach(planeB.xyz, axis, r);
    }
    vec3 ref = abs(axis.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 n = normalize(cross(axis, ref));
    vec3 b = cross(axis, n);
    // the 16-sided mesh circumscribes the round tube it stands for (1 / cos(pi / 16))
    vec3 pos = a + axis * along + (n * Position.x + b * Position.y) * (r * 1.0196);

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    worldPos = pos;
}
