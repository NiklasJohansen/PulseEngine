#version 430 core

in vec2 uv;
out vec4 fragColor;

uniform usampler2D uRenderIdTexture;
uniform vec2 uTextureSize;
uniform int uOutlineWidth;
uniform int uSelectionWordCount;

layout(std430, binding = 13) readonly buffer SelectionBuffer
{
    uint uSelectionWords[];
};

bool isSelected(uint renderId)
{
    if (renderId == 0x7f800000u) // Invalid ID / NaN
        return false;

    uint wordIndex = renderId >> 5u;
    if (wordIndex >= uint(uSelectionWordCount))
        return false;

    return (uSelectionWords[wordIndex] & (1u << (renderId & 31u))) != 0u;
}

void main()
{
    ivec2 size = ivec2(uTextureSize);
    ivec2 pixel = clamp(ivec2(uv * uTextureSize), ivec2(0), size - ivec2(1));
    bool centerSelected = isSelected(texelFetch(uRenderIdTexture, pixel, 0).r);
    bool edge = false;

    for (int y = 0; y <= uOutlineWidth && !edge; y++)
    {
        for (int x = 0; x <= uOutlineWidth; x++)
        {
            if (x == 0 && y == 0) continue;
            ivec2 samplePixel = clamp(pixel + ivec2(x, y), ivec2(0), size - ivec2(1));
            if (isSelected(texelFetch(uRenderIdTexture, samplePixel, 0).r) != centerSelected)
            {
                edge = true;
                break;
            }
        }
    }

    fragColor = edge ? vec4(0.8, 0.55, 0.08, 1.0) : (centerSelected ? vec4(1.0, 1.0, 1.0, 0.01) : vec4(0.0));
}