#version 150 core

in vec2 textureCoord;

out vec4 fragColor;

uniform sampler2D tex;

void main() {
    vec4 textureColor = texture(tex, textureCoord);
    fragColor = textureColor;
}