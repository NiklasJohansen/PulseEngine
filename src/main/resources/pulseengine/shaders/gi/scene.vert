#version 150 core
#define TAU 6.28318530718

// Vertex attributes
in vec2 vertexPos; // In range (0-1)

// Instance attributes
in vec3 worldPos;
in vec2 size;
in float angle;
in float cornerRadius;
in uint color;
in float intensity;
in float coneAngle;

out vec4 vertexColor;
out vec2 texCoord;
out vec2 quadSize;
out float quadCornerRadius;
out float sourceIntensity;
out float sourceAngle;
out float soureConeAngle;

uniform mat4 viewProjection;
uniform vec2 drawOffset; // Used to prevent jitter when lightmap scale is below 1.0

vec4 getColor(uint rgba)
{
    uint r = ((rgba >> uint(24)) & uint(255));
    uint g = ((rgba >> uint(16)) & uint(255));
    uint b = ((rgba >> uint(8))  & uint(255));
    uint a = (rgba & uint(255));
    return vec4(r, g, b, a) / 255.0f;
}

mat2 rotate(float angle)
{
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, s, -s,	c);
}

void main()
{
    vertexColor = getColor(color);
    texCoord = vertexPos;
    quadSize = size;
    quadCornerRadius = cornerRadius;
    sourceIntensity = intensity;

    sourceAngle = int(angle) % 361;
    if (sourceAngle < 0) sourceAngle += 360;

    soureConeAngle = int(coneAngle) % 361;
    if (soureConeAngle < 0) soureConeAngle += 360;

    vec2 offset = (vertexPos - vec2(0.5)) * size * rotate(radians(angle));
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = viewProjection * vertexPos + vec4(drawOffset, 0, 0);
}