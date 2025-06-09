#version 330 core
#define PI 3.14159265359

in vec2 uv;

out vec4 fragColor;

uniform sampler2D localSceneTex;
uniform sampler2D localSceneMetadataTex;
uniform sampler2D lightTex;
uniform sampler2D localSdfTex;
uniform sampler2D normalMapTex;

uniform float dithering;
uniform float scale;
uniform float sourceMultiplier;
uniform vec2 localSdfRes;
uniform vec2 uvSampleOffset;
uniform vec4 ambientLight;
uniform vec4 ambientOccluderLight;
uniform vec2 lightTexUvMax;
uniform float normalMapScale;

float getSdfDistance(vec2 p)
{
    return -texture(localSdfTex, clamp(p, 0, 1)).r;
}

vec2 getSdfDirection(vec2 p)
{
    float h = 0.002 * scale;
    const vec2 k = vec2(1, -1);
    return normalize(
        k.xy * texture(localSdfTex, clamp(p + k.xy * h, 0, 1)).r +
        k.yx * texture(localSdfTex, clamp(p + k.yx * h, 0, 1)).r +
        k.xx * texture(localSdfTex, clamp(p + k.xx * h, 0, 1)).r +
        k.yy * texture(localSdfTex, clamp(p + k.yy * h, 0, 1)).r
    );
}

float hash(vec2 uv)
{
    return fract(sin(7.289 * uv.x + 11.23 * uv.y) * 23758.5453);
}

void main()
{
    vec2 offsetUv = clamp(uv + uvSampleOffset, 0.0, 1.0);
    vec3 light = texture(lightTex, offsetUv * lightTexUvMax).rgb;
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

            float o = 0.003 * scale;
            vec2 offset[9] = vec2[9](vec2(0,0), vec2(o,0), vec2(-o,0), vec2(0,o), vec2(0,-o), vec2(o,o), vec2(-o,o), vec2(o,-o), vec2(-o,-o));
            float edgeLightStrengt = sceneMeta.a * 0.01;

            vec3 normal = normalize(texture(normalMapTex, offsetUv).xyz * 2.0 - 1.0);

            for (int i = 0; i < 9; i++)
            {
                float noise = hash(uv) * 2.0 - 1.0;
                vec2 pos = clamp(offsetUv + offset[i] + 0.0005 * noise, 0.0, 1.0);
                vec2 dir = getSdfDirection(pos);
                float dist = getSdfDistance(pos) + abs(noise * 0.1);

                float falloff = edgeLightStrengt / (1.0 + (0.05 * dist / scale));
                falloff = clamp(falloff, 0.0, 1.0);
                falloff = pow(falloff, 7.0);

                vec3 lightDir = normalize(vec3(dir, normalMapScale != 0.0 ? 1.0 / normalMapScale : 100000.0));
                falloff *= clamp(dot(normal, lightDir) * 2, 0.0, 1.0);

                vec2 samplePos = clamp(offsetUv + dir * dist * 1.2 / localSdfRes, 0.0, 1.0);
                if (samplePos.x != 0.0 && samplePos.y != 0.0)
                    light += texture(lightTex, samplePos * lightTexUvMax).rgb * falloff / 9.0;
            }
        }
    }

    // Ambient light
    light += ambientLight.rgb;

    // Add some dithering to prevent color banding
    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
    light += mix(-dithering / 255.0, dithering / 255.0, noise);

    fragColor = vec4(light, 1.0);
}