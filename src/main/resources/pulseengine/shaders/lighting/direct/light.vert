#version 330 core

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

// Outputs to fragment shader
out vec2 lightPos0;
out vec2 lightPos1;
out float lightDepth;
out float lightRadius;
out float lightDirectionAngle;
out float lightConeAngle;
out float lightSize;
out vec4 lightColor;
out float lightIntensity;
out float lightSpill;
flat out int lightFlags;
flat out int firstEdgeIndex;
flat out int lastEdgeIndex;

// Uniforms
uniform mat4 view;
uniform mat4 projection;
uniform vec2 resolution;
uniform float textureScale;
uniform vec2 drawOffset;
uniform float zRotation;

vec4 unpackAndConvert(uint rgba)
{
    // Unpack the rgba color and convert it from sRGB to linear space
    vec4 sRgba = vec4((rgba >> 24u) & 255u, (rgba >> 16u) & 255u, (rgba >> 8u) & 255u, rgba & 255u) / 255.0;
    vec3 lowRange = sRgba.rgb / 12.92;
    vec3 highRange = pow((sRgba.rgb + 0.055) / 1.055, vec3(2.4));
    vec3 linearRgb = mix(highRange, lowRange, lessThanEqual(sRgba.rgb, vec3(0.0031308)));
    return vec4(linearRgb, sRgba.a);
}

mat2 rotateZ(float angle)
{
    float c = cos(angle);
    float s = sin(angle);
    return mat2(
        c, s,
        -s,	c
    );
}

void main()
{
    // Scale light radius and size to screen space
    float m00 = view[0][0];
    float m01 = view[0][1];
    float m02 = view[0][2];
    float scale = sqrt(m00 * m00 + m01 * m01 + m02 * m02);
    lightRadius = radius * scale * textureScale;
    lightSize = size * scale * textureScale;

    // Ligth properties
    lightDepth = -0.1 * position.z;
    lightDirectionAngle = zRotation + directionAngle;
    lightConeAngle = coneAngle;
    lightColor = unpackAndConvert(color);
    lightIntensity = intensity;
    lightSpill = spill;
    lightFlags = flags;

    // Set the range of edges that are relevant for this light
    firstEdgeIndex = int(edgeIndex);
    lastEdgeIndex = int(edgeIndex + edgeCount);

    // Update radius for linear lights
    vec2 radius = vec2(radius);
    vec2 linearLightOffset = vec2(0.0);
    if ((lightFlags & LINEAR_LIGHT_TYPE) != 0)
    {
        linearLightOffset = vec2(cos(-directionAngle), sin(-directionAngle)) * size;
        radius.x += size; // Increases size of quad from being square to being rectangular
    }

    // Calculate offsets to compensate for jitter when textureScale is below 0
    vec2 lightOnScreenPosOffset = -drawOffset * textureScale;
    vec4 lightVertexPosOffset = vec4(drawOffset / (resolution / textureScale * vec2(-0.5, 0.5)), 0.0, 0.0);

    // Position of light in screen space (0 to width/height)
    lightPos0 = (view * vec4(position.xy - linearLightOffset, 0.0, 1.0) * textureScale).xy + lightOnScreenPosOffset;
    lightPos1 = (view * vec4(position.xy + linearLightOffset, 0.0, 1.0) * textureScale).xy + lightOnScreenPosOffset;

    // Calculate scaled and rotated vertex position
    vec2 vertex = (vertexPos * 2.0 - 1.0) * radius * rotateZ(directionAngle);

    // Set final vertex position
    gl_Position = (projection * view * vec4(position.xy + vertex, 0.0, 1.0)) + lightVertexPosOffset;
}