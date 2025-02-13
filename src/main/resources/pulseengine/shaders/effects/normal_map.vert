#version 330 core

// Vertex attributes
in vec2 vertexPos; // In range (0-1)

// Instance attributes
in vec3 worldPos;
in vec2 size;
in vec2 origin;
in float rotation;
in vec2 uvMin;
in vec2 uvMax;
in vec2 tiling;
in uint textureHandle;
in vec2 normalScale;

out vec2 texStart;
out vec2 texSize;
out vec2 texCoord;
out float texAngleRad;
out vec2 texTiling;
out vec2 quadSize;
out vec2 scale;
flat out uint samplerIndex;
out float texIndex;

uniform mat4 view;
uniform mat4 projection;

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
    return mat2(
        c, s,
        -s,	c
    );
}

void main()
{
    texStart = uvMin;
    texSize = uvMax - uvMin;
    texCoord = vertexPos;
    texAngleRad = radians(rotation);
    texTiling = tiling;
    quadSize = size;
    scale = normalScale;

    samplerIndex = getSamplerIndex(textureHandle);
    texIndex = getTexIndex(textureHandle);

    vec2 offset = (vertexPos * size - size * origin) * rotate(texAngleRad);
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = projection * view * vertexPos;
}