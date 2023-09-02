#version 150 core
#define EDGE_SOFTNESS 0.01
#define NO_TEXTURE 1000

in vec4 vertexColor;
in vec2 texStart;
in vec2 texSize;
in vec2 texCoord;
in vec2 texTiling;
in vec2 quadSize;
in float quadCornerRadius;
in float texIndex;
flat in uint samplerIndex;

out vec4 fragColor;

uniform sampler2DArray textureArrays[16];

void main() {
    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);

    if (texIndex != NO_TEXTURE)
    {
        vec2 sampleCoord = texStart + texSize * (texTiling == vec2(1.0) ? texCoord : fract(texCoord * texTiling));
        textureColor = texture(textureArrays[samplerIndex], vec3(sampleCoord, floor(texIndex)));
    }

    if (quadCornerRadius > 0.0)
    {
        vec2 pos = texCoord * quadSize;
        float border = clamp(quadCornerRadius, 0.0, 0.5 * min(quadSize.x, quadSize.y));
        vec2 corner = clamp(pos, vec2(border), quadSize - border);
        float distFromCorner = length(pos - corner) - border;
        textureColor.a *= 1.0f - smoothstep(0.0, EDGE_SOFTNESS, distFromCorner);
    }

    if (textureColor.a < 0.4)
        discard;

    fragColor = vertexColor * textureColor;
}