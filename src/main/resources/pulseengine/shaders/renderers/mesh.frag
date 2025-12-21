#version 330 core

const float PI = 3.14159265359;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in mat3 vTBN;
in vec2 vTexCoord;

out vec4 fragColor;

uniform sampler2DArray textureArrays[16];

// Packed texture info for BPR material (x: sampler, y: layer, z: uMax, w: vMax)
uniform vec4 albedoTex;
uniform vec4 normalTex;
uniform vec4 aoMetalRoughTex;
uniform vec4 specularTex;
uniform vec4 emissiveTex;
uniform vec4 envDiffuseTex;
uniform vec4 envSpecularTex; 
uniform vec4 brdfLutTex;

uniform vec3 cameraPos;
uniform float envSpecularMipCount;

// ------------------------------------------------------------------
// Helper functions
// ------------------------------------------------------------------

vec2 dirToLatLong(vec3 dir)
{
    dir = normalize(dir);
    float phi = atan(dir.z, dir.x);
    float theta = acos(clamp(dir.y, -1.0, 1.0));
    float u = phi / (2.0 * PI) + 0.5; 
    float v = theta / PI;
    return vec2(u, v);
}

// ------------------------------------------------------------------
// Texture sampling
// ------------------------------------------------------------------

vec4 sampleAlbedo()
{
    if (albedoTex.x <= 0.0) return vec4(1.0);

    int samplerIndex = int(albedoTex.x);
    float layer = albedoTex.y;
    vec2 uvMax = albedoTex.zw;
    vec2 uv = vTexCoord * uvMax;

    return texture(textureArrays[samplerIndex], vec3(uv, layer));
}

vec3 sampleWorldSpaceNormal(out float normalLenTS)
{
    if (normalTex.x <= 0.0)
    {
        normalLenTS = 1.0;
        return normalize(vWorldNormal);
    }

    int samplerIndex = int(normalTex.x);
    float layer = normalTex.y;
    vec2 uvMax = normalTex.zw;
    vec2 uv = vTexCoord * uvMax;

    // Tangent-space normal, 0..1 → -1..1
    vec3 normalTangentSpace = texture(textureArrays[samplerIndex], vec3(uv, layer)).xyz * 2.0 - 1.0;
    normalLenTS = length(normalTangentSpace);

    // To world space
    return normalize(vTBN * normalTangentSpace);
}

vec3 sampleAoMetallicRoughness()
{
    if (aoMetalRoughTex.x <= 0.0) return vec3(1.0, 1.0, 0.0); // Full AO, rough, non-metal

    int samplerIndex = int(aoMetalRoughTex.x);
    float layer = aoMetalRoughTex.y;
    vec2 uvMax = aoMetalRoughTex.zw;
    vec2 uv = vTexCoord * uvMax;

    return texture(textureArrays[samplerIndex], vec3(uv, layer)).rgb; // R=AO, G=roughness, B=metallic
}

vec4 sampleSpecular()
{
    if (specularTex.x <= 0.0) return vec4(0.04, 0.04, 0.04, 0.0); // Default small F0, 0 strength

    int samplerIndex = int(specularTex.x);
    float layer = specularTex.y;
    vec2 uvMax = specularTex.zw;
    vec2 uv = vTexCoord * uvMax;

    return texture(textureArrays[samplerIndex], vec3(uv, layer));
}

vec3 sampleEmissive()
{
    if (emissiveTex.x <= 0.0) return vec3(0.0);

    int samplerIndex = int(emissiveTex.x);
    float layer = emissiveTex.y;
    vec2 uvMax = emissiveTex.zw;
    vec2 uv = vTexCoord * uvMax;

    return texture(textureArrays[samplerIndex], vec3(uv, layer)).rgb;
}

vec3 sampleEnvLatLong(vec3 dir, vec4 texInfo, float lod)
{
    if (texInfo.x <= 0.0) return vec3(0.0);

    int   samplerIndex = int(texInfo.x);
    float layer        = texInfo.y;
    vec2  uvMax        = texInfo.zw;
    vec2 uv = dirToLatLong(dir) * uvMax;
    
    return textureLod(textureArrays[samplerIndex], vec3(uv, layer), lod).rgb;
}

vec2 sampleBrdfLut(float NdotV, float roughness)
{
    if (brdfLutTex.x <= 0.0) return vec2(0.0);

    int   samplerIndex = int(brdfLutTex.x);
    float layer        = brdfLutTex.y;
    vec2  uvMax        = brdfLutTex.zw;

    vec2 uv = vec2(NdotV, roughness) * uvMax;

    return texture(textureArrays[samplerIndex], vec3(uv, layer)).rg;
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

// ------------------------------------------------------------------
// Main
// ------------------------------------------------------------------

void main()
{
    // Sample materials
    vec4 baseColor     = sampleAlbedo();
    vec3 aomr          = sampleAoMetallicRoughness();
    float ao           = clamp(aomr.r, 0.0, 1.0);
    float roughness    = clamp(aomr.g, 0.0, 1.0);
    float metallic     = clamp(aomr.b, 0.0, 1.0);
    vec4 specularColor = sampleSpecular();
    vec3 emissiveColor = sampleEmissive();

    // View + light
    float normalLength;
    vec3 N = sampleWorldSpaceNormal(normalLength);
    vec3 V = normalize(cameraPos - vWorldPos);

    // Roughness inflation to prevent specular aliasing/flickering
    float amount = 5.0;
    vec3 dNdx = dFdx(N);
    vec3 dNdy = dFdy(N);
    float geom = max(dot(dNdx, dNdx), dot(dNdy, dNdy)) * amount;  // Screen-space geometric variance
    float toksvig = clamp(1.0 - normalLength, 0.0, 1.0) * amount; // Toksvig-style: short normals → more variance
    roughness = clamp(sqrt(roughness * roughness + toksvig * toksvig + geom * geom), 0.0, 1.0);
    
    // Simple single directional light
    vec3 L = normalize(vec3(0.4, 1.0, 0.2)); // Direction TO light
    vec3 lightColor = vec3(3.0, 3.0, 3.0);   // Bright white-ish

    vec3 H = normalize(V + L);
    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.0001);
    
    // Base reflectivity from metallic and specular map 
    vec3 F0 = vec3(0.04);
    F0 = mix(F0, baseColor.rgb, metallic);
    float specStrength = clamp(specularColor.a, 0.0, 1.0);
    F0 = mix(F0, specularColor.rgb, specStrength * (1.0 - metallic));

    // Cook-Torrance BRDF
    float NDF = distributionGGX(N, H, roughness);
    float G   = geometrySmith(N, V, L, roughness);
    vec3  F   = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 kS = F;
    vec3 kD = (vec3(1.0) - kS) * (1.0 - metallic);

    float denom = 4.0 * NdotV * NdotL + 0.000001;
    vec3 specular = (NDF * G * F) / denom;

    vec3 radiance = lightColor;
    vec3 diffuse  = kD * baseColor.rgb / PI;
    vec3 Lo = (diffuse + specular) * radiance * NdotL;

    // IBL diffuse
    vec3 irradiance = sampleEnvLatLong(N, envDiffuseTex, 0.0);
    vec3 diffuseIBL = irradiance * baseColor.rgb * kD * ao;

    // IBL specular
    vec3 R = reflect(-V, N);
    float maxMip = envSpecularMipCount - 1.0;
    float lod = roughness * maxMip;
    vec3 prefilteredColor = sampleEnvLatLong(R, envSpecularTex, lod);

    // BRDF LUT
    vec2 brdf = sampleBrdfLut(NdotV, roughness);
    vec3 specularIBL = prefilteredColor * (F * brdf.x + brdf.y);

    vec3 ambient = diffuseIBL + specularIBL;
    vec3 color = ambient + Lo + emissiveColor;

    fragColor = vec4(color, baseColor.a);
}