#version 330 core

in vec2 uv;
layout(location=0) out vec4 externalOut;
layout(location=1) out vec4 internalOut;

uniform sampler2D localSceneTex;
uniform sampler2D globalSceneTex;

void main()
{
    float localAlpha = texture(localSceneTex, uv).a;
    float globalAlpha = texture(globalSceneTex, uv).a;

    externalOut = vec4(
        uv * clamp(localAlpha, 0.0, 1.0),
        uv * clamp(globalAlpha, 0.0, 1.0)
    );

    internalOut = vec4(
        uv * clamp(1.0 - localAlpha, 0.0, 1.0),
        0.0,
        0.0
    );
}