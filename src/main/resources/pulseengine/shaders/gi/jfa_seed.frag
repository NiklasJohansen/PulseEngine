#version 420 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D localSceneTex;
uniform sampler2D globalSceneTex;

void main()
{
    float localAlpha = texture(localSceneTex, uv).a;
    float globalAlpha = texture(globalSceneTex, uv).a;

    fragColor = vec4(
        uv * clamp(localAlpha, 0.0, 1.0),
        uv * clamp(globalAlpha, 0.0, 1.0)
    );
}