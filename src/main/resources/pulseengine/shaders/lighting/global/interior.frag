#version 330 core
#define TAU 6.28318530718

in vec2 uv;
in vec2 uvLastFrame;

out vec4 fragColor;

uniform sampler2D localSceneTex;
uniform sampler2D localSceneMetadataTex;
uniform sampler2D localSdfTex;
uniform sampler2D normalMapTex;
uniform sampler2D exteriorLightTex;
uniform sampler2D lastInteriorTex;

uniform float scale;
uniform vec2 localSdfRes;
uniform vec2 uvSampleOffset;
uniform vec2 exteriorLightTexUvMax;
uniform float normalMapScale;
uniform float camAngle;
uniform float time;

float noise(vec2 p)
{
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 getOccluderLight(vec2 offsetUv, vec4 sceneMetaData)
{
    const int rayCount = 16;

    vec2 noise = vec2(noise(uv.xy + time), noise(uv.yx - time)) * 2.0 - 1.0;
    float angleStep = TAU / rayCount;
    float startAngle = camAngle + angleStep * noise.x;
    vec2 startPos = offsetUv * localSdfRes + noise;
    vec2 invLocalSdfRes = 1.0 / localSdfRes;
    vec3 light = vec3(0.0);

    float edgeLightStrengt = sceneMetaData.a * 0.01;
    vec3 normal = normalize(texture(normalMapTex, uv).xyz * 2.0 - 1.0);

    for (int rayIndex = 0; rayIndex < rayCount; rayIndex++)
    {
        float angle = startAngle + angleStep * rayIndex;
        vec2 dir = vec2(cos(angle), sin(angle));
        vec2 pos = startPos;
        float travelDist = 0.0;

        int i = 0;
        for (; i < 10; i++)
        {
            float dist = -texture(localSdfTex, clamp(pos * invLocalSdfRes, 0, 1)).r;
            if (dist < 0.01)
                break;
            pos += dir * dist;
            travelDist += dist;
        }
        if (i == 10) continue; // No intersection found

        vec3 lightDir = normalize(vec3(dir, normalMapScale));
        float falloff = clamp(dot(normal, lightDir) * 4, 0.0, 1.0);
        falloff *= edgeLightStrengt / (1.0 + (0.05 * travelDist / scale));

        vec2 samplePos = clamp((pos + dir) * invLocalSdfRes, 0.0, 1.0);
        light += texture(exteriorLightTex, samplePos * exteriorLightTexUvMax).rgb * falloff;
    }

    // Combine with last frame's light
    vec3 lastLight = texture(lastInteriorTex, clamp(uvLastFrame, 0.0, 1.0)).rgb;
    return mix(light / rayCount, lastLight.rgb, 0.75);
}

void main()
{
    vec2 offsetUv = clamp(uv + uvSampleOffset, 0.0, 1.0);
    vec4 scene = texture(localSceneTex, offsetUv);
    vec4 sceneMeta = texture(localSceneMetadataTex, offsetUv);

    float sourceIntensity = sceneMeta.b;
    bool isOccluder = scene.a > 0.8;
    bool isLightSource = sourceIntensity > 0.0;
    vec3 light = vec3(0.0);

    if (isOccluder && !isLightSource)
    {
        light = getOccluderLight(offsetUv, sceneMeta);
    }

    fragColor = vec4(light, 1.0);
}