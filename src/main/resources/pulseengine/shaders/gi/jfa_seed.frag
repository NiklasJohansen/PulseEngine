#version 150 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D inputTexture;

void main()
{
    float alpha = texture(inputTexture, uv).a;
    fragColor = vec4(uv * clamp(alpha, 0.0, 1.0), 0.0, 1.0);
}