#version 330 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D sceneTex;

void main()
{
    float alpha = texture(sceneTex, uv).a;
    vec2 outside = uv * clamp(alpha, 0.0, 1.0);
    vec2 inside = uv * clamp(1.0 - alpha, 0.0, 1.0);
    fragColor = vec4(outside, inside);
}