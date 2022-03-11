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
in uint color;
in float texIndex;

out vec4 vertexColor;
out vec2 textureArrayCoord;
out vec2 textureCoord;
out float textureIndex;
out float quadCornerRadius;
out vec2 quadSize;

uniform mat4 view;
uniform mat4 projection;

vec4 getColor(uint rgba) {
    uint r = ((rgba >> uint(24)) & uint(255));
    uint g = ((rgba >> uint(16)) & uint(255));
    uint b = ((rgba >> uint(8))  & uint(255));
    uint a = (rgba & uint(255));
    return vec4(r, g, b, a) / 255.0f;
}

mat2 rotationMatrix(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(
        c, s,
        -s,	c
    );
}

void main() {
    vertexColor = getColor(color);
    textureArrayCoord = uvMin + (uvMax - uvMin) * vertexPos;
    textureCoord = vertexPos;
    textureIndex = texIndex;
    quadCornerRadius = cornerRadius;
    quadSize = size;

    vec2 offset = (vertexPos * size - size * origin) * rotationMatrix(radians(rotation));
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = projection * view * vertexPos;
}