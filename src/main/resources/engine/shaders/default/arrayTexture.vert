// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec3 position;
in vec2 offset;
in float rotation;
in vec2 texCoord;
in float texIndex;
in uint color;

out vec4 vertexColor;
out vec2 textureCoord;
out float textureIndex;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform bool isAlphaTex;

vec4 getColor(uint rgba) {
    float r = ((rgba >> uint(24)) & uint(255)) / 255f;
    float g = ((rgba >> uint(16)) & uint(255)) / 255f;
    float b = ((rgba >> uint(8))  & uint(255)) / 255f;
    float a = (rgba & uint(255)) / 255f;
    return vec4(r, g, b, a);
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
    vertexColor = getColor(color);
    textureCoord = texCoord;
    textureIndex = texIndex;

    vec4 vertex = vec4(2 * offset, 0.0, 1.0) * rotateZ(radians(rotation)) + vec4(2 * position, 1.0);

    mat4 mvp = projection * view * model;

    gl_Position = mvp * vertex;
}





























