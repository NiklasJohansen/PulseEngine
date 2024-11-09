#version 330 core

in vec2 uv;

layout(location=0) out vec4 outside;
layout(location=1) out vec4 inside;

uniform sampler2D jfaTex;
uniform sampler2D jfaTexInside;

void main()
{
    vec4 nearestSeed = texture(jfaTex, uv);
    vec4 nearestSeedInside = texture(jfaTexInside, uv);
    vec2 nearestLocalSeed = nearestSeed.xy;
    vec2 nearestGlobalSeed = nearestSeed.zw;
    vec2 nearestLocalSeedInside = nearestSeedInside.xy;

    float localDist = distance(uv, nearestLocalSeed);
    float globalDist = distance(uv, nearestGlobalSeed);
    vec2 localDistInsideVector = nearestLocalSeedInside - uv;

    outside = vec4(localDist, globalDist, 0.0, 1.0);
    inside = vec4(localDistInsideVector, 0.0, 1.0);
}