#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D baseTex;
uniform sampler2DArray lutTexArray;

uniform int toneMapper;
uniform float exposure;
uniform float contrast;
uniform float saturation;
uniform float vignette;
uniform vec3 lutTexCoord;
uniform float lutIntensity;
uniform float lutSize;

// Narkowicz 2015, "ACES Filmic Tone Mapping Curve"
vec3 aces(vec3 x)
{
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 filmic(vec3 x)
{
    vec3 X = max(vec3(0.0), x - 0.004);
    vec3 result = (X * (6.2 * X + 0.5)) / (X * (6.2 * X + 1.7) + 0.06);
    return pow(result, vec3(2.2));
}

vec3 reinhard(vec3 x)
{
    return x / (1.0 + x);
}

vec3 uncharted2Tonemap(vec3 x)
{
    float A = 0.15;
    float B = 0.50;
    float C = 0.10;
    float D = 0.20;
    float E = 0.02;
    float F = 0.30;
    float W = 11.2;
    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
}

// John Hable 2010, http://filmicworlds.com/blog/filmic-tonemapping-operators/
vec3 uncharted2(vec3 color)
{
    const float W = 11.2;
    float exposureBias = 2.0;
    vec3 curr = uncharted2Tonemap(exposureBias * color);
    vec3 whiteScale = 1.0 / uncharted2Tonemap(vec3(W));
    return curr * whiteScale;
}

// Lottes 2016, "Advanced Techniques and Optimization of HDR Color Pipelines"
vec3 lottes(vec3 x)
{
    const vec3 a      = vec3(1.6);
    const vec3 d      = vec3(0.977);
    const vec3 hdrMax = vec3(8.0);
    const vec3 midIn  = vec3(0.18);
    const vec3 midOut = vec3(0.267);

    vec3 b = (-pow(midIn, a) + pow(hdrMax, a) * midOut) / ((pow(hdrMax, a * d) - pow(midIn, a * d)) * midOut);
    vec3 c = (pow(hdrMax, a * d) * pow(midIn, a) - pow(hdrMax, a) * pow(midIn, a * d) * midOut) / ((pow(hdrMax, a * d) - pow(midIn, a * d)) * midOut);

    return pow(x, a) / (pow(x, a * d) * b + c);
}

vec3 sampleLut(float r, float g, float b)
{
    float x = (r + b * lutSize + 0.5) / (lutSize * lutSize);
    float y = (g + 0.5) / lutSize;
    return texture(lutTexArray, lutTexCoord * vec3(x, y, 1.0)).rgb;
}

vec3 applyLUT(vec3 color)
{
    vec3 scaled = color * (lutSize - 1.0);
    vec3 index0 = floor(scaled);
    vec3 index1 = min(index0 + 1.0, lutSize - 1.0);

    // Sample the eight nearest texels
    vec3 c000 = sampleLut(index0.r, index0.g, index0.b);
    vec3 c100 = sampleLut(index1.r, index0.g, index0.b);
    vec3 c010 = sampleLut(index0.r, index1.g, index0.b);
    vec3 c110 = sampleLut(index1.r, index1.g, index0.b);
    vec3 c001 = sampleLut(index0.r, index0.g, index1.b);
    vec3 c101 = sampleLut(index1.r, index0.g, index1.b);
    vec3 c011 = sampleLut(index0.r, index1.g, index1.b);
    vec3 c111 = sampleLut(index1.r, index1.g, index1.b);

    // Interpolate along the red axis
    vec3 frac = fract(scaled);
    vec3 c00 = mix(c000, c100, frac.r);
    vec3 c10 = mix(c010, c110, frac.r);
    vec3 c01 = mix(c001, c101, frac.r);
    vec3 c11 = mix(c011, c111, frac.r);

    // Interpolate along the green axis
    vec3 c0 = mix(c00, c10, frac.g);
    vec3 c1 = mix(c01, c11, frac.g);

    // Interpolate along the blue axis
    return mix(c0, c1, frac.b);
}

void main()
{
    vec4 color = texture(baseTex, uv);

    // Exposure
    color.rgb *= pow(2.0, exposure) - 1.0;

    // Contrast
    color.rgb = ((color.rgb - 0.5f) * max(1.0 + 0.05 * (contrast - 1), 0)) + 0.5f;

    // Saturation
    vec3 weights   = vec3(0.2125, 0.7154, 0.0721);
    vec3 intensity = vec3(dot(color.rgb, weights));
    color.rgb = mix(intensity, color.rgb, saturation > 1.0 ? (1.0 + 0.1 * saturation) : saturation);

    // Tone mapping
    switch (toneMapper)
    {
        case 0:
            color.rgb = aces(color.rgb);
            break;
        case 1:
            color.rgb = filmic(color.rgb);
            break;
        case 2:
            color.rgb = reinhard(color.rgb);
            break;
        case 3:
            color.rgb = uncharted2(color.rgb);
            break;
        case 4:
            color.rgb = lottes(color.rgb);
            break;
    }

    // Look up table (LUT)
    if (lutTexCoord.z >= 0.0)
    {
        vec3 clampedColor = clamp(color.rgb, 0.0, 1.0);
        vec3 lutColor = applyLUT(clampedColor);
        color.rgb = mix(color.rgb, lutColor, lutIntensity);
    }

    // Vignette
    vec2 v = uv * (1.0 - uv.yx);
    color.rgb *= pow(v.x * v.y * 15.0, vignette);

    fragColor = color;
}