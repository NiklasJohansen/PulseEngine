#version 430 core

layout(location = 0) in vec3 position;
layout(location = 3) in vec2 texCoord;
layout(location = 4) in uvec4 boneIndices;
layout(location = 5) in vec4 boneWeights;

#if USE_INSTANCE_INDEX_ATTRIBUTE
layout(location = 6) in uint aInstanceIndex;
#endif

uniform mat4 viewProjection;

#if USE_INSTANCE_OFFSET_UNIFORM
uniform int uInstanceOffset;
#endif

struct InstanceData
{
    mat4 model;
    mat3 normalMatrix;
    vec4 params; // x=materialId, y=boneOffset, z=handedness, w=reserved
};

layout(std430, binding = 1) readonly buffer InstanceBuffer
{
    InstanceData uInstances[];
};

layout(std430, binding = 3) readonly buffer BoneBuffer
{
    mat4 uBoneMatrices[];
};

out vec2 vTexCoord;
flat out int vMaterialId;

void accumulateBoneInfluence(int boneOffset, int boneIndex, float boneWeight, inout vec4 skinnedPosition, inout float totalWeight)
{
    if (boneWeight <= 0.0) return;

    skinnedPosition += (uBoneMatrices[boneOffset + boneIndex] * vec4(position, 1.0)) * boneWeight;
    totalWeight += boneWeight;
}

void main()
{
    InstanceData instance = uInstances[MODEL_INSTANCE_INDEX];

    int boneOffset = int(instance.params.y);
    vec4 skinnedPosition = vec4(0.0);
    float totalWeight = 0.0;

    accumulateBoneInfluence(boneOffset, int(boneIndices.x), boneWeights.x, skinnedPosition, totalWeight);
    accumulateBoneInfluence(boneOffset, int(boneIndices.y), boneWeights.y, skinnedPosition, totalWeight);
    accumulateBoneInfluence(boneOffset, int(boneIndices.z), boneWeights.z, skinnedPosition, totalWeight);
    accumulateBoneInfluence(boneOffset, int(boneIndices.w), boneWeights.w, skinnedPosition, totalWeight);

    if (totalWeight <= 0.0)
        skinnedPosition = vec4(position, 1.0);

    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);
    vMaterialId = int(instance.params.x);

    gl_Position = viewProjection * instance.model * skinnedPosition;
}