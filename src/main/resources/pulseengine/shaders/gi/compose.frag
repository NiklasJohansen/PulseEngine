#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D sceneTex;
uniform sampler2D sceneMetadataTex;
uniform sampler2D lightTex;
uniform sampler2D internalDistanceFieldTex;

uniform vec2 sampleOffset;
uniform float dithering;
uniform vec2 resolution;
uniform float scale;
uniform float sourceMultiplier;
uniform vec4 occluderAmbientLight;

void main()
{
    vec2 offsetUv = uv + sampleOffset;

    vec3 light = texture(lightTex, offsetUv).rgb;
    vec4 scene = texture(sceneTex, offsetUv);
    vec4 sceneMeta = texture(sceneMetadataTex, offsetUv);

    bool isOccluder = scene.a > 0.5;
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

            vec2 o = vec2(0.002 * scale, 0.0);
            vec2 offset[8] = vec2[8](+o.xy, -o.xy, +o.yx, -o.yx, +o.xx, -o.xx, +o.yy, -o.yy);
            float edgeLightStrengt = sceneMeta.a;

            for (int i = 0; i < 8; i++)
            {
                vec2 dist = texture(internalDistanceFieldTex, offsetUv + offset[i]).xy;
                float falloff = (edgeLightStrengt * 0.01) / (1.0 + length(dist / scale) * 100.0);
                falloff = clamp(falloff, 0.0, 1.0);
                falloff = pow(falloff, 3.0);
                light += texture(lightTex, offsetUv + dist * 1.2).rgb * falloff * 0.125;
            }
        }
    }

    // Add some dithering to prevent color banding
    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
    light += mix(-dithering / 255.0, dithering / 255.0, noise);

    fragColor = vec4(light, 1.0);
}