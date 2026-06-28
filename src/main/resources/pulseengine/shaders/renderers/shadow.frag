#version 430 core

in vec2 vTexCoord;
flat in int vMaterialId;

uniform sampler2DArray textureArrays[16];

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

vec4 sampleTexOrDefault(vec4 texDesc, vec2 tiling)
{
    int samplerIndex = int(texDesc.x);
    if (samplerIndex < 0)
        return vec4(1.0);

    return texture(textureArrays[samplerIndex], vec3(fract(vTexCoord * tiling) * texDesc.zw, texDesc.y));
}

void main()
{
    MaterialData material = uMaterials[vMaterialId];
    float alphaCutoff = material.tilingAlphaFlags.z;
    float alpha = material.baseColor.a * sampleTexOrDefault(material.albedoTex, material.tilingAlphaFlags.xy).a;

    if (alphaCutoff > 0.0 && alpha < alphaCutoff)
        discard;
}
