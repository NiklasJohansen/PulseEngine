#version 330 core

layout(location = 0) in vec3 position;

uniform mat4 view;
uniform mat4 projection;

out vec3 worldDir;

void main()
{
    mat3 Rview = mat3(view);   // Strip translation from view (only rotation)
    mat3 R = transpose(Rview); // camera rotation in world space
    worldDir = R * position;   // World-space direction

    // Position the cube around the camera (no translation)
    gl_Position = projection * vec4(position, 1.0);
}


