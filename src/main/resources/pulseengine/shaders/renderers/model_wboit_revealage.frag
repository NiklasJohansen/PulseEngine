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

uniform sampler2DArray textureArrays[16];

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

layout(std430, binding = 2) readonly buffer MaterialBuffer
{
    MaterialData uMaterials[];
};

vec4 sampleTexOrDefault(vec4 texDesc, vec3 defaultColor, vec2 tiling)
{
    int samplerIndex = int(texDesc.x);
    if (samplerIndex < 0) return vec4(defaultColor, 1.0);

    float layer = texDesc.y;
    vec2 uvMax = texDesc.zw;
    return texture(textureArrays[samplerIndex], vec3(fract(vTexCoord * tiling) * uvMax, layer));
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
    MaterialData material = uMaterials[vMaterialId];
    vec2 tiling = material.tilingAlphaFlags.xy;
    float alphaCutoff = material.tilingAlphaFlags.z;

    vec4 baseColor = material.baseColor * sampleTexOrDefault(material.albedoTex, vec3(1.0), tiling);
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