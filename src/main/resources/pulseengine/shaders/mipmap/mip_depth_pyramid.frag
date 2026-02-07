#version 330 core

uniform sampler2D tex;

uniform int prevMipLevel;
uniform ivec2 prevTexSize; // size of previous mip level

void main()
{
    // Current mip pixel coordinate (integer)
    ivec2 p = ivec2(gl_FragCoord.xy);

    // Corresponding bottom-left pixel in previous mip (2x2 block)
    ivec2 base = p * 2;

    // Clamp for odd sizes (last texel may map outside)
    ivec2 maxCoord = prevTexSize - ivec2(1);

    float d00 = texelFetch(tex, clamp(base + ivec2(0,0), ivec2(0), maxCoord), prevMipLevel).r;
    float d10 = texelFetch(tex, clamp(base + ivec2(1,0), ivec2(0), maxCoord), prevMipLevel).r;
    float d01 = texelFetch(tex, clamp(base + ivec2(0,1), ivec2(0), maxCoord), prevMipLevel).r;
    float d11 = texelFetch(tex, clamp(base + ivec2(1,1), ivec2(0), maxCoord), prevMipLevel).r;

    gl_FragDepth = min(min(d00, d10), min(d01, d11));
}