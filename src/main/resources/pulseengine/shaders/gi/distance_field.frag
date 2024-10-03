#version 150 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D jfaTexture;

void main()
{
    vec2 nearestSeed = texture(jfaTexture, uv).xy;
    float distance = clamp(distance(uv, nearestSeed), 0.0, 1.0);
    fragColor = vec4(vec3(distance), 1.0);
}