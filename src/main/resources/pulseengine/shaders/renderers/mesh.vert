#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec3 tangent;
layout(location = 3) in vec3 bitangent;
layout(location = 4) in vec2 texCoord;

uniform mat4 viewProjection;
uniform mat4 model;

out vec3 vWorldPos;
out vec3 vWorldNormal;
out mat3 vTBN;
out vec2 vTexCoord;

void main()
{
    mat3 M = mat3(model);
    vec3 N = normalize(M * normal);
    vec3 T = normalize(M * tangent);
    vec3 B = normalize(M * bitangent);

    // Re-orthogonalize T to N
    T = normalize(T - dot(T, N) * N);

    vec4 worldPos = model * vec4(position, 1.0);
    
    vWorldPos = worldPos.xyz;
    vWorldNormal = N;
    vTBN = mat3(T, B, N);
    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);

    gl_Position = viewProjection * worldPos;
}