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
    mat3 M  = mat3(model);
    vec3 N  = normalize(M * normal);
    vec3 T  = normalize(M * tangent);
    vec3 B0 = normalize(M * bitangent);

    T = normalize(T - N * dot(T, N));

    float sign = (dot(cross(N, T), B0) < 0.0) ? -1.0 : 1.0;
    vec3 B = normalize(cross(N, T)) * sign;

    vec4 worldPos = model * vec4(position, 1.0);
    
    vWorldPos = worldPos.xyz;
    vWorldNormal = N;
    vTBN = mat3(T, B, N);
    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);

    gl_Position = viewProjection * worldPos;
}