#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform isampler2D uObjectIdTexture;
uniform vec2 uTextureSize;
uniform ivec2 uSelectedId;
uniform int uOutlineWidth;

bool isSelected(ivec2 value)
{
    return all(equal(value, uSelectedId));
}

void main()
{
    ivec2 size = ivec2(uTextureSize);
    ivec2 pixel = clamp(ivec2(uv * uTextureSize), ivec2(0), size - ivec2(1));
    bool centerSelected = isSelected(texelFetch(uObjectIdTexture, pixel, 0).rg);
    bool neighborSelected = false;
    bool neighborDifferent = false;

    for (int y = 0; y <= uOutlineWidth; y++)
    {
        for (int x = 0; x <= uOutlineWidth; x++)
        {
            if (x == 0 && y == 0) continue;
            ivec2 samplePixel = clamp(pixel + ivec2(x, y), ivec2(0), size - ivec2(1));
            bool selected = isSelected(texelFetch(uObjectIdTexture, samplePixel, 0).rg);
            neighborSelected = neighborSelected || selected;
            neighborDifferent = neighborDifferent || !selected;
        }
    }
    
    bool edge = (centerSelected && neighborDifferent) || (!centerSelected && neighborSelected);
    fragColor = edge ? vec4(1.0, 1.0, 1.0, 0.8) : (centerSelected ? vec4(1.0, 1.0, 1.0, 0.01) : vec4(0.0));
}