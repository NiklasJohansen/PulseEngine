#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec3 tangent;
layout(location = 3) in vec3 bitangent;
layout(location = 4) in vec2 texCoord;
layout(location = 5) in vec4 boneIndices;
layout(location = 6) in vec4 boneWeights;

uniform mat4 uViewProjection;
uniform mat4 uModel;
uniform mat4 uBoneMatrices[128];

out vec3 vWorldPos;
out vec3 vWorldNormal;
out mat3 vTBN;
out vec2 vTexCoord;

void accumulateBoneInfluence(
    int boneIndex,
    float boneWeight,
    inout vec4 skinnedPosition,
    inout vec3 skinnedNormal,
    inout vec3 skinnedTangent,
    inout vec3 skinnedBitangent,
    inout float totalWeight
) {
    if (boneWeight <= 0.0) return;

    mat4 boneMatrix = uBoneMatrices[boneIndex];
    mat3 boneBasis = mat3(boneMatrix);

    skinnedPosition += (boneMatrix * vec4(position, 1.0)) * boneWeight;
    skinnedNormal += (boneBasis * normal) * boneWeight;
    skinnedTangent += (boneBasis * tangent) * boneWeight;
    skinnedBitangent += (boneBasis * bitangent) * boneWeight;
    totalWeight += boneWeight;
}

void main()
{
    vec4 skinnedPosition = vec4(0.0);
    vec3 skinnedNormal = vec3(0.0);
    vec3 skinnedTangent = vec3(0.0);
    vec3 skinnedBitangent = vec3(0.0);
    float totalWeight = 0.0;

    accumulateBoneInfluence(int(boneIndices.x), boneWeights.x, skinnedPosition, skinnedNormal, skinnedTangent, skinnedBitangent, totalWeight);
    accumulateBoneInfluence(int(boneIndices.y), boneWeights.y, skinnedPosition, skinnedNormal, skinnedTangent, skinnedBitangent, totalWeight);
    accumulateBoneInfluence(int(boneIndices.z), boneWeights.z, skinnedPosition, skinnedNormal, skinnedTangent, skinnedBitangent, totalWeight);
    accumulateBoneInfluence(int(boneIndices.w), boneWeights.w, skinnedPosition, skinnedNormal, skinnedTangent, skinnedBitangent, totalWeight);

    if (totalWeight <= 0.0)
    {
        skinnedPosition = vec4(position, 1.0);
        skinnedNormal = normal;
        skinnedTangent = tangent;
        skinnedBitangent = bitangent;
    }

    mat3 M = mat3(uModel);
    vec3 N = M * skinnedNormal;
    vec3 T = M * skinnedTangent;
    vec3 B0 = M * skinnedBitangent;

    T = normalize(T - N * dot(T, N));

    float sign = (dot(cross(N, T), B0) < 0.0) ? -1.0 : 1.0;
    vec3 B = normalize(cross(N, T)) * sign;

    vec4 worldPos = uModel * skinnedPosition;

    vWorldPos = worldPos.xyz;
    vWorldNormal = N;
    vTBN = mat3(T, B, N);
    vTexCoord = vec2(texCoord.x, 1.0 - texCoord.y);

    gl_Position = uViewProjection * worldPos;
}