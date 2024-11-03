#version 150 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D jfaTex;

void main()
{
    vec4 nearestSeed = texture(jfaTex, uv);
    vec2 nearestLocalSeed = nearestSeed.xy;
    vec2 nearestGlobalSeed = nearestSeed.zw;

    float localDist = distance(uv, nearestLocalSeed);
    float globalDist = distance(uv, nearestGlobalSeed);

    fragColor = vec4(localDist, globalDist, 0, 1.0);
}