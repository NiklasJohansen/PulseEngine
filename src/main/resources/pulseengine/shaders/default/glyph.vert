#version 330 core

// Vertex attributes
in vec2 vertexPos; // In range (0-1)

// Instance attributes
in vec3 worldPos;
in vec2 size;
in float rotation;
in vec2 uvMin;
in vec2 uvMax;
in uint color;
in float textureIndex;

out vec4 vertexColor;
out vec2 texCoord;
out float texIndex;

uniform mat4 viewProjection;

vec4 getColor(uint rgba) {
    uint r = ((rgba >> uint(24)) & uint(255));
    uint g = ((rgba >> uint(16)) & uint(255));
    uint b = ((rgba >> uint(8))  & uint(255));
    uint a = (rgba & uint(255));
    return vec4(r, g, b, a) / 255.0f;
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
    texCoord = uvMin + (uvMax - uvMin) * vertexPos;
    texIndex = textureIndex;

    vec2 offset = vertexPos * size * rotate(radians(rotation));
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = viewProjection * vertexPos;
}