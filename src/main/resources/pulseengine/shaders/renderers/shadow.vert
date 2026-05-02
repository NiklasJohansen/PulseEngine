#version 330 core

layout(location = 0) in vec3 position;
layout(location = 4) in vec2 texCoord;

uniform mat4 viewProjection;
uniform mat4 model;

out vec2 vTexCoord;

void main() 
{
    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);
    gl_Position = viewProjection * model * vec4(position, 1.0);
}