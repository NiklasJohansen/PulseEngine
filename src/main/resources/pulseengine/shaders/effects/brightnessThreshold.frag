#version 150 core

in vec2 textureCoord;
out vec4 fragColor;

uniform sampler2D tex;
uniform float threshold;

void main()
{
    vec4 textureColor = texture(tex, textureCoord);
    float brightness = dot(textureColor.rgb, vec3(0.2126, 0.7152, 0.0722));

    if (brightness > threshold)
    {
        fragColor = vec4(textureColor.rgb, 1.0);
    }
    else
    {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
    }
}