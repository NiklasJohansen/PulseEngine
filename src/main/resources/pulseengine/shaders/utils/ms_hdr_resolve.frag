#version 330 core

layout(location = 0) out vec4 fragColor;

uniform sampler2DMS uColorMS;
uniform int uSamples;
uniform float uExposure;

void main()
{
    ivec2 pixel = ivec2(gl_FragCoord.xy);

    // Karis-style normalized sample weights, using the peak RGB channel as in Lottes.
    // https://graphicrants.blogspot.com/2013/12/tone-mapping.html
    // https://gpuopen.com/learn/optimized-reversible-tonemapper-for-resolve/

    vec3 weightedColor = vec3(0.0);
    float weightSum = 0.0;
    float alpha = 0.0;
    
    for (int i = 0; i < uSamples; i++)
    {
        vec4 sampleColor = texelFetch(uColorMS, pixel, i);
        float peak = max(0.0, max(sampleColor.r, max(sampleColor.g, sampleColor.b)));
        float weight = 1.0 / (1.0 + peak * uExposure);
        weightedColor += sampleColor.rgb * weight;
        weightSum += weight;
        alpha += sampleColor.a;
    }
    
    fragColor = vec4(weightedColor / weightSum, alpha / float(uSamples));
}
