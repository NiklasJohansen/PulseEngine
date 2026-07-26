#version 150 core

in vec4 vertexColor;
in vec2 texCoord;
in float texIndex;
flat in uint samplerIndex;

out vec4 fragColor;

uniform sampler2DArray textureArrays[16];

const float SDF_EDGE_VALUE = 128.0 / 255.0;
const float SDF_SMOOTHING = 0.85;

// Dynamic indexing of sampler arrays are not supported in GLSL bellow version 4.0, thus this atrocity
vec4 sampleTextureArray(int index, vec3 texCoords)
{
    switch (index)
    {
        case 0:  return texture(textureArrays[0],  texCoords);
        case 1:  return texture(textureArrays[1],  texCoords);
        case 2:  return texture(textureArrays[2],  texCoords);
        case 3:  return texture(textureArrays[3],  texCoords);
        case 4:  return texture(textureArrays[4],  texCoords);
        case 5:  return texture(textureArrays[5],  texCoords);
        case 6:  return texture(textureArrays[6],  texCoords);
        case 7:  return texture(textureArrays[7],  texCoords);
        case 8:  return texture(textureArrays[8],  texCoords);
        case 9:  return texture(textureArrays[9],  texCoords);
        case 10: return texture(textureArrays[10], texCoords);
        case 11: return texture(textureArrays[11], texCoords);
        case 12: return texture(textureArrays[12], texCoords);
        case 13: return texture(textureArrays[13], texCoords);
        case 14: return texture(textureArrays[14], texCoords);
        case 15: return texture(textureArrays[15], texCoords);
        default: return vec4(0.0);
    }
}

void main()
{
    float signedDistance = sampleTextureArray(int(samplerIndex), vec3(texCoord, floor(texIndex))).a;
    vec2 distanceGradient = vec2(dFdx(signedDistance), dFdy(signedDistance));
    float screenSpaceWidth = max(length(distanceGradient) * SDF_SMOOTHING, 0.0001);
    float coverage = clamp((signedDistance - SDF_EDGE_VALUE) / screenSpaceWidth + 0.5, 0.0, 1.0);

    // Avoid writing depth for the transparent SDF padding while preserving the antialiased edge.
    if (coverage <= 0.0) discard;

    // Surface rendering uses straight-alpha blending.
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}