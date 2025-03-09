#version 330 core
#define PI 3.14159265359

in vec2 uv;

out vec4 fragColor;

uniform sampler2D baseTex;
uniform sampler2D localSceneTex;
uniform sampler2D localSceneMetadataTex;
uniform sampler2D lightTex;
uniform sampler2D localSdfTex;

uniform float dithering;
uniform float scale;
uniform float sourceMultiplier;
uniform vec2 sampleOffset;
uniform vec4 ambientLight;
uniform vec4 ambientOccluderLight;

const float sdfDecodeScale = sqrt(2.0) / 65000.0;

float getSdfDistance(vec2 p)
{
    return -texture(localSdfTex, clamp(p, 0, 1)).r * sdfDecodeScale;
}

vec2 getSdfDirection(vec2 p)
{
    float h = 0.002 * scale;
    const vec2 k = vec2(1, -1);
    return normalize(
        k.xy * texture(localSdfTex, clamp(p + k.xy * h, 0, 1)).r * sdfDecodeScale +
        k.yx * texture(localSdfTex, clamp(p + k.yx * h, 0, 1)).r * sdfDecodeScale +
        k.xx * texture(localSdfTex, clamp(p + k.xx * h, 0, 1)).r * sdfDecodeScale +
        k.yy * texture(localSdfTex, clamp(p + k.yy * h, 0, 1)).r * sdfDecodeScale
    );
}

void main()
{
    vec2 offsetUv = clamp(uv + sampleOffset, 0.0, 1.0);
    vec3 base = texture(baseTex, uv).rgb;
    vec3 light = texture(lightTex, offsetUv).rgb;
    vec4 scene = texture(localSceneTex, offsetUv);
    vec4 sceneMeta = texture(localSceneMetadataTex, offsetUv);

    bool isOccluder = scene.a > 0.8;
    bool isLightSource = sceneMeta.b > 0.0; // sourceIntensity > 0.0

    if (isOccluder)
    {
        if (isLightSource)
        {
            light *= 1.0 + sceneMeta.b * sourceMultiplier;
        }
        else
        {
            light = ambientOccluderLight.rgb;

            float o = 0.002 * scale;
            vec2 offset[9] = vec2[9](vec2(0,0), vec2(o,0), vec2(-o,0), vec2(0,o), vec2(0,-o), vec2(o,o), vec2(-o,o), vec2(o,-o), vec2(-o,-o));
            float edgeLightStrengt = sceneMeta.a * 0.01;

            for (int i = 0; i < 9; i++)
            {
                vec2 pos = clamp(offsetUv + offset[i], 0.0, 1.0);
                vec2 dir = getSdfDirection(pos);
                float dist = getSdfDistance(pos);

                float falloff = edgeLightStrengt / (1.0 + (dist / scale) * 100.0);
                falloff = clamp(falloff, 0.0, 1.0);
                falloff = pow(falloff, 3.0);

                vec2 samplePos = clamp(offsetUv + dir * dist * 1.3, 0.0, 1.0);
                light += texture(lightTex, samplePos).rgb * falloff / 9.0;
            }
        }
    }

    // Ambient light
    light += ambientLight.rgb;

    // Add some dithering to prevent color banding
    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
    light += mix(-dithering / 255.0, dithering / 255.0, noise);

    fragColor = vec4(base + light, 1.0);
}