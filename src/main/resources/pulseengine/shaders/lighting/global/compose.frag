#version 330 core
#define PI 3.14159265359
#define TAU 6.28318530718

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
uniform float camAngle;

float noise(vec2 p)
{
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 getOccluderLight(vec2 offsetUv, vec4 sceneMetaData)
{
    const float rayCount = 16.0;
    float angleStep = TAU / rayCount;
    float angleNoise = 0.2 * (noise(uv) * 2.0 - 1.0);
    vec2 origin = offsetUv * localSdfRes;
    vec3 lightSum = vec3(0.0);

    float edgeLightStrengt = sceneMetaData.a * 0.01;
    vec3 normal = normalize(texture(normalMapTex, uv).xyz * 2.0 - 1.0);

    for (float angle = 0.0; angle < TAU; angle += angleStep)
    {
        float ang = camAngle + angle + angleNoise;
        vec2 dir = vec2(cos(ang), sin(ang));
        vec2 pos = origin;
        float travelDist = 0.0;

        for (int i = 0; i < 5; i++)
        {
            float dist = -texture(localSdfTex, clamp(pos / localSdfRes, 0, 1)).r;
            if (dist < 0.01)
                break;
            pos += dir * dist;
            travelDist += dist;
        }

        vec3 lightDir = normalize(vec3(dir, normalMapScale != 0.0 ? 1.0 / normalMapScale : 100000.0));
        float falloff = clamp(dot(normal, lightDir) * 3, 0.0, 1.0);
        falloff *= edgeLightStrengt / (1.0 + (0.05 * travelDist / scale));

        vec2 samplePos = clamp((pos + 2.0 * dir) / localSdfRes, 0.0, 1.0);
        lightSum += texture(lightTex, samplePos * lightTexUvMax).rgb * falloff;
    }

    return lightSum / rayCount;
}

void main()
{
    vec2 offsetUv = clamp(uv + uvSampleOffset, 0.0, 1.0);
    vec3 light = texture(lightTex, offsetUv * lightTexUvMax).rgb;
    vec4 scene = texture(localSceneTex, offsetUv);
    vec4 sceneMeta = texture(localSceneMetadataTex, offsetUv);

    float sourceIntensity = sceneMeta.b;
    bool isOccluder = scene.a > 0.8;
    bool isLightSource = sourceIntensity > 0.0;

    if (isOccluder)
    {
        if (isLightSource)
        {
            light *= 1.0 + sourceIntensity * sourceMultiplier;
        }
        else
        {
            light = getOccluderLight(offsetUv, sceneMeta) + ambientOccluderLight.rgb;
        }
    }

    // Ambient light
    light += ambientLight.rgb;

    // Add some dithering to prevent color banding
    light += mix(-dithering / 255.0, dithering / 255.0, noise(uv));

    fragColor = vec4(light, 1.0);
}