#version 330 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D tex0;
uniform sampler2D tex1;

uniform float bias;

void main()
{
    vec4 c0 = texture(tex0, uv);
    vec4 c1 = texture(tex1, uv);
    if (length(c0.rgb) < bias)
        c0 = vec4(vec3(bias), 1.0);

    fragColor = vec4(c0.rgb * c1.rgb, c0.a);
}