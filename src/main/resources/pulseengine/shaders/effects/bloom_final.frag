#version 330 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D srcTex;
uniform sampler2D bloomTex;
uniform sampler2DArray lensDirtTexArray;

uniform vec3  lensDirtTexCoord;
uniform float lensDirtIntensity;

void main()
{
    vec4 srcColor = texture(srcTex, uv);
    vec3 bloomColor = texture(bloomTex, uv).rgb;

    if (lensDirtTexCoord.z >= 0.0)
    {
        vec3 dirtColor = texture(lensDirtTexArray, lensDirtTexCoord * vec3(uv, 1.0)).rgb;
        bloomColor += bloomColor * dirtColor * lensDirtIntensity;
    }

    fragColor = vec4(srcColor.rgb + bloomColor, srcColor.a);
}