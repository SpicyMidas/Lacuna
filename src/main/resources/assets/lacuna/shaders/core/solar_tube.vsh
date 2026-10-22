#version 150

// Lacuna 3D light tubes (client/solar/SolarTubes): one static closed tube mesh (16 sides, 48 rings, end caps), bent
// and scaled here onto each shaft of light. Position.xy: unit circle (0, 0 at a cap's centre), Position.z: 0..1 along.
// The axis is the quadratic Bezier curve TubeA -> TubeK -> TubeB (camera-relative blocks; a straight shaft has TubeK
// at its midpoint), rings closer together near TubeA (where the target and the eye usually are). The fragment stage
// gets the local axis point, direction and radius and integrates the light along the view ray through the tube.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 TubeA;
uniform vec3 TubeK;
uniform vec3 TubeB;
uniform vec2 TubeRadius;
uniform vec4 TubeColorA;
uniform vec4 TubeColorB;
uniform vec2 TubeV;

out vec3 worldPos;
out vec3 axisPos;
out vec3 axisDir;
out float radius;
out float along;
out vec4 vertexColor;

void main() {
    float q = Position.z * Position.z;
    float p = 1.0 - q;
    vec3 centre = p * p * TubeA + 2.0 * q * p * TubeK + q * q * TubeB;
    vec3 chord = TubeB - TubeA;
    vec3 tangent = p * (TubeK - TubeA) + q * (TubeB - TubeK);
    if (dot(tangent, tangent) < 1.0e-12) {
        tangent = chord;
    }
    tangent = normalize(tangent);
    // one reference for the whole tube, so the rings never twist
    vec3 ref = abs(normalize(chord).y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 n = normalize(cross(tangent, ref));
    vec3 b = cross(tangent, n);
    float r = mix(TubeRadius.x, TubeRadius.y, q);
    // the 16-sided mesh circumscribes the round tube it stands for (1 / cos(pi / 16))
    vec3 pos = centre + (n * Position.x + b * Position.y) * (r * 1.0196);

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    worldPos = pos;
    axisPos = centre;
    axisDir = tangent;
    radius = r;
    along = mix(TubeV.x, TubeV.y, q);
    vertexColor = mix(TubeColorA, TubeColorB, q);
}
