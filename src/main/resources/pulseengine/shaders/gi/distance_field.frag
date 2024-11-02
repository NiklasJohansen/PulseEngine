#version 150 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D jfaTexture;

void main()
{
    vec2 nearestSeed = texture(jfaTexture, uv).xy;
    float distance = distance(uv, nearestSeed);
    fragColor = vec4(distance, 0, 0, 1.0);
}