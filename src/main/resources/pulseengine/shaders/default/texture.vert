#version 150 core

in vec2 vertexPos;
in vec2 texCoord;

out vec2 textureCoord;
out vec4 vertexColor;

uniform mat4 viewProjection;
uniform vec3 position;
uniform vec2 size;
uniform vec2 origin;
uniform float rotation;
uniform int color;

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
    textureCoord = vec2(vertexPos.x, 1.0 - vertexPos.y);
    vertexColor = getColor(uint(color));

    vec2 offset = (vertexPos * size - size * origin) * rotationMatrix(rotation);
    vec4 vertexPos = vec4(position, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = viewProjection * vertexPos;
}
