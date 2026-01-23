#version 330 core

// -----------------------------------------------------
//  Depth-aware upsampling of lower resolution AO texture.
//  Takes AO computed at a lower resolution and reconstruct it at full resolution
//  without smearing across depth edges.
// -----------------------------------------------------

out float outAO;

uniform sampler2D uAoTex;    // Low-res AO texture
uniform sampler2D uDepthTex; // Full-res depth texture

// Inverse projection matrix used to reconstruct view-space position from depth.
uniform mat4 uInvProj;

// Sizes
uniform ivec2 uResolution;
uniform ivec2 uAoTexSize;
uniform int   uDownsampleFactor;

// Upsampling params
uniform float uEdgeTolerance;
uniform float uUpsampleBlur;

const float kEps = 1e-6;

// -----------------------------------------------------
// Helpers
// -----------------------------------------------------

vec3 reconstructViewPosition(vec2 uv, float depth01)
{
    // Reconstruct view-space position from UV + depth01.
    vec3 pNdc = vec3(uv, depth01) * 2.0 - 1.0;
    vec4 clip  = vec4(pNdc, 1.0);
    vec4 view  = uInvProj * clip;
    return view.xyz / max(view.w, 1e-8);
}

float fetchDepth01(ivec2 pixelPos)
{
    pixelPos = clamp(pixelPos, ivec2(0), uResolution - ivec2(1));
    return texelFetch(uDepthTex, pixelPos, 0).r;
}

vec2 pixelPosToUv(ivec2 pixelPos)
{
    return (vec2(pixelPos) + 0.5) / vec2(uResolution);
}

ivec2 aoPixelPosToFullResPixelCenterPos(ivec2 aoPixelPos)
{
    // Map a low-res AO pixel to the center pixel of its corresponding full-res block.
    return aoPixelPos * uDownsampleFactor + ivec2(uDownsampleFactor / 2);
}

// -----------------------------------------------------
// Main
// -----------------------------------------------------

void main()
{
    ivec2 pixelPos = ivec2(gl_FragCoord.xy);
    
    // Depth of the full-res pixel we are upsampling for.
    float centerDepth01 = fetchDepth01(pixelPos);

    // Background/sky: output open AO
    if (centerDepth01 >= 1.0)
    {
        outAO = 1.0;
        return;
    }

    // Reconstruct view-space Z at the full-res pixel.
    vec3 centerViewPos = reconstructViewPosition(pixelPosToUv(pixelPos), centerDepth01);
    float centerViewZ = max(abs(centerViewPos.z), kEps);

    // Which low-res AO pixel corresponds to this full-res pixel?
    ivec2 aoPixelPosCenter = pixelPos / uDownsampleFactor;
    aoPixelPosCenter = clamp(aoPixelPosCenter, ivec2(0), uAoTexSize - ivec2(1));
    
    float sumWeight = 0.0;
    float sumAo     = 0.0;

    // Gather a 4x4 neighborhood around the low-res center.
    for (int oy = -1; oy <= 2; ++oy)
    {
        for (int ox = -1; ox <= 2; ++ox)
        {
            ivec2 aoSamplePos = clamp(aoPixelPosCenter + ivec2(ox, oy), ivec2(0), uAoTexSize - ivec2(1));

            // Candidate AO value
            float aoSample = texelFetch(uAoTex, aoSamplePos, 0).r;

            // Get a representative depth for that AO sample by looking up the depth
            // at the center of its full-res block.
            ivec2 samplePixelPos = aoPixelPosToFullResPixelCenterPos(aoSamplePos);
            float sampleDepth01 = fetchDepth01(samplePixelPos);

            float depthWeight = 0.0;
            if (sampleDepth01 < 1.0) 
            {
                // Depth similarity weight.
                // If the candidate sample is at a very different depth than the full-res pixel,
                // we reduce or reject its contribution to prevent AO from bleeding across edges.
                vec3 sampleViewPos = reconstructViewPosition(pixelPosToUv(samplePixelPos), sampleDepth01);
                float sampleViewZ = max(abs(sampleViewPos.z), kEps);
                float depthSigmaBias = mix(0.0005, 0.0030, uEdgeTolerance);
                float depthSigmaScale = mix(0.0010, 0.0060, uEdgeTolerance);
                float sigma  = max(depthSigmaBias, depthSigmaScale * centerViewZ);
                float deltaZ = abs(sampleViewZ - centerViewZ);
                depthWeight  = exp(-(deltaZ * deltaZ) / max(sigma * sigma, 1e-12));
            }

            // Spatial blur weight in AO (low-res) pixels.
            // Low blur => strongly prefer the nearest AO sample.
            // High blur => blend more of the neighborhood.
            float sigmaSharp = 0.5;
            float sigmaBlur  = 2.5;
            float sigma = exp2(mix(log2(sigmaSharp), log2(sigmaBlur), uUpsampleBlur));
            vec2  offsetAo = vec2(aoSamplePos - aoPixelPosCenter);
            float r2 = dot(offsetAo, offsetAo);
            float s2 = max(sigma * sigma, 1e-12);
            float spatialWeight = exp(-r2 / s2);

            // Combined weight
            float weight = depthWeight * spatialWeight;

            // Skip if completely rejected (avoids halos at silhouettes and disocclusions)
            if (weight <= 0.0) continue;

            sumAo     += weight * aoSample;
            sumWeight += weight;
        }
    }

    // Fall back to low-res sample if everything was rejected (common around thin geometry or disocclusion),
    if (sumWeight <= 1e-6)
    {
        outAO = texelFetch(uAoTex, aoPixelPosCenter, 0).r;
        return;
    }

    outAO = clamp(sumAo / sumWeight, 0.0, 1.0);
}
