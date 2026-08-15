#version 430 core

in vec2 vTexCoord;
flat in int vMaterialId;

uniform sampler2DArray uTextureBanks[16];

// Use fixed sampler indices as some OpenGL drivers reject dynamic indexing of sampler arrays.
vec4 sampleTextureBankGrad(int index, vec3 texCoords, vec2 ddx, vec2 ddy)
{
    switch (index)
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

struct MaterialData
{
    vec4 baseColor;
    vec4 emissiveFactor;
    vec4 albedoTex;
    vec4 normalTex;
    vec4 aoMetalRoughTex;
    vec4 emissiveTex;
    vec4 aoMetalRoughNormalFactor;
    vec4 tilingAlphaFlags; // x/y=tiling, z=alphaCutoff, w=flags
};

layout(std430, binding = 2) readonly buffer MaterialBuffer
{
    MaterialData uMaterials[];
};

vec4 sampleTexOrDefault(vec4 texDesc, vec2 tiling, vec2 texCoordDx, vec2 texCoordDy)
{
    int samplerIndex = int(texDesc.x);
    if (samplerIndex < 0)
        return vec4(1.0);

    vec2 uvMax = texDesc.zw;
    vec2 uv = fract(vTexCoord * tiling) * uvMax;
    vec2 uvDx = texCoordDx * tiling * uvMax;
    vec2 uvDy = texCoordDy * tiling * uvMax;
    return sampleTextureBankGrad(samplerIndex, vec3(uv, texDesc.y), uvDx, uvDy);
}

void main()
{
    vec2 texCoordDx = dFdx(vTexCoord);
    vec2 texCoordDy = dFdy(vTexCoord);
    MaterialData material = uMaterials[vMaterialId];
    float alphaCutoff = material.tilingAlphaFlags.z;
    float alpha = material.baseColor.a * sampleTexOrDefault(material.albedoTex, material.tilingAlphaFlags.xy, texCoordDx, texCoordDy).a;

    if (alphaCutoff > 0.0 && alpha < alphaCutoff)
        discard;
}