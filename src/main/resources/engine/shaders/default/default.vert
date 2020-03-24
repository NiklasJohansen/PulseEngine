// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec3 position;
in uint color;

out vec4 vertexColor;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

vec4 getColor(uint rgba) {
    float r = ((rgba >> uint(24)) & uint(255)) / 255f;
    float g = ((rgba >> uint(16)) & uint(255)) / 255f;
    float b = ((rgba >> uint(8))  & uint(255)) / 255f;
    float a = (rgba & uint(255)) / 255f;
    return vec4(r, g, b, a);
}

void main() {
    vertexColor = getColor(color);
    mat4 mvp = projection * view * model;
    gl_Position = mvp * vec4(position, 1.0);
}


