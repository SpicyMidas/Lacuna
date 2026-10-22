#version 150

// Vertex stage of the Lacuna light shafts with depth (solar_volume, beam_volume), DefaultVertexFormat.PARTICLE as
// written by client/solar/SolarVolume from the effects' camera-facing ribbons:
// Position: camera-relative; UV0.x: signed distance from the axis in blocks (-r on one edge, +r on the other);
// UV0.y: blocks along; Color: tint and intensity; UV2.x: radius at this end, r = exp(x / 2048 - 8); UV2.y: flags
// (1: soft sunbeam profile, 2: sunlight from the sky, no fog).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 worldPos;
out float radius;
flat out int flags;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    worldPos = Position;
    radius = exp(float(UV2.x) / 2048.0 - 8.0);
    flags = UV2.y;
}
