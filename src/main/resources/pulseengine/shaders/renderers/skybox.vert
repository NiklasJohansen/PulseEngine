#version 330 core

layout(location = 0) in vec3 position;

uniform mat4 view;
uniform mat4 projection;

out vec3 worldDir;

void main()
{
    worldDir = position;

    // Remove translation and use only camera rotation
    mat4 viewRotOnly = mat4(mat3(view));
    vec4 clipPos = projection * viewRotOnly * vec4(position, 1.0);

    // Force depth to far plane (1.0)
    gl_Position = vec4(clipPos.xy, clipPos.w, clipPos.w);
}