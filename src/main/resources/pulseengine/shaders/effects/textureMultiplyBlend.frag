#version 330 core

in vec2 textureCoord;
out vec4 fragColor;

uniform sampler2D tex0;
uniform sampler2D tex1;

void main()
{
    vec4 tex0Color = texture(tex0, textureCoord);
    vec4 tex1Color = texture(tex1, textureCoord);
    fragColor = vec4(tex0Color.rgb * tex1Color.rgb, tex0Color.a);
}