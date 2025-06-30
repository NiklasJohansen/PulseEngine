#version 330 core

#define TAU 6.28318530718
#define NUM_DIRS 10
#define NUM_STEPS 10
#define MIN_STEP 0.01

in vec2 uv;
in vec2 uvLastFrame;

out vec4 fragColor;

uniform sampler2D localSdfTex;
uniform sampler2D localSceneTex;
uniform sampler2D localSceneMetadataTex;
uniform sampler2D lastAoTex;

uniform vec2 localSdfTexRes;
uniform float aoStrength;
uniform float aoRadius;
uniform float camScale;
uniform float time;

float noise(vec2 p)
{
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main()
{
    vec4 scene = texture(localSceneTex, uv);
    vec4 sceneMeta = texture(localSceneMetadataTex, uv);

    bool isLightSource = sceneMeta.b > 0.0; // Source intensity
    bool isOccluder = scene.a > 0.3; // Scene Alpha
    float radius = aoRadius * camScale;
    float ao = 1.0;

    if (!isOccluder && !isLightSource)
    {
        float occlusion = 0.0;
        float angleStep = TAU / float(NUM_DIRS);
        float angleNoise = angleStep * (noise(vec2(uv + time)) * 2.0 - 1.0);

        for (int i = 0; i < NUM_DIRS; ++i)
        {
            float a = (i + 0.5) * angleStep + angleNoise;
            vec2 dir = vec2(cos(a), sin(a));
            float ray = 0.0;

            for (int s = 0; s < NUM_STEPS && ray < radius; ++s)
            {
                vec2 sampleUv = clamp(uv + (dir * ray) / localSdfTexRes, 0, 1);
                float dist = texture(localSdfTex, sampleUv).r;
                if (dist < MIN_STEP)
                {
                    bool hitLightSource = texture(localSceneMetadataTex, sampleUv).b > 0.5;
                    if (hitLightSource)
                        break; // Hit a lightsource, dont occlude

                    occlusion += 1.0 - clamp(ray / radius, 0.0, 1.0);
                    break;
                }
                ray += dist;
            }
        }

        ao = clamp(1.0 - (occlusion / float(NUM_DIRS)), 0.0, 1.0);
        ao = pow(ao, aoStrength);
        ao = mix(ao, texture(lastAoTex, uvLastFrame).r, 0.5);
    }

    fragColor = vec4(ao, 0, 0, 1.0);
}