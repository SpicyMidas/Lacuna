#version 150

// Lacuna tide vortex (Tidecaller Maelstrom and geyser), POSITION_TEX_COLOR_NORMAL. Positions arrive camera-relative in
// world axes (the camera rotation lives in ModelViewMat), so the position is also the view ray of the fresnel term.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 viewPos;
out vec3 worldNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    viewPos = Position;
    worldNormal = Normal;
}
