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
in vec2 scale;

out vec2 texSize;
out vec2 texStart;
out vec2 texCoord;
out vec2 texTiling;
out float texIndex;
flat out uint texSamplerIndex;
out mat2 normalRotation;
out vec2 normalScale;

uniform float cameraAngle;
uniform mat4 viewProjection;

uint getTexSamplerIndex(uint textureHandle)
{
    return (textureHandle >> uint(16)) & ((uint(1) << uint(16)) - uint(1));
}

float getTexIndex(uint textureHandle)
{
    return float(textureHandle & ((uint(1) << uint(16)) - uint(1)));
}

mat2 rotMatrix(float angle)
{
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, s, -s, c);
}

void main()
{
    texStart = uvMin;
    texSize = uvMax - uvMin;
    texCoord = vertexPos;
    texTiling = tiling;
    texIndex = getTexIndex(textureHandle);
    texSamplerIndex = getTexSamplerIndex(textureHandle);

    float angle = radians(rotation);
    normalRotation = rotMatrix(angle + cameraAngle);
    normalScale = scale;

    vec2 offset = (vertexPos - origin) * size * rotMatrix(angle);
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = viewProjection * vertexPos;
}