#version 420 core

#define PI 3.1415926538
#define RADIAL_LIGHT_TYPE 1
#define LINEAR_LIGHT_TYPE 2

// Vertex attributes
in vec2 vertexPos;

// Instance attributes
in vec3 position;
in float radius;
in float directionAngle;
in float coneAngle;
in float size;
in uint color;
in float intensity;
in float spill;
in int flags;
in float edgeIndex;
in float edgeCount;

out vec2 lightPosition;
out vec2 lightCoord;
out vec2 lightCoordFixedRot;
out float lightDepth;
out float lightRadius;
out float lightDirectionAngle;
out float lightConeAngle;
out float lightSize;
out vec4 lightColor;
out float lightIntensity;
out float lightSpill;
flat out int lightFlags;
out float lightEdgeIndex;
out float lightEdgeCount;

uniform mat4 view;
uniform mat4 projection;
uniform vec2 resolution;
uniform float textureScale;
uniform vec2 drawOffset;

vec4 getColor(uint rgba) {
    uint r = ((rgba >> uint(24)) & uint(255));
    uint g = ((rgba >> uint(16)) & uint(255));
    uint b = ((rgba >> uint(8))  & uint(255));
    uint a = (rgba & uint(255));
    return vec4(r, g, b, a) / 255.0f;
}

mat2 rotateZ( in float angle ) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(
        c, s,
        -s,	c
    );
}

void main() {
    // Calculate offsets to compensate for jitter when textureScale is below 0
    vec2 lightOnScreenPosOffset = -drawOffset * textureScale;
    vec2 lightVertexPosOffset = drawOffset / (resolution / textureScale * vec2(-0.5, 0.5));

    // Normalized vertex positions
    vec2 vertex = vertexPos * 2.0 - 1.0;
    vec2 rotatedVertex = vertex * rotateZ(directionAngle);

    // Coordinate on light quad (-1 to 1)
    lightCoord = vertex;

    // Cordinate on light quad (-1, 1), but fixed in relation to quads rotation
    lightCoordFixedRot = rotatedVertex;

    // Position of light in screen space (0 to width/height)
    lightPosition = (view * vec4(position.xy, 0.0, 1.0) * textureScale).xy + lightOnScreenPosOffset;

    // Other ligth properties
    lightDepth = position.z;
    lightRadius = radius;
    lightDirectionAngle = directionAngle;
    lightConeAngle = coneAngle;
    lightSize = size;
    lightColor = getColor(color);
    lightIntensity = intensity;
    lightSpill = spill;
    lightFlags = flags;
    lightEdgeIndex = edgeIndex;
    lightEdgeCount = edgeCount;

    gl_Position = (projection * view * vec4(position.xy + (rotatedVertex) * radius, 0.0, 1.0)) + vec4(lightVertexPosOffset, 0f, 0f);
}





























