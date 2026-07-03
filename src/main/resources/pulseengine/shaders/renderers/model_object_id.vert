#version 430 core

layout(location = 0) in vec3 position;
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
    mat3 normalMatrix;
    vec4 params;
};

layout(std430, binding = 1) readonly buffer InstanceBuffer
{
    InstanceData uInstances[];
};

layout(std430, binding = 12) readonly buffer ObjectIdBuffer
{
    ivec2 uObjectIds[];
};

out vec2 vTexCoord;
flat out int vMaterialId;
flat out ivec2 vObjectId;

void main()
{
    uint instanceIndex = MODEL_INSTANCE_INDEX;
    InstanceData instance = uInstances[instanceIndex];

    vMaterialId = int(instance.params.x);
    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);
    vObjectId = uObjectIds[instanceIndex];
    gl_Position = uViewProjection * instance.model * vec4(position, 1.0);
}