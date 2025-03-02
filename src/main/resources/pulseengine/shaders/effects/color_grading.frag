#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D tex;

uniform int toneMapper;
uniform float exposure;
uniform float contrast;
uniform float saturation;
uniform float vignette;

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

void main()
{
    vec4 color = texture(tex, uv);

    // Exposure
    color.rgb = 1.0 - exp(-color.rgb * exposure);

    // Contrast
    color.rgb = ((color.rgb - 0.5f) * max(contrast > 1.0 ? (1.0 + 0.005 * contrast) : contrast, 0)) + 0.5f;

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

    // Vignette
    vec2 v = uv * (1.0 - uv.yx);
    color.rgb *= pow(v.x * v.y * 15.0, vignette);

    fragColor = color;
}