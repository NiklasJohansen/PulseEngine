#version 330 core

#define TAU 6.28318530718
#define PI 3.14159265359
#define LOCAL 0.0
#define GLOBAL 1.0
#define NO_HIT -1.0
#define MIN_STEP 0.01

in vec2 uv;

out vec4 fragColor;

uniform sampler2D localSceneTex;
uniform sampler2D localMetadataTex;
uniform sampler2D globalSceneTex;
uniform sampler2D globalMetadataTex;
uniform sampler2D localSdfTex;
uniform sampler2D globalSdfTex;
uniform sampler2D upperCascadeTex;

uniform vec2 localSceneRes;
uniform vec2 globalSceneRes;
uniform float localSceneScale;
uniform float globalSceneScale;
uniform vec2 resolution;
uniform vec2 invResolution;
uniform vec4 skyColor;
uniform vec4 sunColor;
uniform float sunAngle;
uniform float sunDistance;
uniform float cascadeIndex;
uniform float cascadeCount;
uniform bool bilinearFix;
uniform float intervalLength;
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

vec4 raymarch(vec2 rayPos, vec2 rayDir, float rayLen)
{
    float localToGlobalScale = globalSceneScale / localSceneScale;
    vec2 sceneRes = localSceneRes;
    float traveledDist = 0.0;
    float stepSizeScale = 1.0;
    float space = LOCAL;
    int steps = 0;

    // Transform to local scene space (scene may have a different resolution than RC texture)
    rayPos *= localSceneScale;
    rayLen *= localSceneScale;

    while (steps < maxSteps && traveledDist < rayLen)
    {
        float stepSize = max(0.0, texture(space == LOCAL ? localSdfTex : globalSdfTex, rayPos / sceneRes).r);
        vec2 samplePos = rayPos + rayDir * stepSize;

        if (samplePos.x <= 0.0 || samplePos.x >= sceneRes.x || samplePos.y <= 0.0 || samplePos.y >= sceneRes.y)
        {
            if (traceWorldRays && space == LOCAL && cascadeIndex > 1)
            {
                rayPos = (rayPos - camOrigin * sceneRes) * invWorldScale + camOrigin * sceneRes; // Remap position to global space
                rayPos *= localToGlobalScale;
                rayLen *= localToGlobalScale;
                traveledDist *= localToGlobalScale;
                stepSizeScale = worldScale;
                sceneRes = globalSceneRes;
                space = GLOBAL;
                continue;
            }
            else break;
        }

        if (stepSize <= MIN_STEP)
            return vec4(samplePos / sceneRes, space, steps); // Hit

        rayPos = samplePos;
        traveledDist += stepSize * stepSizeScale;
        steps++;
    }

    return vec4(0, 0, NO_HIT, steps); // No hit
}

vec4 sampleScene(float space, vec2 originPos, vec2 hitPosUv, vec2 rayDir)
{
    vec4 color = texture(space == LOCAL ? localSceneTex : globalSceneTex, hitPosUv);
    vec4 metadata = texture(space == LOCAL ? localMetadataTex : globalMetadataTex, hitPosUv);
    float intensity = metadata.b;
    float radius = metadata.a;
    float coneAngle = metadata.r * PI;

    if (coneAngle < PI) // Directional lights
    {
        float sourceDir = metadata.g * TAU;
        vec2 coneDir = vec2(cos(sourceDir + camAngle), sin(sourceDir + camAngle));
        float dotK = max(dot(coneDir, -rayDir), 0.0);
        float d = cos(coneAngle);
        color.rgb *= clamp((dotK - d), 0, 1);
    }

    if (radius > 0.0) // Lights with radius
    {
        vec2 hitPos = (space == LOCAL) ? hitPosUv : (hitPosUv - camOrigin) * worldScale + camOrigin;
        float dist = distance(hitPos * (space == LOCAL ? localSceneRes : globalSceneRes), originPos);
        color.rgb *= clamp((radius * camScale) / (dist * dist), 0.0, 1.0);
    }

    return vec4(color.rgb * intensity, color.a);
}

vec4 getRadiance(vec2 probeCenter, vec2 rayStart, vec2 rayEnd)
{
    float rayLen = distance(rayStart, rayEnd);
    vec2 rayDir = (rayEnd - rayStart) / rayLen;
    vec4 result = raymarch(rayStart, rayDir, rayLen);
    float space = result.z; // 0=local, 1=global, -1=no hit

    if (space == NO_HIT)
        return vec4(0, 0, 0, result.w == maxSteps ? -1 : 0); // No hit, use a=-1 if max steps reached (prevent merge and sun/sky radiance)

    vec4 sceneColor = sampleScene(space, probeCenter, result.xy, rayDir);

    // Fade out global radiance at the edge of the world to prevent popping when lights go out of global view
    sceneColor.rgb *= 1.0 - space * smoothstep(0.45, 0.5, max(abs(result.x - 0.5), abs(result.y - 0.5)));

    return sceneColor;
}

vec4 fetchUpperCascadeRadiance(float rayIndex, vec2 upperProbeIndex)
{
    // The index of the cascade above the current one
    float upperCascadeIndex = floor(cascadeIndex + 1.0);

    // The amount of space between each probe in the upper cascade (2px in c1, 4px in c2, 16px in c3, etc.)
    float probeSpacing = pow(sqrtBase, upperCascadeIndex); // NOW: 2px

    // The width/height of each group in pixels
    vec2 probeGroupSize = floor(resolution / probeSpacing);

    // The x/y index of the probe group
    vec2 probeGroupIndex = vec2(mod(rayIndex, probeSpacing), floor(rayIndex / probeSpacing));

    // The bottom left position (in pixels) within the probe group
    vec2 probeGroupBottomLeftPos = probeGroupIndex * probeGroupSize;

    // The probe index clamped to the group size to avoid sampling from other groups
    vec2 upperProbeIndexClamped = clamp(upperProbeIndex + 0.5, vec2(0.5), probeGroupSize - 0.5);

    // The x/y position of the probe within the group in uv space
    vec2 samplePosUV = (probeGroupBottomLeftPos + upperProbeIndexClamped) * invResolution;

    // Sample the upper cascade texture
    return texture(upperCascadeTex, samplePosUV);
}

vec4 merge(vec4 currentRadiance, float rayIndex, vec2 upperProbeIndex)
{
    // If the current ray did not hit anything, we sample the ray in the cascade above this one (upper cascade)
    if (mergeCascades && currentRadiance.a == 0.0 && cascadeIndex < cascadeCount - 1.0)
    {
        // Fetch the radiance from the upper cascade and merges it with the radiance from the current ray
        currentRadiance += fetchUpperCascadeRadiance(rayIndex, upperProbeIndex);
    }
    return currentRadiance;
}

void main()
{
    // The total amount of rays per probe accross the whole cascade (4 in c0, 16 in c1 (4 per probe * 4 probe groups), etc.)
    float rayCount = pow(baseRayCount, cascadeIndex + 1.0);

    // The amount of radians to increment the angle by for each ray
    float angleStepSize = TAU / rayCount;

    // The distance from probe center to where the ray starts and ends in screen space
    float d = pow(baseRayCount, cascadeIndex);
    float intervalStart = intervalLength * (1.0 - d) / (1.0 - baseRayCount);
    float intervalEnd = intervalStart + intervalLength * d;

    // The current position in pixel space
    vec2 screenPos = floor(uv * resolution);

    // The amount of space between each probe (1px in c0, 2px in c1, 4px in c2, etc.)
    float probeSpacing = pow(sqrtBase, cascadeIndex);

    // The width/height of each probe group in pixels (probe group = layout of all rays from a singel probe)
    vec2 probeGroupSize = floor(resolution / probeSpacing);

    // The x/y index of the probe group
    vec2 probeGroupIndex = floor(screenPos / probeGroupSize);

    // The x/y index of the probe
    vec2 probeIndex = mod(screenPos, probeGroupSize);

    // The x/y position of the probe
    vec2 probeCenterPos = (probeIndex + 0.5) * probeSpacing;

    // The 1D index of the first ray in the current probe group (0 in c0, 0-3 in c1, etc.)
    float baseRayIndex = baseRayCount * (probeGroupIndex.x + (probeSpacing * probeGroupIndex.y));

    // The spacing between probes in the upper cascade (2px in c1, 4px in c2, etc.)
    float upperProbeSpacing = pow(sqrtBase, cascadeIndex + 1.0);

    // Calculate weights and indices for bilinear sampling
    vec2 upperProbeIndex = floor((probeIndex - 1.0) / sqrtBase);
    vec2 upperBillinearProbeIndecies[4] = vec2[4](
        upperProbeIndex + vec2(0, 0),
        upperProbeIndex + vec2(1, 0),
        upperProbeIndex + vec2(0, 1),
        upperProbeIndex + vec2(1, 1)
    );
    vec2 probeIndexFloored = floor((upperProbeIndex * sqrtBase) + 1.0);
    vec2 weight = vec2(0.25) + 0.5 * (probeIndex - probeIndexFloored);

    // The total radiance for the current probe
    vec3 totalProbeRadiance = vec3(0.0);

    // Gather radiance from each ray of the current probe
    for (int i = 0; i < int(baseRayCount); i++)
    {
        // Ray angle based on the index of the current ray (in range 0 - 3 in c0, 0 - 15 in c1, etc.)
        float rayIndex = baseRayIndex + float(i);
        float rayAngle = (rayIndex + 0.5) * angleStepSize - camAngle;
        vec2 dir = vec2(cos(rayAngle), -sin(rayAngle));
        vec2 rayStart = probeCenterPos + dir * intervalStart;
        vec4 deltaRadiance = vec4(0.0);

        if (bilinearFix)
        {
            vec4 rad[4];
            for (int j = 0; j < 4; j++)
            {
                vec2 upperProbeIndex = upperBillinearProbeIndecies[j];
                vec2 upperProbeCenterPos = (upperProbeIndex + 0.5) * upperProbeSpacing;
                vec2 rayEnd = upperProbeCenterPos + dir * intervalEnd;
                vec4 radiance = getRadiance(probeCenterPos, rayStart, rayEnd);
                rad[j] = merge(radiance, rayIndex, upperProbeIndex);
            }

            deltaRadiance = mix(mix(rad[0], rad[1], weight.x), mix(rad[2], rad[3], weight.x), weight.y);
        }
        else
        {
            vec2 upperProbeIndex = (probeIndex - 0.5) / sqrtBase;
            vec2 rayEnd   = probeCenterPos + dir * intervalEnd;
            vec4 radiance = getRadiance(probeCenterPos, rayStart, rayEnd);
            deltaRadiance = merge(radiance, rayIndex, upperProbeIndex);
        }

        // Mix in the sun and sky radiance if we're in the outermost cascade
        if (deltaRadiance.a == 0.0 && cascadeIndex == cascadeCount - 1.0)
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
    fragColor = vec4(totalProbeRadiance / baseRayCount, 1.0);
}