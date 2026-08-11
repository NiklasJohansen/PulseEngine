#version 330 core

in vec2 uv;

layout(location = 0) out vec4 fragColor;
#ifdef WBOIT_OUTPUT_RENDER_ID
layout(location = 1) out uint outRenderId;
#endif

uniform sampler2D uAccumTex;
uniform sampler2D uRevealageTex;
#ifdef WBOIT_OUTPUT_RENDER_ID
uniform usampler2D uRenderIdTex;
#endif

void main()
{
    vec4 accum = texture(uAccumTex, uv);
    float revealage = clamp(texture(uRevealageTex, uv).r, 0.0, 1.0);
    float alpha = 1.0 - revealage;

    if (alpha <= 0.0001)
        discard;

    vec3 color = accum.rgb / max(accum.a, 0.00001);
    fragColor = vec4(color, alpha);

    #ifdef WBOIT_OUTPUT_RENDER_ID
    outRenderId = texture(uRenderIdTex, uv).r;
    #endif
}
