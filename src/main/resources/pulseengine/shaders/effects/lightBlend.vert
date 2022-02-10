// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec2 position;
in vec2 texCoord;

out vec2 textureCoord;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    textureCoord = texCoord;
}