#version 150 core

in vec3 position;
in uint rgbaColor;

out vec4 vertexColor;

uniform mat4 viewProjection;

vec4 getColor(uint rgba) {
    float r = ((rgba >> uint(24)) & uint(255));
    float g = ((rgba >> uint(16)) & uint(255));
    float b = ((rgba >> uint(8))  & uint(255));
    float a = (rgba & uint(255));
    return vec4(r, g, b, a) / 255.0f;
}

void main() {
    vertexColor = getColor(rgbaColor);
    gl_Position = viewProjection * vec4(position, 1.0);
}