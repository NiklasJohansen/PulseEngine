#version 150 core

#define NO_TEXTURE 65534

in vec4 vertexColor;
in vec2 quadSize;
in float quadCornerRadius;
in float sourceIntensity;
in float sourceAngle;
in float sourceConeAngle;
in float sourceRadius; // light radius / occluder edge light
in vec2 texStart;
in vec2 texSize;
in vec2 texCoord;
in float texIndex;
flat in uint texSamplerIndex;

out vec4 sceneColor;
out vec4 metadata;

uniform sampler2DArray textureArrays[16];

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
    if (quadCornerRadius > 0.0)
    {
        vec2 pos = texCoord * quadSize;
        float border = clamp(quadCornerRadius, 0.0, 0.5 * min(quadSize.x, quadSize.y));
        vec2 corner = clamp(pos, vec2(border), quadSize - border);
        float distFromCorner = length(pos - corner) - border;
        if (distFromCorner > 0.0)
            discard;
    }

    vec4 texColor = vec4(1.0, 1.0, 1.0, 1.0);
    if (texIndex != NO_TEXTURE)
    {
        texColor = sampleTextureArray(int(texSamplerIndex), vec3(texStart + texSize * texCoord, floor(texIndex)));
        if (texColor.a < 0.5)
            discard; // Discard transparent pixels
    }

    sceneColor = vertexColor * texColor;
    metadata = vec4(sourceConeAngle / 360.0, fract(sourceAngle / 360.0), sourceIntensity, sourceRadius);
}