#version 330 core

const float PI = 3.14159265359;

in vec2 uv;
out vec4 fragColor;

uniform sampler2DArray textureArray;
uniform vec3 srcEnv; // x: layer, y: uMax, z: vMax
uniform vec2 srcEnvSize;
uniform float roughness;

const uint SAMPLE_COUNT = 1024u;

vec2 dirToLatLong(vec3 dir)
{
    dir = normalize(dir);
    float phi = atan(dir.z, dir.x);
    float theta = acos(clamp(dir.y, -1.0, 1.0));
    float u = phi / (2.0 * PI) + 0.5;
    float v = theta / PI;
    return vec2(u, v);
}

vec3 latLongToDir(vec2 uv)
{
    float phi = (uv.x - 0.5) * 2.0 * PI;
    float theta = uv.y * PI;
    float x = cos(phi) * sin(theta);
    float y = cos(theta);
    float z = sin(phi) * sin(theta);
    return vec3(x, y, z);
}

float radicalInverse_VdC(uint bits)
{
    bits = (bits << 16u) | (bits >> 16u);
    bits = ((bits & 0x55555555u) << 1u)  | ((bits & 0xAAAAAAAAu) >> 1u);
    bits = ((bits & 0x33333333u) << 2u)  | ((bits & 0xCCCCCCCCu) >> 2u);
    bits = ((bits & 0x0F0F0F0Fu) << 4u)  | ((bits & 0xF0F0F0F0u) >> 4u);
    bits = ((bits & 0x00FF00FFu) << 8u)  | ((bits & 0xFF00FF00u) >> 8u);
    return float(bits) * 2.3283064365386963e-10; // / 2^32
}

vec2 hammersley(uint i, uint N)
{
    return vec2(float(i) / float(N), radicalInverse_VdC(i));
}

vec3 importanceSampleGGX(vec2 Xi, float roughness, vec3 N)
{
    float a = roughness * roughness;
    float phi = 2.0 * PI * Xi.x;
    float cosTheta = sqrt((1.0 - Xi.y) / (1.0 + (a * a - 1.0) * Xi.y));
    float sinTheta = sqrt(1.0 - cosTheta * cosTheta);
    vec3 H = vec3(
        cos(phi) * sinTheta,
        cosTheta,
        sin(phi) * sinTheta
    );
    vec3 up = abs(N.y) < 0.999 ? vec3(0,1,0) : vec3(1,0,0);
    vec3 T = normalize(cross(up, N));
    vec3 B = cross(N, T);

    return normalize(T * H.x + B * H.z + N * H.y);
}

float distributionGGX(float NdotH, float roughness)
{
    float a  = roughness * roughness;
    float a2 = a * a;
    float denom = (NdotH * NdotH) * (a2 - 1.0) + 1.0;
    return a2 / max(PI * denom * denom, 1e-8);
}

vec3 sampleEnvMap(vec3 dir, float lod)
{
    float layer = srcEnv.x;
    vec2 uvMax = srcEnv.yz;
    vec2 uvLL = dirToLatLong(dir) * uvMax;
    return textureLod(textureArray, vec3(uvLL, layer), lod).rgb;
}

void main()
{
    vec3 N = normalize(latLongToDir(uv));
    vec3 V = N;

    // Solid angle per texel
    float saTexel = 4.0 * PI / max(srcEnvSize.x * srcEnvSize.y, 1.0);
    vec3 prefiltered = vec3(0.0);
    float totalWeight = 0.0;

    for (uint i = 0u; i < SAMPLE_COUNT; i++)
    {
        vec2 Xi = hammersley(i, SAMPLE_COUNT);
        vec3 H  = importanceSampleGGX(Xi, roughness, N);

        float NdotH = max(dot(N, H), 0.0);
        float HdotV = max(dot(H, V), 0.0);

        vec3 L = normalize(2.0 * HdotV * H - V);
        float NdotL = max(dot(N, L), 0.0);

        if (NdotL > 0.0)
        {
            float D = distributionGGX(NdotH, roughness);
            float pdf = (D * NdotH) / max(4.0 * HdotV, 1e-6);

            float saSample = 1.0 / max(float(SAMPLE_COUNT) * pdf, 1e-6);
            float lod = max(0.0, 0.5 * log2(saSample / saTexel));

            vec3 env = sampleEnvMap(L, lod);
            prefiltered += env * NdotL;
            totalWeight += NdotL;
        }
    }

    prefiltered = prefiltered / max(totalWeight, 1e-4);
    fragColor = vec4(prefiltered, 1.0);
}