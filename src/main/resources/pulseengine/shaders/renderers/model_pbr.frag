#version 330 core

#define MAX_POINT_LIGHTS 32

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in mat3 vTBN;
in vec2 vTexCoord;
in vec4 vSunPos;

out vec4 fragColor;

// Textures
uniform sampler2DArray textureArrays[16]; // TODO: Prefix with u
uniform sampler2D uGtaoTex;

// Material
uniform vec4  uBaseColor;
uniform vec4  uAlbedoTex;
uniform vec4  uNormalTex;
uniform vec4  uAoMetalRoughTex;
uniform vec4  uEmissiveTex;
uniform vec4  uEnvDiffuseTex;
uniform vec4  uEnvSpecularTex;
uniform vec4  uEnvBrdfLutTex;
uniform vec4  uAoMetalRoughNormalFactor;
uniform vec4  uEmissiveFactor;
uniform float uAlphaCutoff; // 0 for opaque/blend
uniform float uAoIntensity;

// Lighting
uniform float uEnvIntensity;
uniform float uEnvSpecularMipCount;
uniform vec3  uCameraPos;
uniform vec2  uScreenSize;
uniform vec4  uSunColor;
uniform vec3  uSunDirection;
uniform float uSunRadius;
uniform int   uLightCount;
uniform vec4  uLightData[MAX_POINT_LIGHTS * 4]; // 4 vec4s per light: pos+radius, color+intensity, dir+outerCos, innerCos+isSpot+padding

// Shadow mapping
uniform sampler2DShadow uShadowCompareTex;
uniform sampler2D uShadowDepthTex;
uniform float uShadowMapNear;
uniform float uShadowMapFar;
uniform float uShadowMapSizeMeters;
uniform bool  uShadowContactHardening;

// ------------------------------------------------------------------
// Texture sampling
// ------------------------------------------------------------------

vec4 sampleTexOrDefault(vec4 texDesc, vec3 defaultColor)
{
    int samplerIndex = int(texDesc.x);
    if (samplerIndex < 0) return vec4(defaultColor, 1.0);

    float layer = texDesc.y;
    vec2 uvMax = texDesc.zw;
    return texture(textureArrays[samplerIndex], vec3(vTexCoord * uvMax, layer));
}

vec3 sampleWorldSpaceNormal(out float normalLenTS)
{
    // Tangent-space normal
    vec3 normalTs = sampleTexOrDefault(uNormalTex, vec3(0.5, 0.5, 1.0)).rgb * 2.0 - 1.0;
    normalTs.xy *= uAoMetalRoughNormalFactor.w; // Normal scale

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

float pcssShadow(vec3 N, vec3 L, vec4 lightPos, float lightRadius)
{
    // Project light position into shadow map
    vec3 pos = (lightPos.xyz / lightPos.w) * 0.5 + 0.5; // NDC [-1,1] -> UV/depth01 [0,1]

    if (pos.x < 0.0 || pos.x > 1.0 || pos.y < 0.0 || pos.y > 1.0 || pos.z < 0.0 || pos.z > 1.0)
        return 1.0; // Outside shadow map, considered fully lit

    // Stable per-texel rotation
    vec2 shadowMapTexRes = vec2(textureSize(uShadowDepthTex, 0));
    vec2 texelCoord = floor(pos.xy * shadowMapTexRes);
    float angle = TAU * fract(sin(dot(texelCoord, vec2(127.1, 311.7))) * 43758.5453123);
    float s = sin(angle), c = cos(angle);
    mat2 R = mat2(c, -s, s, c);

    float texelUv = 1.0 / shadowMapTexRes.x; // Square shadow map
    float filterRadiusUv = lightRadius * texelUv;

    if (uShadowContactHardening)
    {
        const float searchRadiusTexels = 8.0;
        float searchRadiusUv = searchRadiusTexels * texelUv;

        // Blocker search 
        int numBlockers = 0;
        float sumBlockerLinearDist = 0.0;
        for (int i = 0; i < 16; i++)
        {
            vec2 offset = R * POISSON_DISK_16[i] * searchRadiusUv;
            float d01 = texture(uShadowDepthTex, pos.xy + offset).r;
            if (d01 < pos.z)
            {
                numBlockers++;
                sumBlockerLinearDist += uShadowMapNear + d01 * (uShadowMapFar - uShadowMapNear);
            }
        }

        if (numBlockers == 0) return 1.0; // No blockers, fully lit

        float lightAngularRadius = lightRadius * 0.00465; // 0.00465 = earth-sun dist / earth radius
        float avgBlockerLinearDist = (numBlockers > 0) ? (sumBlockerLinearDist / float(numBlockers)) : 0.0;
        float depthLinear = uShadowMapNear + pos.z * (uShadowMapFar - uShadowMapNear);
        float penumbraSizeMeters = max(depthLinear - avgBlockerLinearDist, 0.0) * lightAngularRadius;
        float metersPerTexel = uShadowMapSizeMeters / shadowMapTexRes.x;
        
        filterRadiusUv = texelUv * clamp(penumbraSizeMeters / max(metersPerTexel, 1e-6), 0.0, 64.0);
    }

    float sum = 0.0;
    for (int i = 0; i < 16; i++) 
    {
        // No bias is used, as lightPos is offset by the surface normal in the vertex shader
        vec2 o = R * POISSON_DISK_16[i] * filterRadiusUv;
        sum += texture(uShadowCompareTex, vec3(pos.xy + o, pos.z)); 
    }

    return sum / 16.0;
}

// ------------------------------------------------------------------
// Point lights
// ------------------------------------------------------------------ 

vec3 accumulatePointLights(vec3 N, vec3 V, float NdotV, vec3 baseColor, float metallic, float roughness, vec3 F0)
{
    vec3 Lo = vec3(0.0);

    for (int i = 0; i < MAX_POINT_LIGHTS; i++)
    {
        if (i >= uLightCount) break;

        int index = i * 4;
        vec3  lightPos   = uLightData[index].xyz;
        float radius     = uLightData[index].w;
        vec3  lightColor = uLightData[index + 1].rgb;
        float intensity  = uLightData[index + 1].a;
        vec3  lightDir   = uLightData[index + 2].xyz;
        float outerCos   = uLightData[index + 2].w;
        float innerCos   = uLightData[index + 3].x;
        float isSpot     = uLightData[index + 3].y;

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

        vec3 radiance = lightColor * intensity * attenuation;

        // Cook-Torrance BRDF
        vec3 H  = normalize(V + L);
        vec3 F  = fresnelSchlick(max(dot(H, V), 0.0), F0);
        vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);

        float G     = geometrySmith(N, V, L, roughness);
        float NDF   = distributionGGX(N, H, roughness);
        float denom = 4.0 * NdotV * NdotL + 0.000001;

        vec3 diffuse  = kD * baseColor / PI;
        vec3 specular = (NDF * G * F) / denom;

        Lo += (diffuse + specular) * radiance * NdotL;
    }

    return Lo;
}

// ------------------------------------------------------------------
// Main
// ------------------------------------------------------------------

void main()
{
    vec4 baseColor = uBaseColor * sampleTexOrDefault(uAlbedoTex, vec3(1.0));
    float alpha = baseColor.a;

    if (uAlphaCutoff > 0.0)
    {
        // Coverage AA around the cutoff
        float w = max(fwidth(alpha), 1.0 / 255.0);
        float coverage = smoothstep(uAlphaCutoff - w, uAlphaCutoff + w, alpha);
        if (coverage < 0.2) discard; // Early-out
        alpha = coverage;
    }

    // PBR material properties
    vec3 emissive   = sampleTexOrDefault(uEmissiveTex, vec3(1.0)).rgb * uEmissiveFactor.rgb;
    vec3 aomr       = sampleTexOrDefault(uAoMetalRoughTex, vec3(1.0, 1.0, 0.0)).rgb; // Default AO=1, rough=1, metal=0
    float ao        = clamp(mix(1.0, aomr.r, uAoMetalRoughNormalFactor.x), 0.0,  1.0);
    float roughness = clamp(aomr.g * uAoMetalRoughNormalFactor.y, 0.04, 1.0);
    float metallic  = clamp(aomr.b * uAoMetalRoughNormalFactor.z, 0.0,  1.0);

    // Normal + View
    float normalLength;
    vec3 N = sampleWorldSpaceNormal(normalLength);
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
    float shadow  = pcssShadow(N, L, vSunPos, uSunRadius);
    vec3 LoSun    = (diffuse + specular) * radiance * NdotL * shadow;

    vec3 LoPointLighs = accumulatePointLights(N, V, NdotV, baseColor.rgb, metallic, roughness, F0);

    vec3 Lo = LoSun + LoPointLighs;

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

    fragColor = vec4(color, alpha);
}