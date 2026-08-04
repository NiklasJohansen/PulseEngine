#version 430 core

in vec2 uv;
out vec4 fragColor;

uniform isampler2D uObjectIdTexture;
uniform vec2 uTextureSize;
uniform int uOutlineWidth;
uniform int uSelectionWordCount;

layout(std430, binding = 13) readonly buffer SelectionBuffer
{
    uint uSelectionWords[];
};

bool isSelected(ivec2 value)
{
    // The object IDs are dense, non-negative values. Negative or high-word IDs include
    // the object ID surface background and object IDs outside the dense selection bitset.
    if (value.x < 0 || value.y != 0)
        return false;

    uint objectId = uint(value.x);
    uint wordIndex = objectId >> 5u;
    if (wordIndex >= uint(uSelectionWordCount))
        return false;

    return (uSelectionWords[wordIndex] & (1u << (objectId & 31u))) != 0u;
}

void main()
{
    ivec2 size = ivec2(uTextureSize);
    ivec2 pixel = clamp(ivec2(uv * uTextureSize), ivec2(0), size - ivec2(1));
    bool centerSelected = isSelected(texelFetch(uObjectIdTexture, pixel, 0).rg);
    bool edge = false;

    for (int y = -uOutlineWidth; y <= uOutlineWidth && !edge; y++)
    {
        for (int x = -uOutlineWidth; x <= uOutlineWidth; x++)
        {
            if (x == 0 && y == 0) continue;
            ivec2 samplePixel = clamp(pixel + ivec2(x, y), ivec2(0), size - ivec2(1));
            if (isSelected(texelFetch(uObjectIdTexture, samplePixel, 0).rg) != centerSelected)
            {
                edge = true;
                break;
            }
        }
    }

    fragColor = edge ? vec4(0.8, 0.55, 0.08, 1.0) : (centerSelected ? vec4(1.0, 1.0, 1.0, 0.01) : vec4(0.0));
}