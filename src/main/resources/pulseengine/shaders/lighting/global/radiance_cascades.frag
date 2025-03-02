#version 330 core
#define TAU 6.28318530718
#define PI 3.14159265359
#define SCREEN 0.0
#define WORLD 1.0
#define NO_HIT -1.0

in vec2 uv;
out vec4 fragColor;

uniform sampler2D localSceneTex;
uniform sampler2D localMetadataTex;
uniform sampler2D globalSceneTex;
uniform sampler2D globalMetadataTex;
uniform sampler2D localSdfTex;
uniform sampler2D globalSdfTex;
uniform sampler2D upperCascadeTex;

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
uniform float intervalOverlap;
uniform float worldScale;
uniform float invWorldScale;
uniform bool traceWorldRays;
uniform bool mergeCascades;
uniform int maxSteps;
uniform float camAngle;
uniform float camScale;
uniform vec2 camOrigin;

const float baseRayCount = 4.0;
const float sqrtBase = sqrt(baseRayCount);
const float sdfDecodeScale = sqrt(2.0) / 65000.0;

vec4 raymarch(vec2 rayPos, vec2 rayDir, float rayLen)
{
    float traveledDist = 0.0;
    float stepSizeScale = 1.0;
    float space = SCREEN;
    int steps = 0;

    while (steps < maxSteps && traveledDist < rayLen)
    {
        float fieldDist = texture(space == SCREEN ? localSdfTex : globalSdfTex, rayPos).r;
        float stepSize = max(fieldDist, 0.0) * sdfDecodeScale;
        vec2 samplePos = rayPos + rayDir * stepSize;

        if (samplePos.x < 0.0 || samplePos.x > 1.0 || samplePos.y < 0.0 || samplePos.y > 1.0)
        {
            if (traceWorldRays && space == SCREEN)
            {
                rayPos = (rayPos - camOrigin) * invWorldScale + camOrigin; // Remap position to world space
                stepSizeScale = worldScale;
                space = WORLD;
                continue;
            }
            else break;
        }

        if (stepSize <= minStepSize)
            return vec4(samplePos, space, steps); // Hit

        rayPos = samplePos;
        traveledDist += stepSize * stepSizeScale;
        steps++;
    }

    return vec4(0, 0, NO_HIT, steps); // No hit
}

vec4 traceRay(vec2 probeCenter, vec2 rayStart, vec2 rayEnd)
{
    float rayLen = distance(rayStart, rayEnd);
    vec2 rayDir = (rayEnd - rayStart) / rayLen;
    vec4 result = raymarch(rayStart, rayDir, rayLen);
    float space = result.z; // 0=screen, 1=world, -1=no hit

    if (space == NO_HIT)
        return vec4(0, 0, 0, result.w == maxSteps ? -1 : 0); // No hit, use a=-1 if max steps reached (prevent merge and sun/sky radiance)

    vec4 scene = texture(space == SCREEN ? localSceneTex : globalSceneTex, result.xy);
    vec4 metadata = texture(space == SCREEN ? localMetadataTex : globalMetadataTex, result.xy);
    float sourceIntensity = metadata.b;
    float sourceRadius = metadata.a;
    float coneAngle = metadata.r * PI;

    // Directional lights
    if (coneAngle < PI)
    {
        float sourceAngle = camAngle + metadata.g * TAU;
        vec2 coneDir = vec2(cos(sourceAngle), sin(sourceAngle));
        float dotK = max(dot(coneDir, -rayDir), 0.0);
        float d = cos(coneAngle);
        scene.rgb *= clamp((dotK - d), 0, 1);
    }

    // Lights with radius
    if (sourceRadius > 0.0)
    {
        vec2 hitPos = (space == SCREEN) ? result.xy : (result.xy - vec2(0.5)) * worldScale + vec2(0.5);
        float dist = length((hitPos - probeCenter) * resolution);
        float radius = 0.2 * sourceRadius * camScale;
        scene.rgb *= clamp(radius / (dist * dist), 0.0, 1.0);
    }

    // Fade out radiance at the edge of the world to prevent popping when lights go out of global view
    float edgeFade = 1.0 - space * smoothstep(0.45, 0.5, max(abs(result.x - 0.5), abs(result.y - 0.5)));

    return vec4(scene.rgb * sourceIntensity * edgeFade, scene.a);
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

    // Sample the upper cascade texture
    return texture(upperCascadeTex, probePosUV);
}

vec4 merge(vec4 currentRadiance, float rayIndex, vec2 probeIndex)
{
    // If the current ray did not hit anything, we sample the ray in the cascade above this one (upper cascade)
    if (mergeCascades && currentRadiance.a == 0.0 && cascadeIndex < cascadeCount - 1.0)
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
    float probeSpacingUpperCascade = pow(sqrtBase, cascadeIndex + 1.0);
    float overlap = intervalOverlap * probeSpacingUpperCascade * sqrtBase * (isInnermostCascade ? 0.1 : 1.0);
    float intervalEnd = intervalLength * (pow(baseRayCount, cascadeIndex) + overlap) / shortestSide;

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
    vec3 totalProbeRadiance = vec3(0.0);

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
                vec2 rayEnd = upperProbeCenterPosUv + endDir * intervalEnd;
                vec4 radiance = traceRay(probeCenterPosUV, rayStart, rayEnd);
                radiances[j] = merge(radiance, rayIndex, upperProbeIndex);
            }

            vec2 weight = fract(probeCenterPos / probeSpacing);
            deltaRadiance = mix(mix(radiances[0], radiances[1], weight.x), mix(radiances[2], radiances[3], weight.x), weight.y);
        }
        else
        {
            vec2 rayStart = probeCenterPosUV + intervalStart * startDir;
            vec2 rayEnd = probeCenterPosUV + intervalEnd * endDir;
            vec4 radiance = traceRay(probeCenterPosUV, rayStart, rayEnd);
            deltaRadiance = merge(radiance, rayIndex, probeIndex);
        }

        // Mix in the sun and sky radiance if we're in the outermost cascade
        if (isOutermostCascade && deltaRadiance.a == 0.0)
        {
            float angleToSun = mod(rayAngle - (sunAngle - camAngle), TAU);
            float sunIntensity = pow(max(0.0, cos(angleToSun)), 4.0 / sunDistance);
            vec3 sunAndSkyRandiance = mix(sunColor.rgb * sunIntensity, skyColor.rgb, 0.3);
            deltaRadiance.rgb = max(sunAndSkyRandiance, deltaRadiance.rgb);
        }

        // Accumulate the radiance from this ray
        totalProbeRadiance += deltaRadiance.rgb;
    }

    // Divide by the amount of rays to get the average incoming radiance
    fragColor = vec4(totalProbeRadiance.rgb / baseRayCount, 1.0);
}