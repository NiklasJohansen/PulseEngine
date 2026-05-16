#version 430 core

layout(location = 0) in vec3 position;
layout(location = 4) in vec2 texCoord;

#if USE_INSTANCE_INDEX_ATTRIBUTE
layout(location = 7) in uint aInstanceIndex;
#endif

uniform mat4 viewProjection;

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

out vec2 vTexCoord;
flat out int vMaterialId;

void main()
{
    InstanceData instance = uInstances[MODEL_INSTANCE_INDEX];

    vMaterialId = int(instance.params.x);
    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);

    gl_Position = viewProjection * instance.model * vec4(position, 1.0);
}