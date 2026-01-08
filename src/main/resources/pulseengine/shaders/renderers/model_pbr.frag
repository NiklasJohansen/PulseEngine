#version 330 core

const float PI = 3.14159265359;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in mat3 vTBN;
in vec2 vTexCoord;

out vec4 fragColor;

uniform sampler2DArray textureArrays[16];

uniform vec4 albedoTex;
uniform vec4 normalTex;
uniform vec4 aoMetalRoughTex;
uniform vec4 emissiveTex;
uniform vec4 envDiffuseTex;
uniform vec4 envSpecularTex; 
uniform vec4 envBrdfLutTex;

uniform float envIntensity;
uniform float envSpecularMipCount;
uniform vec3  cameraPos;
uniform float alphaCutoff; // 0 for opaque/blend

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
    vec3 normalTs = sampleTexOrDefault(normalTex, vWorldNormal).rgb * 2.0 - 1.0;
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
    int samplerIndex = int(envBrdfLutTex.x);
    if (samplerIndex < 0) return vec2(0.0);

    float layer = envBrdfLutTex.y;
    vec2 uvMax = envBrdfLutTex.zw;
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
    vec4 baseColor = sampleTexOrDefault(albedoTex, vec3(1));
    float alpha = baseColor.a;

    if (alphaCutoff > 0.0)
    {
        // Coverage AA around the cutoff
        float w = max(fwidth(alpha), 1.0 / 255.0);
        float coverage = smoothstep(alphaCutoff - w, alphaCutoff + w, alpha);

        if (coverage <= 0.0) discard; // Early-out

        alpha = coverage;
    }

    // Sample PBR material properties
    vec3 aomr          = sampleTexOrDefault(aoMetalRoughTex, vec3(1.0, 1.0, 0.0)).rgb; // Default AO=1, rough=1, metal=0
    float ao           = clamp(aomr.r, 0.0, 1.0);
    float roughness    = clamp(aomr.g, 0.04, 1.0);
    float metallic     = clamp(aomr.b, 0.0, 1.0);
    vec3 emissiveColor = sampleTexOrDefault(emissiveTex, vec3(0.0)).rgb;

    // Prevent all black when no AO channel is present
    if (ao == 0.0) ao = 1.0;

    // View + light
    float normalLength;
    vec3 N = sampleWorldSpaceNormal(normalLength);
    vec3 V = normalize(cameraPos - vWorldPos);

    // Toksvig roughness adjustment
    float len = clamp(normalLength, 1e-5, 1.0);
    float r = roughness * roughness;
    float sigma2 = (1.0 - len) / len;
    r = clamp(r + sigma2 * 0.5, 0.0, 1.0);
    roughness = sqrt(r);

    // Single directional light
    vec3 L = normalize(vec3(0.4, 1.0, 0.2)); // Direction TO light
    vec3 lightColor = vec3(0);   // Bright white-ish

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
    
    float G   = geometrySmith(N, V, L, roughness);
    float NDF = distributionGGX(N, H, roughness);
    float denom = 4.0 * NdotV * NdotL + 0.000001;

    vec3 radiance = lightColor;
    vec3 diffuse  = kD_dir * baseColor.rgb / PI;
    vec3 specular = (NDF * G * F_dir) / denom;
    vec3 Lo       = (diffuse + specular) * radiance * NdotL;

    //--------------------------------------------------
    // Image-based lighting (IBL)
    //--------------------------------------------------
    
    vec2 brdf = sampleBrdfLut(NdotV, roughness);

    // Specular
    vec3 R = reflect(-V, N);
    float maxMip = envSpecularMipCount - 1.0;
    float lod = roughness * maxMip;
    vec3 prefilteredColor = sampleEnvMap(envSpecularTex, R, lod);
    float specOcc = specularOcclusion(NdotV, ao, roughness);
    vec3 specularIBL = prefilteredColor * (F0 * brdf.x + brdf.y) * specOcc;

    // Diffuse
    vec3 F_ibl = fresnelSchlickRoughness(NdotV, F0, roughness);
    vec3 kD_ibl = (vec3(1.0) - F_ibl) * (1.0 - metallic);
    vec3 irradiance = sampleEnvMap(envDiffuseTex, N, 0.0);
    vec3 diffuseIBL = irradiance * baseColor.rgb * kD_ibl * ao;
    
    //--------------------------------------------------
    // Final color composition
    //--------------------------------------------------

    vec3 ambient = diffuseIBL + specularIBL;
    vec3 color = ambient * envIntensity + Lo + emissiveColor;

    fragColor = vec4(color, alpha);
}