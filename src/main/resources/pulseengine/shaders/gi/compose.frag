#version 330 core
#define PI 3.14159265359

in vec2 uv;
out vec4 fragColor;

uniform sampler2D sceneTex;
uniform sampler2D sceneMetadataTex;
uniform sampler2D lightTex;
uniform sampler2D localSdfTex;

uniform vec2 sampleOffset;
uniform float dithering;
uniform float scale;
uniform float sourceMultiplier;
uniform vec4 occluderAmbientLight;

const float sdfDecodeScale = sqrt(2.0) / 65000.0;

vec2 getSdfDirection(vec2 uv)
{
    float texelSize = 0.002 * scale;
    float x1 = texture(localSdfTex, clamp(uv + vec2(texelSize, 0.0), 0, 1)).r * sdfDecodeScale;
    float x2 = texture(localSdfTex, clamp(uv - vec2(texelSize, 0.0), 0, 1)).r * sdfDecodeScale;
    float y1 = texture(localSdfTex, clamp(uv + vec2(0.0, texelSize), 0, 1)).r * sdfDecodeScale;
    float y2 = texture(localSdfTex, clamp(uv - vec2(0.0, texelSize), 0, 1)).r * sdfDecodeScale;
    return normalize(vec2(x1 - x2, y1 - y2));
}

void main()
{
    vec2 offsetUv = clamp(uv + sampleOffset, 0.0, 1.0);
    vec3 light = texture(lightTex, offsetUv).rgb;
    vec4 scene = texture(sceneTex, offsetUv);
    vec4 sceneMeta = texture(sceneMetadataTex, offsetUv);

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
            light = occluderAmbientLight.rgb;

            float o = 0.002 * scale;
            vec2 offset[9] = vec2[9](vec2(0,0), vec2(o,0), vec2(-o,0), vec2(0,o), vec2(0,-o), vec2(o,o), vec2(-o,o), vec2(o,-o), vec2(-o,-o));
            float edgeLightStrengt = sceneMeta.a;

            for (int i = 0; i < 9; i++)
            {
                vec2 pos = clamp(offsetUv + offset[i], 0.0, 1.0);
                float dist = -texture(localSdfTex, pos).x * sdfDecodeScale;
                vec2 dir = getSdfDirection(pos);
                float falloff = (edgeLightStrengt * 0.01) / (1.0 + (dist / scale) * 100.0);
                falloff = clamp(falloff, 0.0, 1.0);
                falloff = pow(falloff, 3.0);
                light += texture(lightTex, clamp(offsetUv + dir * dist * 1.3, 0.0, 1.0)).rgb * falloff / 9.0;
            }
        }
    }

    // Add some dithering to prevent color banding
    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
    light += mix(-dithering / 255.0, dithering / 255.0, noise);

    fragColor = vec4(light, 1.0);
}