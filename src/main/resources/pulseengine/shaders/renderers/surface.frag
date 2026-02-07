#version 150 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D tex;

uniform bool isDepthTexture;

void main()
{
    vec4 c = texture(tex, uv);

    if (isDepthTexture)
    {
        textureColor.rgb = vec3(textureColor.r);
    }

    fragColor = clamp(c, 0.0, 1.0);
}