#version 330 core

in vec2 uv;

out ivec4 fragColor;

uniform sampler2D sceneTex;

void main()
{
    float alpha = texture(sceneTex, uv).a;
    ivec2 externalSeed = (alpha > 0.5) ? ivec2(gl_FragCoord.xy) : ivec2(-1);
    ivec2 internalseed  = (alpha <= 0.5) ? ivec2(gl_FragCoord.xy) : ivec2(-1);
    fragColor = ivec4(externalSeed, internalseed);
}