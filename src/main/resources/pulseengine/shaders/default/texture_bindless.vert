#version 330 core

// Vertex attributes
in vec2 vertexPos; // In range (0-1)

// Instance attributes
in vec3 worldPos;
in vec2 size;
in vec2 origin;
in float rotation;
in float cornerRadius;
in vec2 uvMin;
in vec2 uvMax;
in vec2 tiling;
in uint color;
in uint textureHandle;

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

vec4 getColor(uint rgba) {
    uint r = ((rgba >> uint(24)) & uint(255));
    uint g = ((rgba >> uint(16)) & uint(255));
    uint b = ((rgba >> uint(8))  & uint(255));
    uint a = (rgba & uint(255));
    return vec4(r, g, b, a) / 255.0f;
}

uint getSamplerIndex(uint textureHandle) {
    return (textureHandle >> uint(16)) & ((uint(1) << uint(16)) - uint(1));
}

float getTexIndex(uint textureHandle) {
    return float(textureHandle & ((uint(1) << uint(16)) - uint(1)));
}

mat2 rotate(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(
        c, s,
        -s,	c
    );
}

void main() {
    vertexColor = getColor(color);
    texStart = uvMin;
    texSize = uvMax - uvMin;
    texCoord = vertexPos;
    texTiling = tiling;
    quadSize = size;
    quadCornerRadius = cornerRadius;

    samplerIndex = getSamplerIndex(textureHandle);
    texIndex = getTexIndex(textureHandle);

    vec2 offset = (vertexPos * size - size * origin) * rotate(radians(rotation));
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = viewProjection * vertexPos;
}