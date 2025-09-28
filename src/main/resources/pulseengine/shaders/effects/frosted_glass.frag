#version 330 core

#define TAU 6.28318

in vec2 uv;

out vec4 fragColor;

uniform sampler2D baseTex;

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
    vec3 p1 = fract(vec3(p.xyx) * 0.1031);
    p1 += dot(p1, p1.yzx + 33.33);
    return fract((p1.x + p1.y) * p1.z);
}

mat2 rotate(float a)
{
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}

vec4 sampleAtLevel(float lod, vec2 uvCoord)
{
    vec2 stepSize = radius / vec2(textureSize(baseTex, int(lod)));
    float jitter = 0.7;
    float angle = (hash(gl_FragCoord.xy) - 0.5) * TAU * jitter;
    mat2 rotMat = rotate(angle);
    vec4 sum = textureLod(baseTex, uvCoord, lod);;
    float weightedSum = 1.0;

    // Symmetric ring samples
    for (int i = 0; i < 12; ++i)
    {
        vec2 p = POISSON[i];
        vec2 offset = rotMat * p * stepSize;
        float weight = exp(-dot(p, p) * 1.5); // Gaussian falloff
        sum += textureLod(baseTex, uvCoord + offset, lod) * weight;
        sum += textureLod(baseTex, uvCoord - offset, lod) * weight;
        weightedSum += weight * 2.0;
    }

    return sum / weightedSum;
}

void main()
{
    // Determine target LOD based on intensity
    float t = clamp(intensity, 0.0, 1.0);
    float targetLod = mix(0.0, maxLod, t);

    // Determine mip levels and interpolation weight
    float aLod = clamp(floor(targetLod), 0.0, maxLod);
    float bLod = clamp(aLod + 1.0, 0.0, maxLod);
    float wight = clamp(fract(targetLod), 0.0, 1.0);

    // Sample and blend between two mip levels
    vec4 a = sampleAtLevel(aLod, uv);
    vec4 b = sampleAtLevel(bLod, uv);
    fragColor = mix(a, b, wight);

    // Dither to avoid banding
    float dither = (hash(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor.rgb += dither * 0.05;

    // Apply brightness
    fragColor.rgb *= brightness;
}