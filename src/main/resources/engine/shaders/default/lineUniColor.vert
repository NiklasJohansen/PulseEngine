// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec3 position;

out vec4 vertexColor;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform int color;

vec4 getColor(int rgba) {
    float r = ((rgba >> int(24)) & int(255)) / 255f;
    float g = ((rgba >> int(16)) & int(255)) / 255f;
    float b = ((rgba >> int(8))  & int(255)) / 255f;
    float a = (rgba & int(255)) / 255f;
    return vec4(r, g, b, a);
}

void main() {
    vertexColor = getColor(color);
    mat4 mvp = projection * view * model;
    gl_Position = mvp * vec4(position, 1.0);
}