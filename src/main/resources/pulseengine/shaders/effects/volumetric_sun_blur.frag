#version 330 core

in vec2 uv;

layout(location = 0) out vec3 outColor;

uniform sampler2D uVolumeTex;
uniform sampler2D uDepthTex;

uniform mat4 uInvProjection;

uniform vec2 uTexelSize;
uniform vec2 uBlurDirection;
uniform float uBlurRadius;
uniform float uDepthTolerance;

const float OFFSETS[3] = float[](0.0, 1.3846153846, 3.2307692308);
const float WEIGHTS[3] = float[](0.2270270270, 0.3162162162, 0.0702702703);

float reconstructViewDepth(vec2 uvCoord)
{
    float depth01 = texture(uDepthTex, uvCoord).r;
    if (depth01 >= 0.999999)
        return 1e9;

    vec4 clip = vec4(uvCoord * 2.0 - 1.0, depth01 * 2.0 - 1.0, 1.0);
    vec4 view = uInvProjection * clip;
    return abs(view.z / max(view.w, 1e-6));
}

float bilateralWeight(float centerDepth, float sampleDepth)
{
    if (centerDepth > 1e8 && sampleDepth > 1e8)
        return 1.0;
    if (centerDepth > 1e8 || sampleDepth > 1e8)
        return 0.0;

    float depthDelta = abs(sampleDepth - centerDepth);
    return exp(-depthDelta / max(uDepthTolerance, 1e-4));
}

void main()
{
    vec3 color = texture(uVolumeTex, uv).rgb * WEIGHTS[0];
    float totalWeight = WEIGHTS[0];
    float centerDepth = reconstructViewDepth(uv);

    for (int i = 1; i < 3; i++)
    {
        vec2 offset = uBlurDirection * uTexelSize * OFFSETS[i] * uBlurRadius;

        vec2 uvA = uv + offset;
        vec2 uvB = uv - offset;

        float depthA = reconstructViewDepth(uvA);
        float depthB = reconstructViewDepth(uvB);
        float weightA = WEIGHTS[i] * bilateralWeight(centerDepth, depthA);
        float weightB = WEIGHTS[i] * bilateralWeight(centerDepth, depthB);

        color += texture(uVolumeTex, uvA).rgb * weightA;
        color += texture(uVolumeTex, uvB).rgb * weightB;
        totalWeight += weightA + weightB;
    }

    outColor = color / max(totalWeight, 1e-5);
}