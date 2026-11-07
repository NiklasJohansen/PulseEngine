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

struct TextureRef
{
    float arraySlot;
    float layer;
    vec2  uvMax;
};

struct MaterialData
{
    TextureRef albedoTex;
    TextureRef normalTex;
    TextureRef aoRoughMetalTex;
    TextureRef emissiveTex;
    TextureRef heightTex;

    vec4  baseColor;
    vec4  emissiveFactor;
    float aoStrength;
    float roughnessFactor;
    float metallicFactor;
    float heightScale;
    vec2  normalDir;
    float normalScale;
    float alphaCutoff;
    vec2  tiling;
    uint  layerMask;
    float isAlphaBlended;
};

layout(std430, binding = 3) readonly buffer MaterialBuffer
{
    MaterialData uMaterials[];
};

vec4 sampleTexOrDefault(TextureRef tex, vec3 defaultColor, vec2 tiling, vec2 texCoordDx, vec2 texCoordDy)
{
    int textureArraySlot = int(tex.arraySlot);
    if (textureArraySlot < 0) return vec4(defaultColor, 1.0);

    vec2 uv = fract(vTexCoord * tiling) * tex.uvMax;
    vec2 uvDx = texCoordDx * tiling * tex.uvMax;
    vec2 uvDy = texCoordDy * tiling * tex.uvMax;
    return sampleTextureBankGrad(textureArraySlot, vec3(uv, tex.layer), uvDx, uvDy);
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
    float alphaCutoff = material.alphaCutoff;

    vec4 baseColor = material.baseColor * sampleTexOrDefault(material.albedoTex, vec3(1.0), material.tiling, texCoordDx, texCoordDy);
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