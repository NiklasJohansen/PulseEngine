#version 150 core

in vec4 vertexColor;
in vec2 texCoord;
in float texIndex;

out vec4 fragColor;

uniform sampler2DArray textureArray;

void main() {
    vec4 textureColor = texture(textureArray, vec3(texCoord, floor(texIndex)));
    float d = textureColor.a - 0.4;
    float w = clamp(d / fwidth(d) + 0.7, 0.0, 1.0);

    if (w < 0.5) discard;

    fragColor = mix(vec4(0.0), vertexColor, w);
}