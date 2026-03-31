#version 330 core

in vec2 uv;

layout(location = 0) out vec3 outColor;

uniform sampler2D uVolumeTex;
uniform sampler2D uDepthTex;

uniform mat4 uInvProjection;

uniform ivec2 uResolution;
uniform ivec2 uVolumeTexSize;
uniform int uDownsampleFactor;

uniform float uEdgeTolerance;
uniform float uUpsampleBlur;
uniform float uBackgroundDepth;

const float EPS = 1e-6;

vec2 pixelPosToUv(ivec2 pixelPos)
{
    return (vec2(pixelPos) + 0.5) / vec2(uResolution);
}

float reconstructViewDepth(vec2 uvCoord, float depth01)
{
    if (depth01 >= 0.999999)
        return uBackgroundDepth;

    vec4 clip = vec4(uvCoord * 2.0 - 1.0, depth01 * 2.0 - 1.0, 1.0);
    vec4 view = uInvProjection * clip;
    return abs(view.z / max(view.w, EPS));
}

float fetchViewDepth(ivec2 pixelPos)
{
    ivec2 clampedPos = clamp(pixelPos, ivec2(0), uResolution - ivec2(1));
    float depth01 = texelFetch(uDepthTex, clampedPos, 0).r;
    return reconstructViewDepth(pixelPosToUv(clampedPos), depth01);
}

ivec2 volumePixelPosToFullResPixelCenterPos(ivec2 volumePixelPos)
{
    return clamp(
        volumePixelPos * uDownsampleFactor + ivec2(uDownsampleFactor / 2),
        ivec2(0),
        uResolution - ivec2(1)
    );
}

void main()
{
    ivec2 pixelPos = ivec2(gl_FragCoord.xy);
    ivec2 volumePixelPosCenter = clamp(pixelPos / uDownsampleFactor, ivec2(0), uVolumeTexSize - ivec2(1));

    float centerViewDepth = fetchViewDepth(pixelPos);
    vec3 sumColor = vec3(0.0);
    float sumWeight = 0.0;

    for (int oy = -1; oy <= 2; ++oy)
    {
        for (int ox = -1; ox <= 2; ++ox)
        {
            ivec2 volumeSamplePos = clamp(volumePixelPosCenter + ivec2(ox, oy), ivec2(0), uVolumeTexSize - ivec2(1));
            vec3 volumeSample = texelFetch(uVolumeTex, volumeSamplePos, 0).rgb;

            ivec2 samplePixelPos = volumePixelPosToFullResPixelCenterPos(volumeSamplePos);
            float sampleViewDepth = fetchViewDepth(samplePixelPos);

            float depthSigmaBias = mix(0.0005, 0.0030, uEdgeTolerance);
            float depthSigmaScale = mix(0.0010, 0.0060, uEdgeTolerance);
            float depthSigma = max(depthSigmaBias, depthSigmaScale * max(centerViewDepth, EPS));
            float depthDelta = abs(sampleViewDepth - centerViewDepth);
            float depthWeight = exp(-(depthDelta * depthDelta) / max(depthSigma * depthSigma, 1e-12));

            float sigmaSharp = 0.5;
            float sigmaBlur = 2.5;
            float spatialSigma = exp2(mix(log2(sigmaSharp), log2(sigmaBlur), uUpsampleBlur));
            vec2 offsetVolume = vec2(volumeSamplePos - volumePixelPosCenter);
            float radiusSquared = dot(offsetVolume, offsetVolume);
            float spatialSigmaSquared = max(spatialSigma * spatialSigma, 1e-12);
            float spatialWeight = exp(-radiusSquared / spatialSigmaSquared);

            float weight = depthWeight * spatialWeight;
            if (weight <= 0.0)
                continue;

            sumColor += weight * volumeSample;
            sumWeight += weight;
        }
    }

    if (sumWeight <= 1e-6)
    {
        outColor = texelFetch(uVolumeTex, volumePixelPosCenter, 0).rgb;
        return;
    }

    outColor = sumColor / sumWeight;
}
