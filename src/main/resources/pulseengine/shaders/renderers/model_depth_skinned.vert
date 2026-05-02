#version 330 core

layout(location = 0) in vec3 position;
layout(location = 4) in vec2 texCoord;
layout(location = 5) in vec4 boneIndices;
layout(location = 6) in vec4 boneWeights;

uniform mat4 viewProjection;
uniform mat4 model;
uniform mat4 uBoneMatrices[128];

out vec2 vTexCoord;

void accumulateBoneInfluence(int boneIndex, float boneWeight, inout vec4 skinnedPosition, inout float totalWeight)
{
    if (boneWeight <= 0.0) return;

    skinnedPosition += (uBoneMatrices[boneIndex] * vec4(position, 1.0)) * boneWeight;
    totalWeight += boneWeight;
}

void main()
{
    vec4 skinnedPosition = vec4(0.0);
    float totalWeight = 0.0;

    accumulateBoneInfluence(int(boneIndices.x), boneWeights.x, skinnedPosition, totalWeight);
    accumulateBoneInfluence(int(boneIndices.y), boneWeights.y, skinnedPosition, totalWeight);
    accumulateBoneInfluence(int(boneIndices.z), boneWeights.z, skinnedPosition, totalWeight);
    accumulateBoneInfluence(int(boneIndices.w), boneWeights.w, skinnedPosition, totalWeight);

    if (totalWeight <= 0.0)
        skinnedPosition = vec4(position, 1.0);

    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);
    
    gl_Position = viewProjection * model * skinnedPosition;
}