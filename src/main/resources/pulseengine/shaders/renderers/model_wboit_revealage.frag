#version 430 core

in vec2 vTexCoord;
flat in int vMaterialId;
#ifdef WBOIT_OUTPUT_RENDER_ID
flat in uint vRenderId;
#endif

#ifdef WBOIT_OUTPUT_RENDER_ID
layout(location = 1) out float outRevealage;
layout(location = 2) out uint outRenderId;
#else
layout(location = 0) out float outRevealage;
#endif

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

uniform sampler2D uOpaqueDepthTex;
uniform bool      uUseOpaqueDepthTex;
uniform vec2      uOpaqueDepthTexSize;
uniform float     uWboitAlphaCutoff;

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

layout(std430, binding = 3) readonly buffer MaterialBuffer
{
    MaterialData uMaterials[];
};

vec4 sampleTexOrDefault(vec4 texDesc, vec3 defaultColor, vec2 tiling, vec2 texCoordDx, vec2 texCoordDy)
{
    int textureArraySlot = int(texDesc.x);
    if (textureArraySlot < 0) return vec4(defaultColor, 1.0);

    float layer = texDesc.y;
    vec2 uvMax = texDesc.zw;
    vec2 uv = fract(vTexCoord * tiling) * uvMax;
    vec2 uvDx = texCoordDx * tiling * uvMax;
    vec2 uvDy = texCoordDy * tiling * uvMax;
    return sampleTextureBankGrad(textureArraySlot, vec3(uv, layer), uvDx, uvDy);
}

bool isBehindOpaqueDepth()
{
    if (!uUseOpaqueDepthTex) return false;

    vec2 uv = gl_FragCoord.xy / uOpaqueDepthTexSize;
    float opaqueDepth = texture(uOpaqueDepthTex, uv).r;
    return gl_FragCoord.z > opaqueDepth + 0.000001;
}

void main()
{
    vec2 texCoordDx = dFdx(vTexCoord);
    vec2 texCoordDy = dFdy(vTexCoord);
    MaterialData material = uMaterials[vMaterialId];
    vec2 tiling = material.tilingAlphaFlags.xy;
    float alphaCutoff = material.tilingAlphaFlags.z;

    vec4 baseColor = material.baseColor * sampleTexOrDefault(material.albedoTex, vec3(1.0), tiling, texCoordDx, texCoordDy);
    float alpha = baseColor.a;

    if (alphaCutoff > 0.0)
    {
        float w = max(fwidth(alpha), 1.0 / 255.0);
        float coverage = smoothstep(alphaCutoff - w, alphaCutoff + w, alpha);
        if (coverage < 0.5) discard;
        alpha = coverage;
    }

    if (alpha <= uWboitAlphaCutoff || isBehindOpaqueDepth()) 
        discard;

    outRevealage = alpha;

    #ifdef WBOIT_OUTPUT_RENDER_ID
    outRenderId = vRenderId;
    #endif
}