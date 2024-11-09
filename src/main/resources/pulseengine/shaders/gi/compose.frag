#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D sceneTex;
uniform sampler2D sceneMetadataTex;
uniform sampler2D lightTex;
uniform sampler2D distanceFieldTex;

uniform vec2 sampleOffset;
uniform float dithering;
uniform vec2 resolution;
uniform float scale;
uniform float edgeLighting;
uniform float sourceMultiplier;

void main()
{
    vec2 offsetUv = uv + sampleOffset;

    vec3 light = texture(lightTex, offsetUv).rgb;
    vec4 scene = texture(sceneTex, offsetUv);
    vec4 sceneMeta = texture(sceneMetadataTex, offsetUv);

    bool isSolid = scene.a > 0.5;
    bool isLightSource = sceneMeta.b > 0.0; // sourceIntensity > 0.0

    if (isSolid)
    {
        if (isLightSource)
        {
            light *= sourceMultiplier;
        }
        else
        {
            float edgeLightDist = edgeLighting * 0.001 * scale;
            vec2 ratio = vec2(resolution.x / resolution.y, 1.0);

            light = vec3(0.0);
            for (int i = 0; i < 4; i++)
            {
                vec2 offset = scale * 5.0 * (vec2(i % 2, i / 2) - 0.5) / resolution.x;
                vec2 dist = texture(distanceFieldTex, offsetUv + offset).xy * ratio;
                float falloff = smoothstep(0.0, 1.0, clamp(1.0 - length(dist) / edgeLightDist, 0.0, 1.0));
                light += texture(lightTex, offsetUv + dist).rgb * falloff * 0.25;
            }
        }
    }

    // Add some dithering to prevent color banding
    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
    light += mix(-dithering / 255.0, dithering / 255.0, noise);

    fragColor = vec4(light, 1.0);
}