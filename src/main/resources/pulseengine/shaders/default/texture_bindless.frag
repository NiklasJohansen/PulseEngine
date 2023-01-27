#version 150 core
#define EDGE_SOFTNESS 0.01

in vec4 vertexColor;
in vec2 texStart;
in vec2 texSize;
in vec2 texCoord;
in vec2 texTiling;
in float texIndex;
in vec2 quadSize;
in float quadCornerRadius;

out vec4 fragColor;

// 19:00 and 1:08:43 https://gdcvault.com/play/1020791/
uniform sampler2DArray textureArray; // TODO: Can be an array of sampler2DArray, to have support for multiple texture sizes and sampling types

void main() {
    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);

    if (texIndex >= 0)
    {
        vec2 sampleCoord = texStart + texSize * (texTiling == vec2(1.0) ? texCoord : fract(texCoord * texTiling));
        textureColor = texture(textureArray, vec3(sampleCoord, floor(texIndex)));
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