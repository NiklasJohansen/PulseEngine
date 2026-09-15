#version 430 core

in vec2 uv;
out vec4 fragColor;

uniform usampler2D uRenderIdTexture;
uniform vec2 uTextureSize;
uniform int uOutlineWidth;
uniform int uSelectionWordCount;
uniform float uTime;

layout(std430, binding = 0) readonly buffer SelectionBuffer
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

    for (int y = -uOutlineWidth; y <= uOutlineWidth && !edge; y++)
    {
        for (int x = -uOutlineWidth; x <= uOutlineWidth; x++)
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

    float diagonal = uv.x * (uTextureSize.x / uTextureSize.y) - uv.y;
    float alpha = 0.1 + 0.9 * sin(80 * 6.28318 * (diagonal - mod(uTime * 0.05, 1.0)));
    vec3 color = vec3(0.8, 0.55, 0.08);

    fragColor = edge ? vec4(color, alpha) : (centerSelected ? vec4(color, 0.025 * (0.5 + 0.5 * alpha)) : vec4(0.0));
}