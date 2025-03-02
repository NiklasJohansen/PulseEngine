#version 330 core

uniform sampler2D srcTexture;
uniform sampler2D bloomTexture;
uniform sampler2DArray textureArray;

uniform vec2  dirtTextureUv;
uniform float dirtTextureIndex;
uniform float dirtTextureIntensity;

in vec2 uv;
out vec4 fragColor;

void main()
{
    vec4 srcColor = texture(srcTexture, uv);
    vec3 bloomColor = texture(bloomTexture, uv).rgb;

    if (dirtTextureIntensity > 0)
    {
        vec3 dirtColor = texture(textureArray, vec3(uv * dirtTextureUv, dirtTextureIndex)).rgb;
        bloomColor += bloomColor * dirtColor * dirtTextureIntensity;
    }

    fragColor = vec4(srcColor.rgb + bloomColor, srcColor.a);
}