#version 330 core

// Vertex attributes
in vec2 vertexPos; // In range (0-1)

// Instance attributes
in vec3 worldPos;
in vec2 size;
in vec2 origin;
in float angle;
in float cornerRadius;
in vec2 uvMin;
in vec2 uvMax;
in vec2 tiling;
in uint color;
in uint texHandle;

out vec4 vertexColor;
out vec2 texStart;
out vec2 texSize;
out vec2 texCoord;
out vec2 texTiling;
out vec2 quadSize;
out float quadCornerRadius;
flat out uint samplerIndex;
out float texIndex;

uniform mat4 viewProjection;

vec4 unpackAndConvert(uint rgba)
{
    // Unpack the rgba color and convert it from sRGB to linear space
    vec4 sRgba = vec4((rgba >> 24u) & 255u, (rgba >> 16u) & 255u, (rgba >> 8u) & 255u, rgba & 255u) / 255.0;
    vec3 lowRange = sRgba.rgb / 12.92;
    vec3 highRange = pow((sRgba.rgb + 0.055) / 1.055, vec3(2.4));
    vec3 linearRgb = mix(highRange, lowRange, lessThanEqual(sRgba.rgb, vec3(0.0031308)));
    return vec4(linearRgb, sRgba.a);
}

uint getSamplerIndex(uint textureHandle)
{
    return (textureHandle >> uint(16)) & ((uint(1) << uint(16)) - uint(1));
}

float getTexIndex(uint textureHandle)
{
    return float(textureHandle & ((uint(1) << uint(16)) - uint(1)));
}

mat2 rotate(float angle)
{
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, s, -s, c);
}

void main()
{
    vertexColor = unpackAndConvert(color);
    texStart = uvMin;
    texSize = uvMax - uvMin;
    texCoord = vertexPos;
    texTiling = tiling;
    quadSize = size;
    quadCornerRadius = cornerRadius;

    samplerIndex = getSamplerIndex(texHandle);
    texIndex = getTexIndex(texHandle);

    vec2 offset = (vertexPos - origin) * size * rotate(radians(angle));
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = viewProjection * vertexPos;
}