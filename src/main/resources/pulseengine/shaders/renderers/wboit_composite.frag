#version 150 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D uAccumTex;
uniform sampler2D uRevealageTex;

void main()
{
    vec4 accum = texture(uAccumTex, uv);
    float revealage = clamp(texture(uRevealageTex, uv).r, 0.0, 1.0);
    float alpha = 1.0 - revealage;

    if (alpha <= 0.0001)
        discard;

    vec3 color = accum.rgb / max(accum.a, 0.00001);
    fragColor = vec4(color, alpha);
}
