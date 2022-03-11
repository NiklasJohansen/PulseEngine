#version 150 core

in vec2 textureCoord;
in vec4 vertexColor;

out vec4 fragColor;

uniform sampler2D tex;
uniform bool sampleTexture;

void main() {
    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);
    if (sampleTexture)
        textureColor = texture(tex, textureCoord);
    fragColor = vertexColor * textureColor;
}