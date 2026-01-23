#version 330 core

// -----------------------------------------------------
//  1D bilateral denoiser that preserves edges using depth
// -----------------------------------------------------

out float outAO;

uniform sampler2D uAoTex;
uniform sampler2D uDepthTex;

// Projection matrix to convert depth01 -> view-space Z distance.
uniform mat4 uProj;

// Sizes
uniform ivec2 uDepthTexSize;
uniform ivec2 uAoTexSize;
uniform int   uDownsampleFactor;

// Direction for the separable 1D blur (1,0) or (0,1)
uniform ivec2 uDir;

// Denoising params
uniform int   uPixelRadius;        // Number of pixels to sample on each side
uniform float uBlurWidth;          // How quickly the blur fades with distance in pixels
uniform float uAoEdgePreservation; // Prevents AO bleeding across strong AO changes (0=none, 1=strong)

float viewZFromDepth(float depth01)
{
    // Convert [0..1] depth to view-space Z (distance along camera forward axis).
    float zNdc = depth01 * 2.0 - 1.0;
    float denom = (zNdc + uProj[2][2]);
    denom = (abs(denom) < 1e-8) ? (sign(denom) * 1e-8) : denom; // Avoid division by zero
    float zView = uProj[3][2] / denom;
    return zView;
}

float fetchDepth01(ivec2 aoPix)
{
    // Map possible low res AO pixel to center of the full-res depth pixel
    ivec2 fullPix = aoPix * uDownsampleFactor + ivec2(uDownsampleFactor / 2);
    fullPix = clamp(fullPix, ivec2(0), uDepthTexSize - ivec2(1));
    return texelFetch(uDepthTex, fullPix, 0).r;
}

void main()
{
    ivec2 aoPix = ivec2(gl_FragCoord.xy);
    if (any(lessThan(aoPix, ivec2(0))) || any(greaterThanEqual(aoPix, uAoTexSize)))
    {
        outAO = 1.0;
        return;
    }

    float aoCenter    = texelFetch(uAoTex, aoPix, 0).r;
    float depthCenter = fetchDepth01(aoPix);

    // Skip background pixels
    if (depthCenter >= 1.0)
    {
        outAO = aoCenter;
        return;
    }

    float centerViewZ = max(abs(viewZFromDepth(depthCenter)), 1e-6);
    float sumWeight = 0.0;
    float sumAo     = 0.0;

    for (int i = -64; i <= 64; ++i)
    {
        if (i < -uPixelRadius || i > uPixelRadius) continue;

        ivec2 samplePix = aoPix + uDir * i;
        samplePix = clamp(samplePix, ivec2(0), uAoTexSize - ivec2(1));

        float aoSample    = texelFetch(uAoTex, samplePix, 0).r;
        float depthSample = fetchDepth01(samplePix);
        
        if (depthSample >= 1.0) continue; // Skip background samples

        float sampleViewZ = max(abs(viewZFromDepth(depthSample)), 1e-6);

        // Spatial weight with normal gaussian blur
        // Makes pixels closer to center count more than pixels far away
        float spatialSigma = max(uBlurWidth * uBlurWidth, 1e-6);
        float spatialWeight = exp(-(float(i) * float(i)) / spatialSigma);

        // Depth weight prevents blurring across edges
        float depthSigmaBias = 0.02; // Minimum tolerance so we don't over-reject nearby pixels
        float depthSigmaScale = 0.1; // How quickly tolerance grows with distance
        float depthSigma  = max(depthSigmaBias, depthSigmaScale * centerViewZ);
        float deltaViewZ  = abs(sampleViewZ - centerViewZ);
        float depthWeight = exp(-(deltaViewZ * deltaViewZ) / max(depthSigma * depthSigma, 1e-12));

        // AO range weight prevents AO from bleeding too much across strong AO changes. 
        // Works as a bilateral filter in AO-space.
        float aoRangeWeight = 1.0;
        float aoSigma = mix(1.0, 0.05, uAoEdgePreservation);
        if (uAoEdgePreservation > 0.0)
        {
            float aoSigma2 = max(aoSigma * aoSigma, 1e-6);
            float deltaAo = aoSample - aoCenter;
            aoRangeWeight = exp(-(deltaAo * deltaAo) / aoSigma2);
        }

        // Final weight
        float weight = spatialWeight * depthWeight * aoRangeWeight;

        sumAo     += weight * aoSample;
        sumWeight += weight;
    }

    outAO = (sumWeight > 1e-6) ? clamp(sumAo / sumWeight, 0.0, 1.0) : aoCenter;
}