#version 150 core
#define EDGE_SOFTNESS 0.01
#define NO_TEXTURE 65534

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

// Dynamic indexing of sampler arrays are not supported in GLSL bellow version 4.0, thus this atrocity
vec4 sampleTextureArrayGrad(int index, vec3 texCoords, vec2 ddx, vec2 ddy)
{
    switch (index)
    {
        case 0:  return textureGrad(textureArrays[0],  texCoords, ddx, ddy);
        case 1:  return textureGrad(textureArrays[1],  texCoords, ddx, ddy);
        case 2:  return textureGrad(textureArrays[2],  texCoords, ddx, ddy);
        case 3:  return textureGrad(textureArrays[3],  texCoords, ddx, ddy);
        case 4:  return textureGrad(textureArrays[4],  texCoords, ddx, ddy);
        case 5:  return textureGrad(textureArrays[5],  texCoords, ddx, ddy);
        case 6:  return textureGrad(textureArrays[6],  texCoords, ddx, ddy);
        case 7:  return textureGrad(textureArrays[7],  texCoords, ddx, ddy);
        case 8:  return textureGrad(textureArrays[8],  texCoords, ddx, ddy);
        case 9:  return textureGrad(textureArrays[9],  texCoords, ddx, ddy);
        case 10: return textureGrad(textureArrays[10], texCoords, ddx, ddy);
        case 11: return textureGrad(textureArrays[11], texCoords, ddx, ddy);
        case 12: return textureGrad(textureArrays[12], texCoords, ddx, ddy);
        case 13: return textureGrad(textureArrays[13], texCoords, ddx, ddy);
        case 14: return textureGrad(textureArrays[14], texCoords, ddx, ddy);
        case 15: return textureGrad(textureArrays[15], texCoords, ddx, ddy);
        default: return vec4(0.0);
    }
}

void main()
{
    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);

    if (texIndex != NO_TEXTURE)
    {
        // Compute derivatives before tiling as tiled uv coordinates create discontinuities
        // at tile edges causing the wrong mip level to be selected
        vec2 coord = texCoord * texTiling;
        vec2 tiled = fract(coord);
        vec2 ddx = dFdx(coord) * texSize;
        vec2 ddy = dFdy(coord) * texSize;
        vec2 uv = texStart + texSize * tiled;
        textureColor = sampleTextureArrayGrad(int(samplerIndex), vec3(uv, floor(texIndex)), ddx, ddy);
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