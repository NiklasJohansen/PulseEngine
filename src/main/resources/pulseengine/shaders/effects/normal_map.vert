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
in float textureIndex;
in float normalScale;

out vec2 texStart;
out vec2 texSize;
out vec2 texCoord;
out float texAngleRad;
out vec2 texTiling;
out float texIndex;
out vec2 quadSize;
out float scale;

uniform mat4 view;
uniform mat4 projection;

mat2 rotate(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(
        c, s,
        -s,	c
    );
}

void main() {
    texStart = uvMin;
    texSize = uvMax - uvMin;
    texCoord = vertexPos;
    texAngleRad = radians(rotation);
    texTiling = tiling;
    texIndex = textureIndex;
    quadSize = size;
    scale = normalScale;

    vec2 offset = (vertexPos * size - size * origin) * rotate(texAngleRad);
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = projection * view * vertexPos;
}