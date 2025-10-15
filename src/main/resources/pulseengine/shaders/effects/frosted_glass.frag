#version 330 core

#define TAU 6.28318

in vec2 uv;

out vec4 fragColor;

uniform sampler2D tex;

uniform float intensity;
uniform float brightness;
uniform float radius;
uniform float maxLod;

// 16-point poisson disk
const vec2 POISSON[16] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074),
    vec2(-0.696,  0.457), vec2(-0.203,  0.621),
    vec2( 0.962, -0.195), vec2( 0.473, -0.480),
    vec2( 0.519,  0.767), vec2( 0.185, -0.893),
    vec2( 0.507,  0.064), vec2( 0.896,  0.412),
    vec2(-0.322, -0.932), vec2(-0.792, -0.598),
    vec2(-0.633,  0.173), vec2(-0.183, -0.266),
    vec2( 0.321, -0.132), vec2(-0.046,  0.203)
);

float hash(vec2 p)
{
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453123);
}

mat2 rotate(float a)
{
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}

vec3 sampleAtLevel(float lod, vec2 uvCoord)
{
    vec2 texSize  = vec2(textureSize(tex, int(lod)));
    vec2 stepSize = radius / texSize;
    float jitter = 0.6;
    float angle = (hash(uvCoord * texSize) - 0.5) * TAU * jitter;
    mat2 rotMat = rotate(angle);
    vec3 sum = textureLod(tex, clamp(uvCoord, 0.0, 1.0), lod).rgb;
    float wsum = 1.0;

    for (int i = 0; i < 12; ++i)
    {
        vec2 p = POISSON[i];
        vec2 offset = rotMat * p * stepSize;
        float weight = exp(-dot(p, p) * 1.5);
        vec2 aUv = uvCoord + offset;
        vec2 bUv = uvCoord - offset;
        float aMask = float(all(greaterThanEqual(aUv, vec2(0))) && all(lessThanEqual(aUv, vec2(1))));
        float bMask = float(all(greaterThanEqual(bUv, vec2(0))) && all(lessThanEqual(bUv, vec2(1))));

        sum += textureLod(tex, clamp(aUv, 0.0, 1.0), lod).rgb * weight * aMask;
        sum += textureLod(tex, clamp(bUv, 0.0, 1.0), lod).rgb * weight * bMask;
        wsum += weight * (aMask + bMask);
    }

    return sum / max(wsum, 1e-6);
}

void main()
{
    // Determine target LOD based on intensity
    float t = clamp(intensity, 0.0, 1.0);
    float targetLod = mix(0.0, maxLod, t);

    // Determine mip levels and interpolation weight
    float aLod = clamp(floor(targetLod), 0.0, maxLod);
    float bLod = clamp(aLod + 1.0, 0.0, maxLod);
    float weight = clamp(fract(targetLod), 0.0, 1.0);

    // Sample and blend between two mip levels
    vec3 a = sampleAtLevel(aLod, uv);
    vec3 b = sampleAtLevel(bLod, uv);
    vec3 color = mix(a, b, weight);

    // Dither to avoid banding
    float dither = (hash(uv * vec2(textureSize(tex, 0))) - 0.5) / 255.0;
    color += dither * 0.1;

    // Reduce brightness to improve visibility
    color *= brightness;

    fragColor = vec4(color, 1.0);
}