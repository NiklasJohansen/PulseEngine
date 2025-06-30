#version 330 core
#define PI 3.14159265359
#define TAU 6.28318530718

in vec2 uv;

out vec4 fragColor;

uniform sampler2D localSceneTex;
uniform sampler2D localSceneMetadataTex;
uniform sampler2D exteriorLightTex;
uniform sampler2D interiorLightTex;
uniform sampler2D aoTex;

uniform float dithering;
uniform float sourceIntensity;
uniform vec2 uvSampleOffset;
uniform vec4 ambientLight;
uniform vec4 ambientInteriorLight;
uniform vec2 exteriorLightTexUvMax;
uniform bool aoEnabled;

void main()
{
    vec2 offsetUv = clamp(uv + uvSampleOffset, 0.0, 1.0);
    vec4 scene = texture(localSceneTex, offsetUv);
    vec4 sceneMeta = texture(localSceneMetadataTex, offsetUv);
    vec3 light = texture(exteriorLightTex, offsetUv * exteriorLightTexUvMax).rgb;

    float sceneSourceIntensity = sceneMeta.b;
    bool isOccluder = scene.a > 0.8;
    bool isLightSource = sceneSourceIntensity > 0.0;

    if (isOccluder)
    {
        if (isLightSource)
        {
            light *= sourceIntensity;
        }
        else
        {
            light = texture(interiorLightTex, uv).rgb + ambientInteriorLight.rgb;
        }
    }

    // Ambient light
    light += ambientLight.rgb;

    // Apply ambient occlusion
    if (aoEnabled)
        light *= texture(aoTex, offsetUv).r;

    // Add some dithering to prevent color banding
    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
    light += mix(-dithering / 255.0, dithering / 255.0, noise);

    fragColor = vec4(light, 1.0);
}