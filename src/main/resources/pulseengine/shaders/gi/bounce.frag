#version 420 core

in vec2 uv;
in vec2 uvLastFrame;

layout(location=0) out vec4 sceneColor;
layout(location=1) out vec4 sceneMetadata;

layout(binding=0) uniform sampler2D sceneTex;
layout(binding=1) uniform sampler2D sceneMetaTex;
layout(binding=2) uniform sampler2D lightTex;

uniform bool lightBounce;

void main()
{
    vec4 scene = texture(sceneTex, uv);
    vec4 sceneMeta = texture(sceneMetaTex, uv);
    float sourceIntensity = sceneMeta.b;

    float bounceIntensity = 1.2;
    float bounceDecay = 0.5;
    float bounceSampleRadius = 0.0005;

    if (lightBounce && scene.a > 0.1 && sourceIntensity < 0.1)
    {
        vec2 offset = vec2(bounceSampleRadius, 0);
        vec4 lb0 = texture(lightTex, uvLastFrame + offset.xy);
        vec4 lb1 = texture(lightTex, uvLastFrame - offset.xy);
        vec4 lb2 = texture(lightTex, uvLastFrame + offset.yx);
        vec4 lb3 = texture(lightTex, uvLastFrame - offset.yx);
        vec4 lb4 = texture(lightTex, uvLastFrame + offset.xx);
        vec4 lb5 = texture(lightTex, uvLastFrame - offset.xx);
        vec4 lb6 = texture(lightTex, uvLastFrame + offset.yy);
        vec4 lb7 = texture(lightTex, uvLastFrame - offset.yy);

        vec4 lightBounceMax0 = max(max(lb0, lb1), max(lb2, lb3));
        vec4 lightBounceMax1 = max(max(lb4, lb5), max(lb6, lb7));
        vec4 lightBounce = max(lightBounceMax0, lightBounceMax1);

        scene.rgb *= lightBounce.rgb * bounceDecay;
        sceneMeta.b = bounceIntensity;
    }

    sceneColor = scene;
    sceneMetadata = sceneMeta;
}