#version 330 core

const float PI = 3.14159265359;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in mat3 vTBN;
in vec2 vTexCoord;

out vec4 fragColor;

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

uniform float uEnvIntensity;
uniform float uEnvSpecularMipCount;
uniform vec3  uCameraPos;
uniform vec2  uScreenSize;

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
        if (coverage <= 0.0) discard; // Early-out
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
    vec3 L = normalize(vec3(0.4, 1.0, 0.2)); // Direction TO light
    vec3 lightColor = vec3(0); // Default no direct light

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

    vec3 radiance = lightColor;
    vec3 diffuse  = kD_dir * baseColor.rgb / PI;
    vec3 specular = (NDF * G * F_dir) / denom;
    vec3 Lo       = (diffuse + specular) * radiance * NdotL;

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