#version 330 core

in vec2 uv;
out vec4 fragColor;
uniform samplerCube captureTexture;
uniform float resolution;
uniform float roughness;
uniform bool diffuse;
uniform int face;

const float PI = 3.14159265359;
const uint SAMPLE_COUNT = 256u;

vec3 faceDirection(vec2 p)
{
    if (face == 0) return vec3( 1.0, -p.y, -p.x);
    if (face == 1) return vec3(-1.0, -p.y,  p.x);
    if (face == 2) return vec3( p.x,  1.0,  p.y);
    if (face == 3) return vec3( p.x, -1.0, -p.y);
    if (face == 4) return vec3( p.x, -p.y,  1.0);

    return vec3(-p.x, -p.y, -1.0);
}

float radicalInverse(uint bits)
{
    bits = (bits << 16u) | (bits >> 16u);
    bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
    bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
    bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
    bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
    return float(bits) * 2.3283064365386963e-10;
}

void main()
{
    vec3 N = normalize(faceDirection(uv * 2.0 - 1.0));
    
    if (!diffuse && roughness < 0.0001)
    {
        fragColor = vec4(textureLod(captureTexture, N, 0.0).rgb, 1.0);
        return;
    }
    
    vec3 up = abs(N.y) < 0.999 ? vec3(0, 1, 0) : vec3(1, 0, 0);
    vec3 T = normalize(cross(up, N));
    vec3 B = cross(N, T);
    if (diffuse)
    {
        vec3 color = vec3(0.0);
        float texelSolidAngle = 4.0 * PI / (6.0 * resolution * resolution);
        for (uint i = 0u; i < SAMPLE_COUNT; i++)
        {
            vec2 Xi = vec2((float(i) + 0.5) / float(SAMPLE_COUNT), radicalInverse(i));
            float phi = 2.0 * PI * Xi.y;
            float cosTheta = sqrt(1.0 - Xi.x);
            float sinTheta = sqrt(Xi.x);
            vec3 L = T * (cos(phi) * sinTheta) + B * (sin(phi) * sinTheta) + N * cosTheta;
            float sampleSolidAngle = PI / (float(SAMPLE_COUNT) * cosTheta);
            float lod = max(0.0, 0.5 * log2(sampleSolidAngle / texelSolidAngle));
            color += textureLod(captureTexture, L, lod).rgb;
        }
        // Cosine-weighted sampling stores irradiance / PI, matching the diffuse IBL convention.
        fragColor = vec4(color / float(SAMPLE_COUNT), 1.0);
        return;
    }
    
    float a = roughness * roughness;
    float a2 = a * a;
    float texelSolidAngle = 4.0 * PI / (6.0 * resolution * resolution);

    vec3 color = vec3(0.0);
    float weight = 0.0;
    
    for (uint i = 0u; i < SAMPLE_COUNT; i++)
    {
        vec2 Xi = vec2(float(i) / float(SAMPLE_COUNT), radicalInverse(i));
        float phi = 2.0 * PI * Xi.x;
        float cosTheta = sqrt((1.0 - Xi.y) / (1.0 + (a2 - 1.0) * Xi.y));
        float sinTheta = sqrt(max(0.0, 1.0 - cosTheta * cosTheta));
        vec3 H = normalize(T * (cos(phi) * sinTheta) + B * (sin(phi) * sinTheta) + N * cosTheta);
        float NdotH = max(dot(N, H), 0.0);
        vec3 L = normalize(2.0 * NdotH * H - N);
        float NdotL = max(dot(N, L), 0.0);
        
        if (NdotL > 0.0)
        {
            float denominator = NdotH * NdotH * (a2 - 1.0) + 1.0;
            float D = a2 / max(PI * denominator * denominator, 1e-8);
            float pdf = max(D * 0.25, 1e-6);
            float sampleSolidAngle = 1.0 / (float(SAMPLE_COUNT) * pdf);
            float lod = max(0.0, 0.5 * log2(sampleSolidAngle / texelSolidAngle));
            
            color += textureLod(captureTexture, L, lod).rgb * NdotL;
            weight += NdotL;
        }
    }

    fragColor = vec4(color / max(weight, 1e-4), 1.0);
}