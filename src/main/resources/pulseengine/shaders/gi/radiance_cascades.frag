#version 420 core
#define TAU 6.28318530718
#define PI 3.14159265359
#define SRGB 2.2

in vec2 uv;
out vec4 fragColor;

layout(binding=0) uniform sampler2D sceneTexture;
layout(binding=1) uniform sampler2D sceneMedatadaTexture;
layout(binding=2) uniform sampler2D sceneSdf;
layout(binding=3) uniform sampler2D upperCascadeTexture;

uniform vec4 skyColor;
uniform vec4 sunColor;
uniform float sunAngle;
uniform float sunDistance;
uniform vec2 resolution;
uniform float minStepSize;
uniform float cascadeIndex;
uniform float cascadeCount;
uniform bool bilinearFix;
uniform bool forkFix;
uniform float intervalLength;

const float baseRayCount = 4.0;
const float sqrtBase = sqrt(baseRayCount);

vec4 raymarch(vec2 startPos, vec2 endPos)
{
    vec2 pos = startPos;
    float rayLength = distance(startPos, endPos);
    vec2 rayDir = (endPos - startPos) / rayLength;

    // Ray march until we hit a surface or reach the end of the interval
    for (float traveledDist = 0; traveledDist < rayLength;)
    {
        // Move the ray along its direction by the distance to the closest non-empty space
        float dist = texture(sceneSdf, pos).r;
        pos += dist * rayDir;
        traveledDist += dist;

        // Stop if we're out of bounds
        if (pos.x < 0.0 || pos.x > 1.0 || pos.y < 0.0 || pos.y > 1.0)
            break;

        // Return the radiance if we're close enough to the surface
        if (dist <= minStepSize)
        {
            // Sample the scene texture at the hit position
            vec4 radiance = texture(sceneTexture, pos);
            vec4 metadata = texture(sceneMedatadaTexture, pos);
            float coneAngle = metadata.r * PI;

            if (coneAngle < PI)
            {
                float sourceAngle = metadata.g * TAU;
                vec2 coneDir = vec2(cos(sourceAngle), sin(sourceAngle));
                float dotK = max(dot(coneDir, -rayDir), 0.0);
                float d = cos(coneAngle);
                radiance.rgb *= clamp((dotK - d), 0, 1);
            }

            radiance.rgb *= metadata.b; // sourceIntensity

            // Convert the color to linear space
            return vec4(pow(radiance.rgb, vec3(SRGB)), radiance.a);
        }
    }

    return vec4(0); // No hit
}

vec4 fetchUpperCascadeRadiance(float rayIndex, vec2 probeIndex)
{
    // The index of the cascade above the current one
    float upperCascadeIndex = cascadeIndex + 1.0;

    // The amount of space between each probe in the upper cascade (2px in c1, 4px in c2, 16px in c3, etc.)
    float probeSpacing = pow(sqrtBase, upperCascadeIndex);

    // The width/height of each quadrant in pixeles
    vec2 quadrantSize = floor(resolution / probeSpacing);

    // The x/y index of the quadrant
    vec2 quadrantIndex = vec2(mod(rayIndex, probeSpacing), floor(rayIndex / probeSpacing));

    // The top left position (in pixels) within the quadrant
    vec2 quadrantTopLeftPos = quadrantIndex * quadrantSize;

    // The x/y index of the probe within the upper cascade quadrant
    vec2 upperProbeIndex = (probeIndex + 0.5) / sqrtBase;

    // The probe index clamped to the quadrant size to avoid sampling from other quadrants
    vec2 probeIndexClamped = clamp(upperProbeIndex, vec2(0.5), quadrantSize - 0.5);

    // The x/y position of the probe within the quadrant in uv space
    vec2 probePosUV = (quadrantTopLeftPos + probeIndexClamped) / resolution;

    // Sample the cascade texture
    return texture(upperCascadeTexture, probePosUV);
}

vec4 merge(vec4 currentRadiance, float rayIndex, vec2 probeIndex)
{
    // If the current ray did not hit anything, we sample the ray in the cascade above this one (upper cascade)
    if (currentRadiance.a == 0.0 && cascadeIndex < cascadeCount - 1.0)
    {
        // Fetch the radiance from the upper cascade and merges it with the radiance from the current ray
        currentRadiance += fetchUpperCascadeRadiance(rayIndex, probeIndex);
    }
    return currentRadiance;
}

void main()
{
    bool isInnermostCascade = (cascadeIndex == 0.0);
    bool isOutermostCascade = (cascadeIndex == cascadeCount - 1.0);

    // Determine scaling factor to keep the rays consistent across different screen ratios
    float shortestSide = min(resolution.x, resolution.y);
    vec2 scale = shortestSide / resolution;

    // The total amount of rays per probe accross the whole cascade (4 in c0, 16 in c1 (4 per probe * 4 quadrants), etc.)
    float rayCount = pow(baseRayCount, cascadeIndex + 1.0);

    // The amount of radians to increment the angle by for each ray
    float angleStepSize = TAU / rayCount;

    // The distance from probe center to where the ray starts in uv space
    float intervalStart = intervalLength * (isInnermostCascade ? 0.0 : pow(baseRayCount, cascadeIndex - 1.0)) / shortestSide;

    // The distance from probe center to where the ray ends in uv space
    float intervalEnd = intervalLength * pow(baseRayCount, cascadeIndex) / shortestSide;

    // The amount of space between each probe (1px in c0, 2px in c1, 4px in c2, etc.)
    float probeSpacing = pow(sqrtBase, cascadeIndex);

    // The current position in pixel space
    vec2 screenPos = floor(uv * resolution);

    // The width/height of each quadrant in pixel space
    vec2 quadrantSize = floor(resolution / probeSpacing);

    // The x/y index of the probe within the current quadrant
    vec2 probeIndex = mod(screenPos, quadrantSize);

    // The x/y position of the probe
    vec2 probeCenterPos = (probeIndex + 0.5) * probeSpacing;
    vec2 probeCenterPosUV = probeCenterPos / resolution;

    // The x/y index of the current quadrant
    vec2 quadrantIndex = floor(screenPos / quadrantSize);

    // The 1D index of the first ray in the current quadrant (0 in c0, 0-3 in c1, etc.)
    float baseRayIndex = baseRayCount * (quadrantIndex.x + (probeSpacing * quadrantIndex.y));

    // The total radiance for the current probe
    vec4 totalProbeRadiance = vec4(0.0);

    // Gather radiance from each ray of the current probe
    for (int i = 0; i < int(baseRayCount); i++)
    {
        vec4 deltaRadiance = vec4(0.0);

        // Ray angle based on the index of the current ray (in range 0 - 3 in c0, 0 - 15 in c1, etc.)
        float rayIndex = baseRayIndex + float(i);
        float rayAngle = (rayIndex + 0.5) * angleStepSize;
        vec2 endDir = vec2(cos(rayAngle), -sin(rayAngle)) * scale;
        vec2 startDir = endDir;

        if (forkFix)
        {
            float forkRayAngle = (floor(baseRayIndex * 0.25) + 0.5) * angleStepSize * 4;
            startDir = vec2(cos(forkRayAngle), -sin(forkRayAngle)) * scale;
        }

        if (bilinearFix)
        {
            vec4 radiances[4] = vec4[4](vec4(0), vec4(0), vec4(0), vec4(0));

            for (int j = 0; j < 4; j++)
            {
                vec2 offset = vec2(j % 2, j / 2) * 2.0 - 1.0;
                vec2 upperProbeIndex = probeIndex + offset * sqrtBase;
                vec2 upperProbePos = upperProbeIndex * probeSpacing;
                vec2 upperProbePosClamped = clamp(upperProbePos, vec2(0), resolution);
                vec2 upperProbeCenterPosUv = upperProbePosClamped / resolution;

                vec2 rayStart = probeCenterPosUV + startDir * intervalStart;
                vec2 rayEnd = upperProbeCenterPosUv + endDir * (intervalStart + intervalEnd);
                vec4 radiance = raymarch(rayStart, rayEnd);
                radiances[j] = merge(radiance, rayIndex, upperProbeIndex);
            }

            vec2 weight = fract(probeCenterPos / probeSpacing);
            deltaRadiance = mix(mix(radiances[0], radiances[1], weight.x), mix(radiances[2], radiances[3], weight.x), weight.y);
        }
        else
        {
            vec2 rayStart = probeCenterPosUV + intervalStart * startDir;
            vec2 rayEnd = probeCenterPosUV + intervalEnd * endDir;
            vec4 radiance = raymarch(rayStart, rayEnd);
            deltaRadiance = merge(radiance, rayIndex, probeIndex);
        }

        // Mix in the sun and sky radiance if we're in the outermost cascade
        if (isOutermostCascade)
        {
            float angleToSun = mod(rayAngle - sunAngle, TAU);
            float sunIntensity = pow(max(0.0, cos(angleToSun)), 4.0 / sunDistance);
            vec3 sunAndSkyRandiance = mix(sunColor.rgb * sunIntensity, skyColor.rgb, 0.3);
            deltaRadiance.rgb = max(sunAndSkyRandiance, deltaRadiance.rgb);
        }

        // Accumulate the radiance from this ray
        totalProbeRadiance += deltaRadiance;
    }

    // Divide by the amount of rays to get the average incoming radiance
    vec3 avgRadiance = totalProbeRadiance.rgb / baseRayCount;

    // Convert to sRGB if we're in the inner most cascade
    fragColor = vec4(isInnermostCascade ? pow(avgRadiance.rgb, vec3(1.0 / SRGB)) : avgRadiance.rgb, 1.0);
}