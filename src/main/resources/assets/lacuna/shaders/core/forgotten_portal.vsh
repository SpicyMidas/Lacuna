#version 150

// Lacuna Forgotten Portal: like vanilla rendertype_end_portal, the only varying is the projected screen position,
// so every face (and the full-screen transition quad) samples one continuous screen-space field.

#moj_import <projection.glsl>

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 texProj0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texProj0 = projection_from_position(gl_Position);
}
