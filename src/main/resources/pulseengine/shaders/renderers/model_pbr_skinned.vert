#version 430 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec4 tangent;
layout(location = 3) in vec2 texCoord;
layout(location = 4) in uvec4 boneIndices;
layout(location = 5) in vec4 boneWeights;

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

layout(std430, binding = 3) readonly buffer BoneBuffer
{
    mat4 uBoneMatrices[];
};

out vec3 vWorldPos;
out vec3 vWorldNormal;
out mat3 vTBN;
out vec2 vTexCoord;
flat out int vMaterialId;

void accumulateBoneInfluence(
    int boneOffset,
    int boneIndex,
    float boneWeight,
    inout vec4 skinnedPosition,
    inout vec3 skinnedNormal,
    inout vec3 skinnedTangent,
    inout float totalWeight
) {
    if (boneWeight <= 0.0) return;

    mat4 boneMatrix = uBoneMatrices[boneOffset + boneIndex];
    mat3 boneBasis = mat3(boneMatrix);

    skinnedPosition  += (boneMatrix * vec4(position, 1.0)) * boneWeight;
    skinnedNormal    += (boneBasis * normal) * boneWeight;
    skinnedTangent   += (boneBasis * tangent.xyz) * boneWeight;
    totalWeight += boneWeight;
}

void main()
{
    InstanceData instance = uInstances[MODEL_INSTANCE_INDEX];

    int boneOffset = int(instance.params.y);
    vec4 skinnedPosition = vec4(0.0);
    vec3 skinnedNormal = vec3(0.0);
    vec3 skinnedTangent = vec3(0.0);
    float totalWeight = 0.0;

    accumulateBoneInfluence(boneOffset, int(boneIndices.x), boneWeights.x, skinnedPosition, skinnedNormal, skinnedTangent, totalWeight);
    accumulateBoneInfluence(boneOffset, int(boneIndices.y), boneWeights.y, skinnedPosition, skinnedNormal, skinnedTangent, totalWeight);
    accumulateBoneInfluence(boneOffset, int(boneIndices.z), boneWeights.z, skinnedPosition, skinnedNormal, skinnedTangent, totalWeight);
    accumulateBoneInfluence(boneOffset, int(boneIndices.w), boneWeights.w, skinnedPosition, skinnedNormal, skinnedTangent, totalWeight);

    if (totalWeight <= 0.0)
    {
        skinnedPosition = vec4(position, 1.0);
        skinnedNormal = normal;
        skinnedTangent = tangent.xyz;
    }

    mat4 model = instance.model;
    mat3 M = mat3(model);
    vec3 N = normalize(transpose(inverse(M)) * skinnedNormal);
    vec3 T = normalize(M * skinnedTangent);

    T = normalize(T - N * dot(T, N));

    float sign = tangent.w * ((determinant(M) < 0.0) ? -1.0 : 1.0);
    vec3 B = normalize(cross(N, T)) * sign;

    vec4 worldPos = model * skinnedPosition;

    vWorldPos = worldPos.xyz;
    vWorldNormal = N;
    vTBN = mat3(T, B, N);
    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);
    vMaterialId = int(instance.params.x);

    gl_Position = uViewProjection * worldPos;
}
