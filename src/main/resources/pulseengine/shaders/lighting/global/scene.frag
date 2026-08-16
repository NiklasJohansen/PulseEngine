#version 150 core

#define NO_TEXTURE 65534

in vec4 vertexColor;
in vec2 quadSize;
in float quadCornerRadius;
in float sourceIntensity;
in float sourceAngle;
in float sourceConeAngle;
in float sourceRadius; // light radius / occluder edge light
in vec2 texStart;
in vec2 texSize;
in vec2 texCoord;
in vec2 texTiling;
in float texIndex;
flat in uint textureArraySlot;

out vec4 sceneColor;
out vec4 metadata;

uniform sampler2DArray uTextureBanks[16];

// Use fixed texture-array slots as some OpenGL drivers reject dynamic indexing of sampler arrays.
vec4 sampleTextureBankGrad(int textureArraySlot, vec3 texCoords, vec2 ddx, vec2 ddy)
{
    switch (textureArraySlot)
    {
        case 0:  return textureGrad(uTextureBanks[0],  texCoords, ddx, ddy);
        case 1:  return textureGrad(uTextureBanks[1],  texCoords, ddx, ddy);
        case 2:  return textureGrad(uTextureBanks[2],  texCoords, ddx, ddy);
        case 3:  return textureGrad(uTextureBanks[3],  texCoords, ddx, ddy);
        case 4:  return textureGrad(uTextureBanks[4],  texCoords, ddx, ddy);
        case 5:  return textureGrad(uTextureBanks[5],  texCoords, ddx, ddy);
        case 6:  return textureGrad(uTextureBanks[6],  texCoords, ddx, ddy);
        case 7:  return textureGrad(uTextureBanks[7],  texCoords, ddx, ddy);
        case 8:  return textureGrad(uTextureBanks[8],  texCoords, ddx, ddy);
        case 9:  return textureGrad(uTextureBanks[9],  texCoords, ddx, ddy);
        case 10: return textureGrad(uTextureBanks[10], texCoords, ddx, ddy);
        case 11: return textureGrad(uTextureBanks[11], texCoords, ddx, ddy);
        case 12: return textureGrad(uTextureBanks[12], texCoords, ddx, ddy);
        case 13: return textureGrad(uTextureBanks[13], texCoords, ddx, ddy);
        case 14: return textureGrad(uTextureBanks[14], texCoords, ddx, ddy);
        case 15: return textureGrad(uTextureBanks[15], texCoords, ddx, ddy);
        default: break;
    }
    return vec4(0.0);
}

void main()
{
    if (quadCornerRadius > 0.0)
    {
        vec2 pos = texCoord * quadSize;
        float border = clamp(quadCornerRadius, 0.0, 0.5 * min(quadSize.x, quadSize.y));
        vec2 corner = clamp(pos, vec2(border), quadSize - border);
        float distFromCorner = length(pos - corner) - border;
        if (distFromCorner > 0.0)
            discard;
    }

    vec4 texColor = vec4(1.0, 1.0, 1.0, 1.0);
    if (texIndex != NO_TEXTURE)
    {
        // Compute derivatives before tiling as tiled uv coordinates create discontinuities
        // at tile edges causing the wrong mip level to be selected
        vec2 coord = texCoord * texTiling;
        vec2 ddx = dFdx(coord) * texSize;
        vec2 ddy = dFdy(coord) * texSize;
        vec2 uv = texStart + texSize * fract(coord);

        texColor = sampleTextureBankGrad(int(textureArraySlot), vec3(uv, floor(texIndex)), ddx, ddy);

        if (texColor.a < 0.5)
            discard; // Discard transparent pixels
    }

    sceneColor = vertexColor * texColor;
    metadata = vec4(sourceConeAngle / 360.0, fract(sourceAngle / 360.0), sourceIntensity, sourceRadius);
}