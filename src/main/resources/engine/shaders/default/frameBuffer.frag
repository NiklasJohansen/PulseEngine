// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec2 textureCoord;

out vec4 fragColor;

uniform sampler2D screenTexture;

void main() {
    vec4 textureColor = texture(screenTexture, textureCoord);
    vec4 color = texture(screenTexture, textureCoord);
    fragColor = vec4(1f - color.r, 1f - color.g, 1f - color.b, color.a);
    fragColor = color;
}