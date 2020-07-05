// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = vertexColor;
}