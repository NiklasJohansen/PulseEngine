#version 330 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D tex0;
uniform sampler2D tex1;

uniform float minReflectance;

void main()
{
    vec4 c0 = texture(tex0, uv);
    vec4 c1 = texture(tex1, uv);

    // Creates a path-to-white if albedo is 100% black
    if (length(c0.rgb) < minReflectance)
        c0.rgb = vec3(minReflectance);

    fragColor = vec4(c0.rgb * c1.rgb, c0.a);
}