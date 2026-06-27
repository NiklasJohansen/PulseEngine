#version 430 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec4 tangent;
layout(location = 3) in vec2 texCoord;

#if USE_INSTANCE_INDEX_ATTRIBUTE
layout(location = 6) in uint aInstanceIndex;
#endif

uniform mat4 uViewProjection;

#if USE_INSTANCE_OFFSET_UNIFORM
uniform int uInstanceOffset;
#endif

struct InstanceData
{
    mat4 model;
    vec4 params; // x=materialId, y=boneOffset, z/w=reserved
};

layout(std430, binding = 1) readonly buffer InstanceBuffer
{
    InstanceData uInstances[];
};

out vec3 vWorldPos;
out vec3 vWorldNormal;
out mat3 vTBN;
out vec2 vTexCoord;
flat out int vMaterialId;

void main()
{
    InstanceData instance = uInstances[MODEL_INSTANCE_INDEX];

    mat4 model = instance.model;
    mat3 M  = mat3(model);
    vec3 N  = normalize(transpose(inverse(M)) * normal);
    vec3 T  = normalize(M * tangent.xyz);

    T = normalize(T - N * dot(T, N));

    float sign = tangent.w * ((determinant(M) < 0.0) ? -1.0 : 1.0);
    vec3 B = normalize(cross(N, T)) * sign;

    vec4 worldPos = model * vec4(position, 1.0);

    vWorldPos = worldPos.xyz;
    vWorldNormal = N;
    vTBN = mat3(T, B, N);
    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);
    vMaterialId = int(instance.params.x);

    gl_Position = uViewProjection * worldPos;
}
