#version 330 core

// Vertex attributes
in vec2 vertexPos;

// Instance attributes
in vec3 worldPos;
in vec2 size;
in vec2 origin;
in float rotation;
in vec2 uvMin;
in vec2 uvMax;
in uint color;
in float texIndex;

out vec4 vertexColor;
out vec2 textureCoord;
out float textureIndex;

uniform mat4 view;
uniform mat4 projection;

uniform mat4 viewProjection;

vec4 getColor(uint rgba) {
    uint r = ((rgba >> uint(24)) & uint(255));
    uint g = ((rgba >> uint(16)) & uint(255));
    uint b = ((rgba >> uint(8))  & uint(255));
    uint a = (rgba & uint(255));
    return vec4(r, g, b, a) / 255.0f;
}

mat4 rotateZ( in float angle ) {
    return mat4(
        cos(angle),	-sin(angle), 0,	0,
        sin(angle),	cos(angle),	0,	0,
        0,	0,	1,	0,
        0,	0,	0,	1
    );
}

void main() {
    textureIndex = texIndex;
    vertexColor = getColor(color);
    textureCoord = uvMin + (uvMax - uvMin) * vertexPos;

    vec4 vertex = vec4(worldPos, 1.0) + vec4(vertexPos * size - size * origin, 0, 0) * rotateZ(radians(rotation));

    gl_Position = projection * view * vertex;
}


















