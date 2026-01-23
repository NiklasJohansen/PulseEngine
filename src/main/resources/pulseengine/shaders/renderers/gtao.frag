#version 330 core

//  --------------------------------------------
// GTAO implementation based on "Practical Realtime Strategies for Accurate Indirect Occlusion"
// https://www.activision.com/cdn/research/PracticalRealtimeStrategiesTRfinal.pdf
// --------------------------------------------

out float outAO;

// Depth buffer with mip pyramid (near=0, far=1) 
uniform sampler2D uDepthTex;

// Camera matrices
uniform mat4 uInvView;
uniform mat4 uInvProj;
uniform mat4 uProj;

// Resolutions and pyramid info
uniform ivec2 uDepthTexSize;      
uniform ivec2 uAoTexSize;        
uniform int   uDepthMaxMipIndex; 
uniform int   uDownsampleFactor;

// AO controls
uniform int   uNumSlices;        // Number of slice directions
uniform int   uMaxSteps;         // Maximum number of steps per slice
uniform float uRadiusMeters;     // AO radius in meters
uniform float uMaxRadiusPixels;  // Upper clamp for sampling radius in full-res pixels
uniform float uThicknessMeters;  // Small bias to reduce self-occlusion

// --------------------------------------------

const float PI = 3.14159265358979323846;

const int kMinSteps  = 6; // Minimum radial steps per slice
const int kMaxSlices = 8; // Maximum number of slices

const float kThinOccluderSmoothing = 0.25; // Stabilizes horizon when a sample briefly "drops"
const float kPlaneThicknessMeters = 0.15;  // Used to decide "same surface" vs "real occluder"
const int   kMaxAoMip = 5;                 // Cap mip usage (avoids overly blurry horizons)

const float kCoarseNoiseCellMeters  = 0.005f; // Cell size of the coarse world-stable noise
const float kFineNoiseCellMeters    = 0.01f;  // Cell size of the fine world-stable noise
const float kNoiseAmount            = 0.2f;   // Amount of noise to add to angle/jitter

struct DepthSample
{
    float depth01;  // [0..1] depth from pyramid
    vec2  uv;       // Snapped UV that matches the fetched texel center
};

// --------------------------------------------
// Helpers
// --------------------------------------------

DepthSample sampleDepthPyramid(vec2 uv, int lod)
{
    ivec2 mipSize = max(uDepthTexSize >> lod, ivec2(1));
    ivec2 texel   = clamp(ivec2(uv * vec2(mipSize)), ivec2(0), mipSize - ivec2(1));

    DepthSample ds;
    ds.depth01 = texelFetch(uDepthTex, texel, lod).r;
    ds.uv      = (vec2(texel) + 0.5) / vec2(mipSize);

    return ds;
}

vec3 reconstructViewPosition(vec2 uv, float depth01)
{
    vec3 pNdc = vec3(uv, depth01) * 2.0 - 1.0;
    vec4 clip  = vec4(pNdc, 1.0);
    vec4 view  = uInvProj * clip;
    return view.xyz / max(view.w, 1e-8);
}

vec3 viewRayDirection(vec2 uv)
{
    vec4 clip = vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec4 view = uInvProj * clip;
    return view.xyz / max(view.w, 1e-8);
}

vec3 computeViewNormalFromDepth(ivec2 fullPix, vec2 uvCenter, float depthCenter, vec3 toCameraDir)
{
    ivec2 maxPix = uDepthTexSize - ivec2(1);

    ivec2 pL = max(fullPix + ivec2(-1, 0), ivec2(0));
    ivec2 pR = min(fullPix + ivec2( 1, 0), maxPix);
    ivec2 pD = max(fullPix + ivec2( 0,-1), ivec2(0));
    ivec2 pU = min(fullPix + ivec2( 0, 1), maxPix);

    float dL = texelFetch(uDepthTex, pL, 0).r;
    float dR = texelFetch(uDepthTex, pR, 0).r;
    float dD = texelFetch(uDepthTex, pD, 0).r;
    float dU = texelFetch(uDepthTex, pU, 0).r;

    vec2 invFull = 1.0 / vec2(uDepthTexSize);

    vec3 P  = reconstructViewPosition(uvCenter, depthCenter);
    vec3 PL = (dL < 1.0) ? reconstructViewPosition((vec2(pL) + 0.5) * invFull, dL) : P;
    vec3 PR = (dR < 1.0) ? reconstructViewPosition((vec2(pR) + 0.5) * invFull, dR) : P;
    vec3 PD = (dD < 1.0) ? reconstructViewPosition((vec2(pD) + 0.5) * invFull, dD) : P;
    vec3 PU = (dU < 1.0) ? reconstructViewPosition((vec2(pU) + 0.5) * invFull, dU) : P;

    // Pick the closest-in-depth neighbor on each axis (edge-friendly).
    float zC = P.z;
    vec3 PX = (abs(PL.z - zC) < abs(PR.z - zC)) ? PL : PR;
    vec3 PY = (abs(PD.z - zC) < abs(PU.z - zC)) ? PD : PU;

    vec3 dx = PX - P;
    vec3 dy = PY - P;
    vec3 N = cross(dx, dy);
    float nLen = length(N);

    // Fallback if geometry is degenerate.
    if (nLen < 1e-6)
    {
        vec3 dPdx = dFdx(P);
        vec3 dPdy = dFdy(P);
        N = cross(dPdx, dPdy);
        nLen = length(N);
        if (nLen < 1e-6) N = vec3(0.0, 0.0, 1.0);
    }

    N /= max(nLen, 1e-8);

    // Face the normal toward the camera to keep sign consistent
    if (dot(N, toCameraDir) < 0.0) N = -N;

    return N;
}

// Turns a visible angular range into a “visibility amount” for a slice.
// “given the open interval and the blocked interval, how visible is the hemisphere?”
float integrateVisibilityArc(float thetaLo, float thetaHi, float normalAngle)
{
    float c   = cos(normalAngle);
    float s   = sin(normalAngle);
    float aLo = 0.25 * (-cos(2.0 * thetaLo - normalAngle) + c + 2.0 * thetaLo * s);
    float aHi = 0.25 * (-cos(2.0 * thetaHi - normalAngle) + c + 2.0 * thetaHi * s);
    return aLo + aHi;
}

// --------------------------------------------
// World-stable random (hash) helpers
// --------------------------------------------

float hash01(ivec3 p)
{
    uint v = uint(p.x) * 1973u ^ uint(p.y) * 9277u ^ uint(p.z) * 26699u ^ 0x68bc21ebu;
    v ^= v >> 16;
    v *= 0x7feb352du;
    v ^= v >> 15;
    v *= 0x846ca68bu;
    v ^= v >> 16;
    return float(v) * (1.0 / 4294967296.0); // [0..1)
}

vec3 hash(ivec3 p)
{
    return vec3(hash01(p), hash01(p + ivec3(17, 59, 101)), hash01(p + ivec3(71, 23,  9)));
}

vec3 hash_forSlice(ivec3 cell, int sliceIndex, ivec3 salt)
{
    // sliceIndex+1 so slice 0 isn't identical to the base cell
    ivec3 p = cell + salt * (sliceIndex + 1);
    
    return vec3(hash01(p), hash01(p + ivec3(17, 59, 101)), hash01(p + ivec3(71, 23,  9)));
}

// --------------------------------------------
// Find the horizon angle in one direction along one slice
// --------------------------------------------

float findSignedHorizonAngle(
    ivec2 centerFullPix,
    vec2  centerUvFull,
    vec3  viewPos,
    vec3  viewNormal,
    vec3  toCameraDir,
    vec2  screenDir,          // 2D direction in screen pixel space (unit length)
    float radiusPixels,       // search radius in full-res pixels
    float radiusMeters,       // same radius in meters at this depth
    float directionSign,      // +1 or -1 (two sides of the slice)
    vec3  dViewPos_dx,        // local surface “tangent” estimate (meters per AO-pixel step)
    vec3  dViewPos_dy,
    float stepJitter01,       // random [0..1) to avoid banding
    vec3  random3             // per-slice random
) {
    vec2 invFull = 1.0 / vec2(uDepthTexSize);

    // Steps scale with radius: small radius = few samples, big radius = more.
    int steps = int(clamp(radiusPixels * 0.125, min(kMinSteps, uMaxSteps), uMaxSteps));

    float bestCos = 0.0; // cos(theta) of the best horizon so far
    float prevCos = 0.0;

    ivec2 lastOffset = ivec2(999999);
    
    for (int i = 1; i <= steps; ++i)
    {
        // Push more samples toward the outer radius
        float t    = clamp((float(i) - 0.5 + stepJitter01) / float(steps), 0.0, 1.0);
        float rPix = max(1.0, (t * t) * radiusPixels);

        ivec2 offset = ivec2(round(screenDir * rPix * directionSign));

        // Skip repeats (can happen due to rounding).
        if (all(equal(offset, ivec2(0))) || all(equal(offset, lastOffset))) continue;
            lastOffset = offset;

        ivec2 samplePix = centerFullPix + offset;
        if (samplePix.x < 0 || samplePix.y < 0 || samplePix.x >= uDepthTexSize.x || samplePix.y >= uDepthTexSize.y)
            break;

        vec2 sampleUv = (vec2(samplePix) + 0.5) * invFull;

        // Choose a mip level based on how far we are sampling. Farther samples use coarser mips.
        int lodIdeal = int(floor(log2(max(rPix, 1.0))));
        lodIdeal = clamp(lodIdeal, 0, uDepthMaxMipIndex);
        int lod = max(lodIdeal - 3, 0); // Bias toward sharper mips for better detail
        lod = min(lod, kMaxAoMip);
        if (rPix < 12.0) lod = 0; // Keep nearby samples crisp.

        ivec2 mipSize  = max(uDepthTexSize >> lod, ivec2(1));
        vec2  mipTexel = 1.0 / vec2(mipSize);

        // Tiny per-sample jitter inside the mip texel reduces repeating patterns.
        vec2 r2 = fract(random3.xy + vec2(0.37, 0.73) * (float(i) + 13.0 * stepJitter01));
        vec2 jitterUv = (r2 - 0.5) * 0.49 * mipTexel;

        vec2 uvMin = 0.5 * mipTexel;
        vec2 uvMax = 1.0 - 0.5 * mipTexel;
        DepthSample ds = sampleDepthPyramid(clamp(sampleUv + jitterUv, uvMin, uvMax), lod);

        float cosCandidate = 0.0;

        if (ds.depth01 < 1.0)
        {
            vec3 sampleViewPos = reconstructViewPosition(ds.uv, ds.depth01);
            vec3 delta         = sampleViewPos - viewPos;
            float distMeters   = length(delta);

            if (distMeters <= radiusMeters)
            {
                // --------------------------------------------
                // Self-occlusion control
                //
                // When sampling very close to the camera, we may hit the same surface we are standing on.
                // That creates fake AO darkening as it occludes itself.
                //
                // Fix idea:
                // 1) If the sample point looks like it lies on the same surface plane, we treat it as “maybe self”.
                // 2) We predict where the surface plane should be for that sample, and check if the sample is
                //    clearly in front of that predicted plane (real occluder) or not (likely self).
                // 3) We *fade* the contribution instead of hard rejecting it. This avoids edge artifacts.
                // --------------------------------------------

                // Thickness grows a bit when the local surface is steep relative to the view direction.
                float mipBlockSizeFull = float(1 << lod);
                float halfDiagAoPixels = 0.7071 * 0.5 * mipBlockSizeFull / float(uDownsampleFactor);

                float viewSlope = abs(dot(dViewPos_dx, toCameraDir)) + abs(dot(dViewPos_dy, toCameraDir));
                float thickness = max(uThicknessMeters, viewSlope * halfDiagAoPixels);

                // “Is the sample roughly on the same surface plane as the center?”
                float planeDist = abs(dot(sampleViewPos - viewPos, viewNormal));
                float sameSurfaceWeight = 1.0 - smoothstep(kPlaneThicknessMeters, 2.0 * kPlaneThicknessMeters, planeDist);

                // Predict where our surface would be at the sample UV using local tangents.
                vec2 deltaFullPix = (ds.uv - centerUvFull) * vec2(uDepthTexSize);
                vec2 deltaAoPix   = deltaFullPix / float(uDownsampleFactor);
                vec3 predictedPos = viewPos + dViewPos_dx * deltaAoPix.x + dViewPos_dy * deltaAoPix.y;

                // If the sample is not clearly in front of the predicted plane along the view direction,
                // it is probably self-occlusion and should be reduced.
                float inFrontAmount = dot(sampleViewPos - predictedPos, toCameraDir);
                float selfOcclusionFade = smoothstep(thickness, 2.0 * thickness, inFrontAmount);

                // Only apply self-occlusion fading when it actually looks like the same surface.
                float selfWeight = mix(1.0, selfOcclusionFade, sameSurfaceWeight);

                // Convert the sample into a horizon candidate.
                float towardCamera = dot(sampleViewPos - viewPos, toCameraDir);

                // Small bias to avoid micro self-shadowing.
                float towardBiased = max(0.0, towardCamera - thickness);

                cosCandidate = clamp(towardBiased / max(distMeters, 1e-6), 0.0, 1.0);
                cosCandidate *= selfWeight;

                // Smooth falloff with distance so far samples matter less.
                float x = distMeters / max(radiusMeters, 1e-6);
                cosCandidate *= exp(-2.0 * x * x);
            }
        }

        // "Thin occluder" stabilizer: if a sample briefly gives a weaker horizon than the previous sample,
        // blend it toward the previous result to reduce flicker (especially helpful in VR).
        if (cosCandidate < prevCos) cosCandidate = mix(prevCos, cosCandidate, kThinOccluderSmoothing);

        bestCos = max(bestCos, cosCandidate);
        prevCos = cosCandidate;
    }

    float horizonAngle = acos(clamp(bestCos, 0.0, 1.0));
    return directionSign * horizonAngle;
}

// --------------------------------------------
// Main
// --------------------------------------------

void main()
{
    ivec2 aoPix = ivec2(gl_FragCoord.xy);
    if (any(lessThan(aoPix, ivec2(0))) || any(greaterThanEqual(aoPix, uAoTexSize)))
    {
        outAO = 1.0;
        return;
    }

    // Map AO pixel to the full-res pixel near the center of the AO block.
    ivec2 fullPix = aoPix * uDownsampleFactor + ivec2(uDownsampleFactor / 2);
    fullPix = clamp(fullPix, ivec2(0), uDepthTexSize - ivec2(1));

    float depth01 = texelFetch(uDepthTex, fullPix, 0).r;
    if (depth01 >= 1.0)
    {
        outAO = 1.0;
        return;
    }

    vec2 invFull = 1.0 / vec2(uDepthTexSize);
    vec2 uvFull  = (vec2(fullPix) + 0.5) * invFull;

    vec3 viewPos = reconstructViewPosition(uvFull, depth01);

    // Direction from shading point to camera in view space (camera at origin).
    vec3 toCameraDir = -viewPos / max(length(viewPos), 1e-6);
    vec3 viewNormal = computeViewNormalFromDepth(fullPix, uvFull, depth01, toCameraDir);

    // --------------------------------------------
    // Estimate local tangents (used for self-occlusion prediction)
    //
    // We sample neighbors spaced by the AO stride. This is more stable than raw dFdx/dFdy
    // for downsampled AO buffers.
    // --------------------------------------------

    ivec2 stepX = ivec2(uDownsampleFactor, 0);
    ivec2 stepY = ivec2(0, uDownsampleFactor);

    ivec2 px = clamp(fullPix + stepX, ivec2(0), uDepthTexSize - ivec2(1));
    ivec2 py = clamp(fullPix + stepY, ivec2(0), uDepthTexSize - ivec2(1));

    float depthX = texelFetch(uDepthTex, px, 0).r;
    float depthY = texelFetch(uDepthTex, py, 0).r;

    vec3 viewPosX = (depthX < 1.0) ? reconstructViewPosition((vec2(px) + 0.5) * invFull, depthX) : viewPos;
    vec3 viewPosY = (depthY < 1.0) ? reconstructViewPosition((vec2(py) + 0.5) * invFull, depthY) : viewPos;

    // If the neighbor looks like a different surface (big depth jump), ignore it.
    float z0 = viewPos.z;
    if (abs(viewPosX.z - z0) > 0.01 * abs(z0)) viewPosX = viewPos;
    if (abs(viewPosY.z - z0) > 0.01 * abs(z0)) viewPosY = viewPos;

    vec3 dViewPos_dx = viewPosX - viewPos; // meters per 1 AO-step in X
    vec3 dViewPos_dy = viewPosY - viewPos; // meters per 1 AO-step in Y

    // --------------------------------------------
    // Convert AO radius (meters) to pixels at this depth
    // --------------------------------------------

    float projY = abs(uProj[1][1]);
    float viewZ = max(abs(viewPos.z), 1e-3);

    // How many full-res pixels represent 1 meter at this depth?
    float metersToPixels = (0.5 * float(uDepthTexSize.y) * projY) / viewZ;

    float desiredRadiusPixels = uRadiusMeters * metersToPixels;

    // Clamp radius in pixels to avoid huge costs near the camera.
    float clampT = smoothstep(0.85 * uMaxRadiusPixels, uMaxRadiusPixels, desiredRadiusPixels);
    float radiusPixels = mix(desiredRadiusPixels, uMaxRadiusPixels, clampT);

    // Convert the clamped pixel radius back to meters (effective radius at this depth).
    float radiusMeters = radiusPixels / metersToPixels;

    // --------------------------------------------
    // World-stable random rotation / jitter
    //
    // Base random numbers on world position so AO doesn’t "swim" when the camera moves.
    // Important for VR to avoid different patterns in each eye.
    // --------------------------------------------

    vec3 worldPos = (uInvView * vec4(viewPos, 1.0)).xyz;
    
    ivec3 coarseCellId = ivec3(floor(worldPos / kCoarseNoiseCellMeters));
    ivec3 fineCellId   = ivec3(floor(worldPos / kFineNoiseCellMeters));
    
    vec3 rndCoarse = hash(coarseCellId);
    vec3 rndFine   = hash(fineCellId);
    
    float baseRotation01 = fract(rndCoarse.x + (rndFine.x - 0.5) * kNoiseAmount);
    float baseStepJitter = fract(rndCoarse.z + (rndFine.z - 0.5) * kNoiseAmount);

    int slices = clamp(uNumSlices, 1, kMaxSlices);
    float sliceWidth = PI / float(slices);

    // --------------------------------------------
    // Slice integration
    //
    // Each slice is a 2D plane through the camera ray. Find the horizon on both sides
    // of that plane, then convert it into a visibility value for that slice.
    // --------------------------------------------

    float weightedVisibilitySum = 0.0;
    float weightSum             = 0.0;

    vec3 ray0 = viewRayDirection(uvFull);

    for (int s = 0; s < kMaxSlices; ++s)
    {
        if (s >= slices) break;

        // Per-slice randoms (world-stable).
        vec3 rndPos = hash_forSlice(fineCellId, s, ivec3(17, 59, 101));
        vec3 rndNeg = hash_forSlice(fineCellId, s, ivec3(71, 23, 9));

        // Pick an angle in [0..PI). Slice directions cover half a circle since we sample both sides.
        float phi = (baseRotation01 * PI) + ((float(s) + 0.5) * sliceWidth);

        // Small random shift inside the slice to break up banding.
        phi += (rndPos.x - 0.5) * kNoiseAmount * sliceWidth * 0.75;
        phi = mod(phi, PI);

        vec2 screenDir = vec2(cos(phi), sin(phi));
        
        // Jitter for stepping along the radius (changes where samples land).
        float stepJitter01 = fract(baseStepJitter + (rndPos.y - 0.5) * kNoiseAmount + float(s) * 0.61803398875);

        // Build a basis for the slice plane in view space.
        vec3 ray1 = viewRayDirection(uvFull + screenDir * invFull);
        vec3 sliceTangent = ray1 - ray0;

        // Remove component along the camera direction, so tangent stays in the slice plane.
        sliceTangent -= toCameraDir * dot(sliceTangent, toCameraDir);

        float tangentLen = length(sliceTangent);
        if (tangentLen < 1e-6) continue;
        sliceTangent /= tangentLen;

        // Plane normal for this slice.
        vec3 slicePlaneNormal = normalize(cross(sliceTangent, toCameraDir));
        
        // Project the surface normal into the slice plane.
        vec3 normalInPlane = viewNormal - slicePlaneNormal * dot(viewNormal, slicePlaneNormal);
        float normalInPlaneLen = length(normalInPlane);
        if (normalInPlaneLen < 1e-6) continue;

        vec3 normalInPlaneDir = normalInPlane / normalInPlaneLen;

        // Angle of the normal inside the slice plane.
        float cosG = clamp(dot(normalInPlaneDir, toCameraDir), -1.0, 1.0);
        float sinG = clamp(dot(normalInPlaneDir, sliceTangent), -1.0, 1.0);
        float normalAngle = atan(sinG, cosG);

        // Open sky visibility for this slice (no occlusion).
        float openLo = max(-0.5 * PI, normalAngle - 0.5 * PI);
        float openHi = min( 0.5 * PI, normalAngle + 0.5 * PI);
        float openArea = max(integrateVisibilityArc(openLo, openHi, normalAngle), 1e-6);

        // Find the horizon on each side of the slice.
        float horizonPos = findSignedHorizonAngle(
            fullPix, uvFull, viewPos, viewNormal, toCameraDir, screenDir,
            radiusPixels, radiusMeters, +1.0,
            dViewPos_dx, dViewPos_dy,
            stepJitter01, rndPos
        );

        float horizonNeg = findSignedHorizonAngle(
            fullPix, uvFull, viewPos, viewNormal, toCameraDir, screenDir,
            radiusPixels, radiusMeters, -1.0,
            dViewPos_dx, dViewPos_dy,
            fract(stepJitter01 + 0.53), rndNeg
        );

        // Clamp occluded interval to the valid hemisphere range around the normal.
        float occludedLo = max(min(horizonNeg, horizonPos), normalAngle - 0.5 * PI);
        float occludedHi = min(max(horizonNeg, horizonPos), normalAngle + 0.5 * PI);

        float visibleArea = 0.0;
        if (occludedHi > occludedLo) 
            visibleArea = integrateVisibilityArc(occludedLo, occludedHi, normalAngle);

        float visibility = visibleArea / openArea; // 1 = fully visible, 0 = fully occluded

        // Weight slices where the normal has a strong component in the slice plane.
        weightedVisibilitySum += normalInPlaneLen * visibility;
        weightSum             += normalInPlaneLen;
    }

    outAO = clamp(weightedVisibilitySum / max(weightSum, 1e-6), 0.0, 1.0);
}