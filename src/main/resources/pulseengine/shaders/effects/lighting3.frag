#version 430 core

#define NONE_SHADOW_TYPE 64
#define PI 3.1415926538

in vec2 lightPosition;
in float lightDepth;
in vec2 lightCoord;
in vec2 lightCoordFixedRot;

in vec2 texCoordRotLocked;
in float lightRadius;
in float lightDirectionAngle;
in float lightConeAngle;
in float lightSize;
in vec4 lightColor;
in float lightIntensity;
in float lightSpill;
flat in int lightFlags;
in float lightEdgeIndex;
in float lightEdgeCount;

out vec4 fragColor;

layout(binding=0) uniform sampler2D normalMap;
layout(binding=1) uniform sampler2D occluderMap;

uniform int edgeCount;
uniform vec4 ambientColor;
uniform vec2 resolution;
uniform float hasNormalMap;
uniform float hasOccluderMap;
uniform vec2 drawOffset;
uniform float textureScale;

struct Edge {
    vec2 point0;
    vec2 point1;
};

layout(std430, binding = 0) buffer edgeLayout
{
    Edge edges[];
};

////////////////////////////////////////// RADIAL HARD SHADOWS //////////////////////////////////////////

bool hasIntersection(vec2 p0, vec2 p1, vec2 p2, vec2 p3)
{
    float s1_x = p1.x - p0.x;
    float s1_y = p1.y - p0.y;
    float s2_x = p3.x - p2.x;
    float s2_y = p3.y - p2.y;
    float d = (-s2_x * s1_y + s1_x * s2_y);
    float s = (-s1_y * (p0.x - p2.x) + s1_x * (p0.y - p2.y)) / d;
    float t = ( s2_x * (p0.y - p2.y) - s2_y * (p0.x - p2.x)) / d;
    return s >= 0 && s <= 1 && t >= 0 && t <= 1;
}

float radialHardShadows(vec2 pixelPos)
{
    if ((lightFlags & NONE_SHADOW_TYPE) == 0)
    {
        for (int j = int(lightEdgeIndex); j < lightEdgeIndex + lightEdgeCount; j++)
        {
            Edge edge = edges[j];

            if (hasIntersection(lightPosition, pixelPos, edge.point0, edge.point1))
               return 1.0;
        }
    }

    return 0.0;
}

////////////////////////////////////////// MAIN //////////////////////////////////////////

void main()
{
    // Squarded distance from center of light
    float distSquared = lightCoord.x * lightCoord.x + lightCoord.y * lightCoord.y;

    // Calculate light cone
    vec2 dir = normalize(vec2(-lightCoord.x, lightCoord.y));
    float coneAngleDelta = cos(lightConeAngle) - dir.x;
    distSquared *= pow(1.0 + clamp(coneAngleDelta, 0.0, 100.0), 10.0);

    // Early exit
    if (distSquared > 1.0)
        discard;

    // Postion on screen
    vec2 pixelPos = vec2(gl_FragCoord.x, resolution.y - gl_FragCoord.y);

    // Calculate shadow
    float shadow = radialHardShadows(pixelPos);

    // Calculate texure sample coordinate (offset compensates for jitter when texture scale is below 0)
    vec2 offset = vec2(drawOffset.x, -drawOffset.y) * textureScale;
    vec2 sampleCoord = (gl_FragCoord.xy + offset) / resolution;

    // Sample occluder map to determine spill light
    if (hasOccluderMap > 0)
    {
        vec4 occluder = texture(occluderMap, sampleCoord);
        if (occluder.a > 0.0)
        {
            shadow *= 1.0 - lightSpill;
            distSquared *= 1.0 + 40.0 * clamp(1.0 - lightSpill, 0.0, 1.0);
        }
    }

    // Calculate surface normal
    vec3 surfaceNormal = vec3(0, 0, 1);
    if (hasNormalMap > 0)
        surfaceNormal = texture(normalMap, sampleCoord).xyz * 2.0 - 1.0;

    // Calculate lambertian based on surace normal and (unrotated) light direction
    vec3 lightDir = normalize(vec3(-lightCoordFixedRot.x, lightCoordFixedRot.y, -lightDepth * 0.1));
    float lambertian = max(0.0, dot(surfaceNormal, lightDir));

    // Calculate diffuse color
    vec3 diffuse = lightColor.rgb * lambertian * lightIntensity;

    // Attenuation based on distance from center of light quad
    float attenuation = clamp(1.0 - sqrt(distSquared), 0.0, 1.0);

    // Final color cobines diffuse color, attenuation and shadow
    vec3 finalColor = diffuse * attenuation * attenuation * (1.0 - shadow);

    fragColor = vec4(finalColor, 1.0);
}