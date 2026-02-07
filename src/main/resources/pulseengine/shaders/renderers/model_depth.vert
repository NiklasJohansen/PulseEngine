#version 330 core

layout(location = 0) in vec3 position;

uniform mat4 viewProjection;
uniform mat4 model;

void main() 
{
    gl_Position = viewProjection * model * vec4(position, 1.0);
}