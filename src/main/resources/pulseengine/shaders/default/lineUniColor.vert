#version 150 core

in vec3 position;

out vec4 vertexColor;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform int color;

vec4 getColor(int rgba) {
    int r = (rgba >> 24) & 255;
    int g = (rgba >> 16) & 255;
    int b = (rgba >> 8) & 255;
    int a = (rgba & 255);
    return vec4(r, g, b, a) / 255.0f;
}

void main() {
    vertexColor = getColor(color);
    mat4 mvp = projection * view * model;
    gl_Position = mvp * vec4(position, 1.0);
}