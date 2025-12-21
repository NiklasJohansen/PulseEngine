#version 330 core

const float PI = 3.14159265359;

in vec2 uv;
out vec4 fragColor;

uniform sampler2DArray textureArray;
uniform vec3 srcEnv; // x: layer, y: uMax, z: vMax
uniform float roughness;

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

vec3 sampleEnv(vec3 dir)
{
    float layer = srcEnv.x;
    vec2 uvMax = srcEnv.yz;
    vec2 uv = dirToLatLong(dir) * uvMax;
    return texture(textureArray, vec3(uv, layer)).rgb;
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

void main()
{
    vec3 N = latLongToDir(uv);
    vec3 V = N;

    const uint SAMPLE_COUNT = 1024u;
    vec3 prefiltered = vec3(0.0);
    float totalWeight = 0.0;

    for (uint i = 0u; i < SAMPLE_COUNT; i++)
    {
        vec2 Xi = hammersley(i, SAMPLE_COUNT);
        vec3 H = importanceSampleGGX(Xi, roughness, N);
        vec3 L = normalize(2.0 * dot(V,H) * H - V);
        float NdotL = max(dot(N, L), 0.0);

        if (NdotL > 0.0)
        {
            vec3 env = sampleEnv(L);
            prefiltered += env * NdotL;
            totalWeight += NdotL;
        }
    }

    prefiltered = prefiltered / max(totalWeight, 0.0001);
    fragColor = vec4(prefiltered, 1.0);
}