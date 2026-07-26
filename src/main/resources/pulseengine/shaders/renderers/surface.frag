#version 150 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D tex;

uniform bool isDepthTexture;
uniform bool isPremultipliedAlpha;

void main()
{
    vec4 c = texture(tex, uv);

    if (isDepthTexture)
        c.rgb = vec3(c.r);

    // The back-buffer compositor uses premultiplied-alpha blending
    if (!isPremultipliedAlpha)
        c.rgb *= c.a;

    fragColor = clamp(c, 0.0, 1.0);
}