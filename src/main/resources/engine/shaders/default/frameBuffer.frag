// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec2 textureCoord;

out vec4 fragColor;

uniform sampler2D screenTexture;

void main() {
    vec4 textureColor = texture(screenTexture, textureCoord);
    vec4 color = texture(screenTexture, textureCoord);
    fragColor = vec4(1.0 - color.r, 1.0 - color.g, 1.0 - color.b, color.a);
    fragColor = color;
}