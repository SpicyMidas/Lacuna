#version 150

// Vertex stage of the Lacuna soft billboards with depth (solar_soft_glow, solar_soft_corona): POSITION_TEX_COLOR with
// the camera-relative position passed on for the depth and fog helpers (shaders/include/solar_depth.glsl).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 worldPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    worldPos = Position;
}
