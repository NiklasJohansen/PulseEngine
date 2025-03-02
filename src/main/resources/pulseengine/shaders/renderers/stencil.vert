#version 150 core

in vec2 vertexPos;

uniform mat4 viewProjection;
uniform vec4 posAndSize;

void main()
{
    vec2 offset = (vertexPos * posAndSize.zw);
    vec4 vertexPos = vec4(posAndSize.xy, 0.0, 1.0) + vec4(offset, 0.0, 0.0);
    gl_Position = viewProjection * vertexPos;
}
