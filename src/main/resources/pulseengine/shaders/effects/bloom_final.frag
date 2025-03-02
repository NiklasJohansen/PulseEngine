#version 330 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D srcTex;
uniform sampler2D bloomTex;
uniform sampler2DArray texArray;

uniform vec2  dirtTexUv;
uniform float dirtTexIndex;
uniform float dirtTexIntensity;

void main()
{
    vec4 srcColor = texture(srcTex, uv);
    vec3 bloomColor = texture(bloomTex, uv).rgb;

    if (dirtTexIntensity > 0)
    {
        vec3 dirtColor = texture(texArray, vec3(uv * dirtTexUv, dirtTexIndex)).rgb;
        bloomColor += bloomColor * dirtColor * dirtTexIntensity;
    }

    fragColor = vec4(srcColor.rgb + bloomColor, srcColor.a);
}