#version 330 core

in vec2 uv;
in vec2 uvLastFrame;

layout(location=0) out vec4 sceneColor;
layout(location=1) out vec4 sceneMetadata;

uniform sampler2D sceneTex;
uniform sampler2D sceneMetaTex;
uniform sampler2D lightTex;

uniform float bounceAccumulation;
uniform float bounceEdgeFade;
uniform float bounceRadius;
uniform vec2 resolution;
uniform float scale;
uniform vec2 lightTexUvMax;

void main()
{
    vec4 scene = texture(sceneTex, uv);
    vec4 sceneMeta = texture(sceneMetaTex, uv);
    bool isOccluder = scene.a > 0.5;
    bool isLightSource = sceneMeta.b > 0; // sourceIntensity > 0

    if (bounceAccumulation > 0.0 && isOccluder && !isLightSource)
    {
        vec2 p = (1.0 / resolution) * scale;
        vec2 dir[8] = vec2[8](+p.xy, -p.xy, +p.yx, -p.yx, +p.xx, -p.xx, +p.yy, -p.yy);
        vec3 lightAcc = vec3(0);
        float n = 0;

        for (int i = 0; i < 8; i++)
        {
            bool isOpenSpace = texture(sceneTex, clamp(uv + dir[i], 0, 1)).a < 0.5;
            if (isOpenSpace)
            {
                lightAcc += texture(lightTex, clamp(lightTexUvMax * uvLastFrame + dir[i] * 4, 0, 1)).rgb;
                n++;
            }
        }

        // Fade out bounce light near the edge of the screen
        float edgeFade = 1.0 - smoothstep(0.5 * (1.0 - bounceEdgeFade), 0.5, max(abs(uv.x - 0.5), abs(uv.y - 0.5)));
        vec3 avgBounceLight = lightAcc / max(1.0, n);

        // Tint the bounce light based on the scene color
        float maxChannel = max(max(scene.r, scene.g), scene.b);
        vec3 tint = (maxChannel > 0.0) ? scene.rgb / maxChannel : vec3(1.0);

        scene.rgb = tint * avgBounceLight * bounceAccumulation * edgeFade;
        sceneMeta.a = bounceRadius; // sourceRadius
        sceneMeta.b = 1.0; // sourceIntensity = 1.0
    }

    sceneColor = scene;
    sceneMetadata = sceneMeta;
}