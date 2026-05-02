#version 330 core

in vec2 vTexCoord;

uniform sampler2DArray textureArrays[16];
uniform vec4 uAlbedoTex;
uniform float uAlphaCutoff;

vec4 sampleTexOrDefault(vec4 texDesc)
{
    int samplerIndex = int(texDesc.x);
    if (samplerIndex < 0)
        return vec4(1.0);

    return texture(textureArrays[samplerIndex], vec3(vTexCoord * texDesc.zw, texDesc.y));
}

void main()
{
    if (uAlphaCutoff > 0.0 && sampleTexOrDefault(uAlbedoTex).a < uAlphaCutoff)
        discard;
}