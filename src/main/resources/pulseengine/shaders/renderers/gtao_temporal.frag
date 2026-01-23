#version 330 core

// -----------------------------------------------------
//  Temporal accumulation for Ambient Occlusion
//  Reduces AO noise and flicker by blending the current frame with a reprojected AO value from the previous frame.
//  Works as folows:
//    1) Reproject: find where this pixel was in the previous frame (using depth + matrices).
//    2) Validate: only reuse history if the previous depth matches the current surface.
//    3) Clamp: keep history from drifting too far from current neighborhood (reduces ghosting).
//    4) Blend: mix current AO with history AO using uTemporalFeedback.
// -----------------------------------------------------

out float outAO;

// Input textures
uniform sampler2D uAoCurrent;
uniform sampler2D uAoHistory;
uniform sampler2D uDepthCurrent;
uniform sampler2D uDepthPrev;

// Camera matrices
uniform mat4 uInvProj;
uniform mat4 uInvView;
uniform mat4 uPrevInvProj;
uniform mat4 uPrevViewProj;

// Sizes
uniform ivec2 resolution;

// Temporal controls
uniform float uTemporalFeedback;     // (0..1) Higher = steadier, more ghost risk.
uniform float uHistoryClampStrength; // (0..1) Higher = stronger clamp.

// Depth history validation controls
const float kDepthThresholdScale = 0.01f;
const float kDepthThresholdBias  = 0.01f;

const float kEps = 1e-6;

// -----------------------------------------------------
// Helpers
// -----------------------------------------------------

vec2 pixelToUv(ivec2 pixelPos)
{
    return (vec2(pixelPos) + 0.5) / vec2(resolution);
}

float fetchDepth01(sampler2D depthTex, ivec2 pixelPos)
{
    pixelPos = clamp(pixelPos, ivec2(0), resolution - ivec2(1));
    return texelFetch(depthTex, pixelPos, 0).r;
}

vec3 reconstructViewPosition(mat4 invProj, vec2 uv, float depth01)
{
    vec3 ndc = vec3(uv, depth01) * 2.0 - 1.0;
    vec4 clip = vec4(ndc, 1.0);
    vec4 view = invProj * clip;
    return view.xyz / max(view.w, 1e-8);
}

float depthTolerance(float centerViewZ)
{
    // Depth tolerance for "is this the same surface?".
    // The tolerance increases with distance because depth becomes less stable in perspective.
    return kDepthThresholdBias + kDepthThresholdScale * centerViewZ;
}

// Compute a local mean and standard deviation of current AO in a 3x3 neighborhood,
// but only include neighbors that are on roughly the same surface (depth-aware).
// This is used to clamp history AO so it doesn't drift far away from what the current
// frame believes is reasonable at this pixel.
void computeAoStats3x3SameSurface(ivec2 centerPixelPos, float centerDepth01, out float meanAO, out float sigmaAO)
{
    vec3 centerViewPos = reconstructViewPosition(uInvProj, pixelToUv(centerPixelPos), centerDepth01);
    float centerViewZ = max(abs(centerViewPos.z), kEps);

    float zTolerance = depthTolerance(centerViewZ);

    float sum = 0.0;
    float weightSum = 0.0;
    float sumSquared = 0.0;

    for (int oy = -1; oy <= 1; ++oy)
    {
        for (int ox = -1; ox <= 1; ++ox)
        {
            ivec2 pixelPos = clamp(centerPixelPos + ivec2(ox, oy), ivec2(0), resolution - ivec2(1));

            float depth = texelFetch(uDepthCurrent, pixelPos, 0).r;
            if (depth >= 1.0) continue;

            vec3 viewPos = reconstructViewPosition(uInvProj, pixelToUv(pixelPos), depth);
            float viewZ  = max(abs(viewPos.z), kEps);

            // Only accept neighbors that are at a similar distance from the camera.
            if (abs(viewZ - centerViewZ) > zTolerance) continue;

            float ao = texelFetch(uAoCurrent, pixelPos, 0).r;

            sum += ao;
            sumSquared += ao * ao;
            weightSum += 1.0;
        }
    }

    if (weightSum < 1e-5)
    {
        meanAO  = texelFetch(uAoCurrent, centerPixelPos, 0).r;
        sigmaAO = 0.0;
        return;
    }

    meanAO = sum / weightSum;

    // Standard deviation of AO values in the neighborhood.
    float var = max(sumSquared / weightSum - meanAO * meanAO, 0.0);
    sigmaAO = sqrt(var);
}

// -----------------------------------------------------
// Main
// -----------------------------------------------------

void main()
{
    ivec2 pixelPos = ivec2(gl_FragCoord.xy);
    
    float aoCurrent = texelFetch(uAoCurrent, pixelPos, 0).r;
    float depthCurrent = fetchDepth01(uDepthCurrent, pixelPos);

    // No geometry: output open AO and reset history.
    if (depthCurrent >= 1.0)
    {
        outAO = 1.0;
        return;
    }

    // Reproject world position into previous frame clip space
    vec3 viewPosCurrent = reconstructViewPosition(uInvProj, pixelToUv(pixelPos), depthCurrent);
    vec4 worldPos = uInvView * vec4(viewPosCurrent, 1.0);
    vec4 prevClip = uPrevViewProj * worldPos;

    bool historyValid = true;

    // Behind camera / invalid projection
    if (prevClip.w <= 1e-6) historyValid = false;

    // Previous UV
    vec3 prevNdc = prevClip.xyz / max(prevClip.w, 1e-6);
    vec2 uvPrev  = prevNdc.xy * 0.5 + 0.5;

    // No valid history if outside screen 
    if (any(lessThan(uvPrev, vec2(0.0))) || any(greaterThan(uvPrev, vec2(1.0))))
        historyValid = false;

    float aoHistory = 1.0;

    // Validate history via depth (disocclusion test)
    if (historyValid)
    {
        ivec2 prevPixelPos = ivec2(uvPrev * vec2(resolution));
        float depthPrev = fetchDepth01(uDepthPrev, prevPixelPos);
        
        if (depthPrev >= 1.0)
        {
            historyValid = false;
        }
        else
        {
            // Compare view-space distance (Z) between current and previous surface.
            vec3 viewPosPrev = reconstructViewPosition(uPrevInvProj, pixelToUv(prevPixelPos), depthPrev);
            float prevViewZ = max(abs(viewPosPrev.z), kEps);
            float currentViewZ = max(abs(viewPosCurrent.z), kEps);
            
            float zDelta = abs(prevViewZ - currentViewZ);
            float zTolerance = depthTolerance(currentViewZ);

            if (zDelta > zTolerance) historyValid = false;
        }

        // Sample history AO if still valid
        if (historyValid) aoHistory = texture(uAoHistory, uvPrev).r;
    }

    // Clamp history toward current neighborhood to reduce ghosting
    if (historyValid && uHistoryClampStrength > 0.0)
    {
        float meanAO, sigmaAO;
        computeAoStats3x3SameSurface(pixelPos, depthCurrent, meanAO, sigmaAO);

        // "k" controls how wide the allowed history range is.
        // Lower = less ghosting, but more noise / less temporal stability.
        const float k = 2.0;

        float minAO = meanAO - k * sigmaAO;
        float maxAO = meanAO + k * sigmaAO;

        float historyClamped = clamp(aoHistory, minAO, maxAO);

        aoHistory = mix(aoHistory, historyClamped, clamp(uHistoryClampStrength, 0.0, 1.0));
    }

    // If history isn't valid, we don't blend it in at all (feedback = 0).
    float historyWeight = historyValid ? clamp(uTemporalFeedback, 0.0, 1.0) : 0.0;

    // Final AO: blend current with (reprojected, validated, clamped) history.
    outAO = clamp(mix(aoCurrent, aoHistory, historyWeight), 0.0, 1.0);
}