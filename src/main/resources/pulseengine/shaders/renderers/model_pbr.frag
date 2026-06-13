#version 430 core

#define CASCADE_COUNT 4

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in mat3 vTBN;
in vec2 vTexCoord;
flat in int vMaterialId;

#ifdef PBR_OUTPUT_WBOIT_ACCUM
layout(location = 0) out vec4 outAccum;
#else
layout(location = 0) out vec4 fragColor;
#endif

// Textures
uniform sampler2DArray textureArrays[16]; // TODO: Prefix with u
uniform sampler2D uGtaoTex;

// Environment
uniform vec4  uEnvDiffuseTex;
uniform vec4  uEnvSpecularTex;
uniform vec4  uEnvBrdfLutTex;
uniform float uAoIntensity;

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

const int MATERIAL_FLAG_FLIP_NORMALS = 1;

// Lighting
uniform float uEnvIntensity;
uniform float uEnvSpecularMipCount;
uniform vec3  uCameraPos;
uniform vec2  uScreenSize;
uniform mat4  uView;

#ifdef PBR_OUTPUT_WBOIT_ACCUM
uniform sampler2D uOpaqueDepthTex;
uniform bool      uUseOpaqueDepthTex;
uniform vec2      uOpaqueDepthTexSize;
uniform float     uWboitAlphaCutoff;
#endif

uniform vec4  uSunColor;
uniform vec3  uSunDirection;
uniform float uSunRadius;

// Clustered local lighting
struct LocalLightData
{
    vec4 positionRadius;
    vec4 colorDirectionX;
    vec4 directionYZOuterInnerCos;
    vec4 isSpotShadowInfo;  // x=isSpotLight, y=ShadowBias, z=firstFace, w=faceCount,
};

struct LocalShadowFaceData
{
    vec4 atlasScaleBias;        // xy=scale, zw=bias
    mat4 shadowViewProjection;
};

layout(std430, binding = 13) readonly buffer LocalLightBuffer
{
    LocalLightData uLocalLights[];
};

layout(std430, binding = 14) readonly buffer ClusterBuffer
{
    uvec2 uClusterRanges[]; // x=offset into uClusterLightIndices, y=count
};

layout(std430, binding = 15) readonly buffer ClusterLightIndexBuffer
{
    uint uClusterLightIndices[];
};

layout(std430, binding = 16) readonly buffer LocalShadowFaceBuffer
{
    LocalShadowFaceData uLocalShadowFaces[];
};

uniform bool  uClusteredLightingEnabled;
uniform ivec3 uClusterGridSize;
uniform vec2  uClusterTileSize;
uniform float uClusterNearPlane;
uniform float uClusterDepthSliceScale;
uniform int   uClusterCount;
uniform int   uClusterLightCount;
uniform int   uLocalShadowFaceCount;

// Cascaded shadow mapping
uniform sampler2DShadow uShadowMapTex;
uniform float           uShadowMapTexSize;
uniform mat4            uShadowViewProjections[CASCADE_COUNT];
uniform vec4            uShadowCascadeSplitDistances; // Far split distance for each cascade (view-space depth)
uniform vec4            uShadowCascadeSizeMeters;     // World-space size of each cascade in meters

// Local shadow atlas
uniform sampler2DShadow uLocalShadowAtlasTex;
uniform float           uLocalShadowAtlasTexSize;

// Atlas offsets for 2x2 layout: cascade 0=bottom-left, 1=bottom-right, 2=top-left, 3=top-right
const vec2 CASCADE_OFFSETS[CASCADE_COUNT] = vec2[](vec2(0.0, 0.0), vec2(0.5, 0.0), vec2(0.0, 0.5), vec2(0.5, 0.5));

// ------------------------------------------------------------------
// Texture sampling
// ------------------------------------------------------------------

vec4 sampleTexOrDefault(vec4 texDesc, vec3 defaultColor, vec2 tiling)
{
    int samplerIndex = int(texDesc.x);
    if (samplerIndex < 0) 
        return vec4(defaultColor, 1.0);

    float layer = texDesc.y;
    vec2 uvMax = texDesc.zw;
    return texture(textureArrays[samplerIndex], vec3(fract(vTexCoord * tiling) * uvMax, layer));
}

vec3 sampleWorldSpaceNormal(MaterialData material, out float normalLenTS)
{
    vec2 tiling = material.tilingAlphaFlags.xy;
    int flags = int(material.tilingAlphaFlags.w);

    // Tangent-space normal
    vec3 normalTs = sampleTexOrDefault(material.normalTex, vec3(0.5, 0.5, 1.0), tiling).rgb * 2.0 - 1.0;

    if ((flags & MATERIAL_FLAG_FLIP_NORMALS) != 0)
        normalTs.y *= -1;

    normalTs.xy *= material.aoMetalRoughNormalFactor.w; // Normal scale

    float len = max(length(normalTs), 1e-5);
    normalLenTS = min(len, 1.0);

    // To world space
    vec3 N = normalize(vTBN * (normalTs / len));

    // Flip back-face normals
    if (!gl_FrontFacing) N = -N;

    return N;
}

vec3 sampleEnvMap(vec4 texDesc, vec3 dir, float lod)
{
    int samplerIndex = int(texDesc.x);
    if (samplerIndex < 0) return vec3(0.0);

    // Convert direction to lat-long UV
    dir = normalize(dir);
    float phi = atan(dir.z, dir.x);
    float theta = acos(clamp(dir.y, -1.0, 1.0));
    float u = phi / (2.0 * PI) + 0.5;
    float v = theta / PI;

    float layer = texDesc.y;
    vec2 uvMax = texDesc.zw;
    vec2 uv = vec2(u, v) * uvMax;
    return textureLod(textureArrays[samplerIndex], vec3(uv, layer), lod).rgb;
}

vec2 sampleBrdfLut(float NdotV, float roughness)
{
    int samplerIndex = int(uEnvBrdfLutTex.x);
    if (samplerIndex < 0) return vec2(0.0);

    float layer = uEnvBrdfLutTex.y;
    vec2 uvMax = uEnvBrdfLutTex.zw;
    vec2 uv = vec2(NdotV, roughness) * uvMax;

    return texture(textureArrays[int(samplerIndex)], vec3(uv, layer)).rg;
}

// ------------------------------------------------------------------
// PBR functions
// ------------------------------------------------------------------

float distributionGGX(vec3 N, vec3 H, float roughness)
{
    float a  = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;
    return a2 / max(denom, 0.000001);
}

float geometrySchlickGGX(float NdotV, float roughness)
{
    float r = roughness + 1.0;
    float k = (r * r) / 8.0; // Direct lighting version
    float denom = NdotV * (1.0 - k) + k;
    return NdotV / max(denom, 0.000001);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
{
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx1 = geometrySchlickGGX(NdotV, roughness);
    float ggx2 = geometrySchlickGGX(NdotL, roughness);
    return ggx1 * ggx2;
}

vec3 fresnelSchlick(float cosTheta, vec3 F0)
{
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

vec3 fresnelSchlickRoughness(float cosTheta, vec3 F0, float roughness)
{
    return F0 + (max(vec3(1.0 - roughness), F0) - F0) * pow(1.0 - cosTheta, 5.0);
}

float specularOcclusion(float NdotV, float ao, float roughness)
{
    float exponent = exp2(-16.0 * roughness - 1.0);
    return clamp(pow(NdotV + ao, exponent) - 1.0 + ao, 0.0, 1.0);
}

// ------------------------------------------------------------------
// Shadow mapping
// ------------------------------------------------------------------

const vec2 POISSON_DISK_16[16] = vec2[]
(
    vec2(-0.94201624, -0.39906216),
    vec2( 0.94558609, -0.76890725),
    vec2(-0.09418410, -0.92938870),
    vec2( 0.34495938,  0.29387760),
    vec2(-0.91588581,  0.45771432),
    vec2(-0.81544232, -0.87912464),
    vec2(-0.38277543,  0.27676845),
    vec2( 0.97484398,  0.75648379),
    vec2( 0.44323325, -0.97511554),
    vec2( 0.53742981, -0.47373420),
    vec2(-0.26496911, -0.41893023),
    vec2( 0.79197514,  0.19090188),
    vec2(-0.24188840,  0.99706507),
    vec2(-0.81409955,  0.91437590),
    vec2( 0.19984126,  0.78641367),
    vec2( 0.14383161, -0.14100790)
);

float pcssShadowCascade(vec3 shadowWorldPos, vec3 N, float lightRadius, int cascade)
{
    vec2 atlasOffset = CASCADE_OFFSETS[cascade];
    float cascadeSizeMeters = uShadowCascadeSizeMeters[cascade];

    // Project the normal-offset world position into cascade's light space
    vec4 lightPos = uShadowViewProjections[cascade] * vec4(shadowWorldPos, 1.0);
    vec3 pos = (lightPos.xyz / lightPos.w) * 0.5 + 0.5; // NDC [-1,1] -> [0,1]

    if (pos.x < 0.0 || pos.x > 1.0 || pos.y < 0.0 || pos.y > 1.0 || pos.z < 0.0 || pos.z > 1.0)
        return 1.0; // Outside this cascade, fully lit

    // Remap UV to atlas quadrant: [0,1] -> [offset, offset+0.5]
    vec2 atlasUv = pos.xy * 0.5 + atlasOffset;

    // Stable per-texel rotation
    vec2 texelCoord = floor(atlasUv * uShadowMapTexSize);
    float angle = TAU * fract(sin(dot(texelCoord, vec2(127.1, 311.7))) * 43758.5453123);
    float s = sin(angle), c = cos(angle);
    mat2 R = mat2(c, -s, s, c);

    // Half-res texel size in atlas UV space
    float halfRes = uShadowMapTexSize * 0.5;
    float texelUv = 1.0 / halfRes;
    float filterRadiusUv = lightRadius * texelUv;

    // Clamp bounds with half-texel inset to prevent bleeding across quadrant edges
    vec2 clampMin = atlasOffset + vec2(texelUv * 0.5);
    vec2 clampMax = atlasOffset + vec2(0.5) - vec2(texelUv * 0.5);

    float sum = 0.0;
    for (int i = 0; i < 16; i++)
    {
        vec2 o = R * POISSON_DISK_16[i] * filterRadiusUv;
        vec2 sampleUv = clamp(atlasUv + o, clampMin, clampMax);
        sum += texture(uShadowMapTex, vec3(sampleUv, pos.z));
    }

    return sum / 16.0;
}

vec3 shadowWorldPos(vec3 worldPos, vec3 N, int cascade)
{
    // Scale the normal offset by the cascade's texel size so that larger cascades
    // (which cover more world-space area) get a proportionally larger offset.
    float halfRes = uShadowMapTexSize * 0.5;
    float texelSize = uShadowCascadeSizeMeters[cascade] / halfRes;
    return worldPos + N * texelSize;
}

float cascadedShadow(vec3 worldPos, vec3 N, float lightRadius)
{
    // Compute view-space depth for cascade selection
    float viewDepth = -(uView * vec4(worldPos, 1.0)).z;

    // Find the first cascade that contains this fragment
    int cascade = CASCADE_COUNT - 1;
    for (int i = 0; i < CASCADE_COUNT; i++)
    {
        if (viewDepth < uShadowCascadeSplitDistances[i])
        {
            cascade = i;
            break;
        }
    }

    vec3 swp = shadowWorldPos(worldPos, N, cascade);
    float shadow = pcssShadowCascade(swp, N, lightRadius, cascade);

    // Blend between cascades at the transition boundary to hide seams
    float splitDist = uShadowCascadeSplitDistances[cascade];
    float prevSplitDist = (cascade > 0) ? uShadowCascadeSplitDistances[cascade - 1] : 0.0;
    float cascadeRange = splitDist - prevSplitDist;
    float blendZone = cascadeRange * 0.15; // 15% transition band
    float distToEdge = splitDist - viewDepth;

    if (distToEdge < blendZone && cascade < CASCADE_COUNT - 1)
    {
        vec3 nextSwp = shadowWorldPos(worldPos, N, cascade + 1);
        float nextShadow = pcssShadowCascade(nextSwp, N, lightRadius, cascade + 1);
        float t = smoothstep(0.0, blendZone, distToEdge);
        shadow = mix(nextShadow, shadow, t);
    }

    return shadow;
}

// ------------------------------------------------------------------
// Local lights
// ------------------------------------------------------------------ 

int clusterIndexForFragment(float viewDepth)
{
    ivec2 tile = ivec2(floor(gl_FragCoord.xy / uClusterTileSize));
    if (tile.x < 0 || tile.y < 0 || tile.x >= uClusterGridSize.x || tile.y >= uClusterGridSize.y)
        return -1;

    float z = log(max(viewDepth, uClusterNearPlane) / uClusterNearPlane) * uClusterDepthSliceScale;
    int zSlice = int(clamp(floor(z), 0.0, float(uClusterGridSize.z - 1)));
    int clusterIndex = (zSlice * uClusterGridSize.y + tile.y) * uClusterGridSize.x + tile.x;
    return (clusterIndex >= 0 && clusterIndex < uClusterCount) ? clusterIndex : -1;
}

int pointShadowFaceIndex(vec3 fromLight)
{
    vec3 a = abs(fromLight);
    if (a.x >= a.y && a.x >= a.z)
        return fromLight.x >= 0.0 ? 0 : 1;
    if (a.y >= a.x && a.y >= a.z)
        return fromLight.y >= 0.0 ? 2 : 3;
    return fromLight.z >= 0.0 ? 4 : 5;
}

float localShadowFace(int faceIndex, vec3 shadowWorldPos)
{
    if (faceIndex < 0 || faceIndex >= uLocalShadowFaceCount)
        return 1.0;

    LocalShadowFaceData face = uLocalShadowFaces[faceIndex];
    vec4 lightPos = face.shadowViewProjection * vec4(shadowWorldPos, 1.0);
    vec3 pos = (lightPos.xyz / lightPos.w) * 0.5 + 0.5;

    if (pos.x < 0.0 || pos.x > 1.0 || pos.y < 0.0 || pos.y > 1.0 || pos.z < 0.0 || pos.z > 1.0)
        return 1.0;

    vec4 atlas = face.atlasScaleBias;
    vec2 atlasUv = pos.xy * atlas.xy + atlas.zw;
    float texelUv = 1.0 / max(uLocalShadowAtlasTexSize, 1.0);

    vec2 clampMin = atlas.zw + vec2(texelUv * 0.5);
    vec2 clampMax = atlas.zw + atlas.xy - vec2(texelUv * 0.5);

    float sum = 0.0;
    for (int y = -1; y <= 1; y++)
    {
        for (int x = -1; x <= 1; x++)
        {
            vec2 offset = vec2(x, y) * texelUv;
            vec2 sampleUv = clamp(atlasUv + offset, clampMin, clampMax);
            sum += texture(uLocalShadowAtlasTex, vec3(sampleUv, pos.z));
        }
    }

    return sum / 9.0;
}

float localLightShadow(LocalLightData light, vec3 lightPos, vec3 worldPos, vec3 N)
{
    float shadowBias = light.isSpotShadowInfo.y;
    if (shadowBias < 0.0)
        return 1.0; //  Not casting shadows

    vec3 shadowWorldPos = worldPos + N * shadowBias;
    int firstFace = int(light.isSpotShadowInfo.z + 0.5);
    int faceCount = int(light.isSpotShadowInfo.w + 0.5);

    if (firstFace < 0 || faceCount <= 0)
        return 1.0;

    int faceIndex = firstFace;
    if (light.isSpotShadowInfo.x < 0.5 && faceCount >= 6)
        faceIndex += pointShadowFaceIndex(worldPos - lightPos);

    return localShadowFace(faceIndex, shadowWorldPos);
}

vec3 accumulateLocalLights(vec3 N, vec3 V, float NdotV, vec3 baseColor, float metallic, float roughness, vec3 F0)
{
    vec3 Lo = vec3(0.0);

    if (!uClusteredLightingEnabled)
        return Lo;

    float viewDepth = -(uView * vec4(vWorldPos, 1.0)).z;
    int clusterIndex = clusterIndexForFragment(viewDepth);
    if (clusterIndex < 0)
        return Lo;

    uvec2 range = uClusterRanges[clusterIndex];

    for (uint clusterLight = 0u; clusterLight < range.y; clusterLight++)
    {
        uint lightIndex = uClusterLightIndices[range.x + clusterLight];
        if (lightIndex >= uint(uClusterLightCount))
            continue;

        LocalLightData light = uLocalLights[int(lightIndex)];
        vec3  lightPos   = light.positionRadius.xyz;
        float radius     = light.positionRadius.w;
        vec3  lightColor = light.colorDirectionX.rgb;
        vec3  lightDir   = vec3(light.colorDirectionX.w, light.directionYZOuterInnerCos.xy);
        float outerCos   = light.directionYZOuterInnerCos.z;
        float innerCos   = light.directionYZOuterInnerCos.w;
        float isSpot     = light.isSpotShadowInfo.x;

        vec3 toL = lightPos - vWorldPos;
        float d2 = dot(toL, toL);
        float d  = sqrt(max(d2, 1e-6));

        if (d >= radius) continue;

        vec3 L = toL / d;
        float NdotL = max(dot(N, L), 0.0);
        if (NdotL <= 0.0) continue;

        // Distance attenuation
        float x = d / max(radius, 1e-6);
        float smoothCutoff = clamp(1.0 - x*x*x*x, 0.0, 1.0);
        smoothCutoff *= smoothCutoff;
        float invD2 = 1.0 / max(d2, 1e-4);
        float attenuation = invD2 * smoothCutoff;

        // Spot light cone attenuation
        if (isSpot > 0.5)
        {
            float theta = dot(-L, normalize(lightDir));
            float spotAtten = clamp((theta - outerCos) / max(innerCos - outerCos, 1e-4), 0.0, 1.0);
            attenuation *= spotAtten * spotAtten; // Squared for smoother falloff
        }

        vec3 radiance = lightColor * attenuation;
        float shadow = localLightShadow(light, lightPos, vWorldPos, N);

        // Cook-Torrance BRDF
        vec3 H  = normalize(V + L);
        vec3 F  = fresnelSchlick(max(dot(H, V), 0.0), F0);
        vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);

        float G     = geometrySmith(N, V, L, roughness);
        float NDF   = distributionGGX(N, H, roughness);
        float denom = 4.0 * NdotV * NdotL + 0.000001;

        vec3 diffuse  = kD * baseColor / PI;
        vec3 specular = (NDF * G * F) / denom;

        Lo += (diffuse + specular) * radiance * NdotL * shadow;
    }

    return Lo;
}

// ------------------------------------------------------------------
// Weighted blended OIT
// ------------------------------------------------------------------

#ifdef PBR_OUTPUT_WBOIT_ACCUM
float computeWboitWeight(float alpha)
{
    float alphaWeight = pow(min(1.0, alpha * 10.0) + 0.01, 3.0);
    float depthWeight = pow(1.0 - gl_FragCoord.z * 0.9, 3.0);
    return clamp(alphaWeight * 100000000.0 * depthWeight, 0.01, 3000.0);
}

bool isBehindOpaqueDepth()
{
    if (!uUseOpaqueDepthTex) return false;

    vec2 uv = gl_FragCoord.xy / uOpaqueDepthTexSize;
    float opaqueDepth = texture(uOpaqueDepthTex, uv).r;
    return gl_FragCoord.z > opaqueDepth + 0.000001;
}
#endif

// ------------------------------------------------------------------
// Main
// ------------------------------------------------------------------

void main()
{
    MaterialData material = uMaterials[vMaterialId];
    vec2 tiling = material.tilingAlphaFlags.xy;
    float alphaCutoff = material.tilingAlphaFlags.z;

    vec4 baseColor = material.baseColor * sampleTexOrDefault(material.albedoTex, vec3(1.0), tiling);
    float alpha = baseColor.a;

    if (alphaCutoff > 0.0)
    {
        // Coverage AA around the cutoff
        float w = max(fwidth(alpha), 1.0 / 255.0);
        float coverage = smoothstep(alphaCutoff - w, alphaCutoff + w, alpha);
        if (coverage < 0.5) discard; // Early-out
        alpha = coverage;
    }

    // Check wighted blend alpha cutoff and opaque depth before doing expensive PBR calculations
    #ifdef PBR_OUTPUT_WBOIT_ACCUM
    if (alpha <= uWboitAlphaCutoff || isBehindOpaqueDepth()) discard;
    #endif

    // PBR material properties
    vec3 emissive   = sampleTexOrDefault(material.emissiveTex, vec3(1.0), tiling).rgb * material.emissiveFactor.rgb;
    vec3 aomr       = sampleTexOrDefault(material.aoMetalRoughTex, vec3(1.0, 1.0, 0.0), tiling).rgb; // Default AO=1, rough=1, metal=0
    float ao        = clamp(mix(1.0, aomr.r, material.aoMetalRoughNormalFactor.x), 0.0,  1.0);
    float roughness = clamp(aomr.g * material.aoMetalRoughNormalFactor.y, 0.04, 1.0);
    float metallic  = clamp(aomr.b * material.aoMetalRoughNormalFactor.z, 0.0,  1.0);

    // Normal + View
    float normalLength;
    vec3 N = sampleWorldSpaceNormal(material, normalLength);
    vec3 V = normalize(uCameraPos - vWorldPos);

    // Roughness adjustment (Toksvig + screen-space normal variation) 
    float len = clamp(normalLength, 1e-5, 1.0);
    float sigma2 = (1.0 - len) / len;
    float r2 = roughness * roughness;
    r2 = clamp(r2 + 0.5 * sigma2, 0.0, 1.0);
    vec3 fw = fwidth(N);
    float variance = dot(fw, fw);
    float k = 0.5;
    r2 = clamp(r2 + k * variance, 0.0, 1.0);
    roughness = sqrt(r2);

    // Ambient occlusion
    vec2 uvScreen = gl_FragCoord.xy / uScreenSize;
    float gtao = texture(uGtaoTex, uvScreen).r;
    gtao = clamp(exp(-uAoIntensity * (1.0 - gtao)), 0.0, 1.0);
    float aoCombined = gtao * ao;

    // Single directional light
    vec3 L = normalize(-uSunDirection);

    vec3 H = normalize(V + L);
    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.0001);
    vec3 F0 = mix(vec3(0.04), baseColor.rgb, metallic);

    //--------------------------------------------------
    // Direct lighting with Cook-Torrance BRDF
    //--------------------------------------------------

    vec3 F_dir  = fresnelSchlick(max(dot(H, V), 0.0), F0);
    vec3 kS_dir = F_dir;
    vec3 kD_dir = (vec3(1.0) - kS_dir) * (1.0 - metallic);

    float G     = geometrySmith(N, V, L, roughness);
    float NDF   = distributionGGX(N, H, roughness);
    float denom = 4.0 * NdotV * NdotL + 0.000001;

    vec3 radiance = uSunColor.rgb;
    vec3 diffuse  = kD_dir * baseColor.rgb / PI;
    vec3 specular = (NDF * G * F_dir) / denom;
    float shadow  = cascadedShadow(vWorldPos, N, uSunRadius);
    vec3 LoSun    = (diffuse + specular) * radiance * NdotL * shadow;

    vec3 LoLocalLights = accumulateLocalLights(N, V, NdotV, baseColor.rgb, metallic, roughness, F0);

    vec3 Lo = LoSun + LoLocalLights;

    //--------------------------------------------------
    // Image-based lighting (IBL)
    //--------------------------------------------------

    // Specular
    vec3 R = reflect(-V, N);
    float maxMip = uEnvSpecularMipCount - 1.0;
    float lod = roughness * maxMip;
    vec3 prefilteredColor = sampleEnvMap(uEnvSpecularTex, R, lod);
    float specOcc = specularOcclusion(NdotV, aoCombined, roughness);
    vec3 F = fresnelSchlickRoughness(NdotV, F0, roughness);
    vec2 brdf = sampleBrdfLut(NdotV, roughness);
    vec3 specularIBL = prefilteredColor * (F * brdf.x + brdf.y) * specOcc;

    // Diffuse
    vec3 kD_ibl = (vec3(1.0) - F) * (1.0 - metallic);
    vec3 irradiance = sampleEnvMap(uEnvDiffuseTex, N, 0.0);
    vec3 diffuseIBL = irradiance * baseColor.rgb * kD_ibl * aoCombined;

    //--------------------------------------------------
    // Final color composition
    //--------------------------------------------------

    vec3 ambient = diffuseIBL + specularIBL;
    vec3 color = ambient * uEnvIntensity + Lo + emissive;

    #ifdef PBR_OUTPUT_WBOIT_ACCUM
    float weight = computeWboitWeight(alpha);
    outAccum = vec4(color * alpha * weight, alpha * weight);
    #else
    fragColor = vec4(color, alpha);
    #endif
}