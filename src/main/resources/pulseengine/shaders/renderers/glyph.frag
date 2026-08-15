#version 150 core

in vec4 vertexColor;
in vec2 texCoord;
in float texIndex;
flat in uint samplerIndex;

out vec4 fragColor;

uniform sampler2DArray uTextureBanks[16];

const float SDF_EDGE_VALUE = 128.0 / 255.0;
const float SDF_SMOOTHING = 0.85;

// Use fixed sampler indices as some OpenGL drivers reject dynamic indexing of sampler arrays.
vec4 sampleTextureBankGrad(int index, vec3 texCoords, vec2 ddx, vec2 ddy)
{
    switch (index)
    {
        case 0:  return textureGrad(uTextureBanks[0],  texCoords, ddx, ddy);
        case 1:  return textureGrad(uTextureBanks[1],  texCoords, ddx, ddy);
        case 2:  return textureGrad(uTextureBanks[2],  texCoords, ddx, ddy);
        case 3:  return textureGrad(uTextureBanks[3],  texCoords, ddx, ddy);
        case 4:  return textureGrad(uTextureBanks[4],  texCoords, ddx, ddy);
        case 5:  return textureGrad(uTextureBanks[5],  texCoords, ddx, ddy);
        case 6:  return textureGrad(uTextureBanks[6],  texCoords, ddx, ddy);
        case 7:  return textureGrad(uTextureBanks[7],  texCoords, ddx, ddy);
        case 8:  return textureGrad(uTextureBanks[8],  texCoords, ddx, ddy);
        case 9:  return textureGrad(uTextureBanks[9],  texCoords, ddx, ddy);
        case 10: return textureGrad(uTextureBanks[10], texCoords, ddx, ddy);
        case 11: return textureGrad(uTextureBanks[11], texCoords, ddx, ddy);
        case 12: return textureGrad(uTextureBanks[12], texCoords, ddx, ddy);
        case 13: return textureGrad(uTextureBanks[13], texCoords, ddx, ddy);
        case 14: return textureGrad(uTextureBanks[14], texCoords, ddx, ddy);
        case 15: return textureGrad(uTextureBanks[15], texCoords, ddx, ddy);
        default: break;
    }
    return vec4(0.0);
}

void main()
{
    vec2 texCoordDx = dFdx(texCoord);
    vec2 texCoordDy = dFdy(texCoord);
    float signedDistance = sampleTextureBankGrad(int(samplerIndex), vec3(texCoord, floor(texIndex)), texCoordDx, texCoordDy).a;
    vec2 distanceGradient = vec2(dFdx(signedDistance), dFdy(signedDistance));
    float screenSpaceWidth = max(length(distanceGradient) * SDF_SMOOTHING, 0.0001);
    float coverage = clamp((signedDistance - SDF_EDGE_VALUE) / screenSpaceWidth + 0.5, 0.0, 1.0);

    // Avoid writing depth for the transparent SDF padding while preserving the antialiased edge.
    if (coverage <= 0.0) discard;

    // Surface rendering uses straight-alpha blending.
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}