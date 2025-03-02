#version 150 core

in vec2 position;
in vec2 texCoord;

out vec2 uv;

void main()
{
    uv = texCoord;
    gl_Position = vec4(position, 0.0, 1.0);
}