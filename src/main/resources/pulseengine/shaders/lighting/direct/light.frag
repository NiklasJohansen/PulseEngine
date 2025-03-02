#version 430 core

#define LIGHT_TYPE_RADIAL 1
#define LIGHT_TYPE_LINEAR 2
#define SHADOW_TYPE_NONE 64
#define SHADOW_TYPE_HARD 128
#define SHADOW_TYPE_SOFT 256
#define PI 3.1415926538

in vec2 lightPos0;
in vec2 lightPos1;
in float lightDepth;
in float lightRadius;
in float lightDirectionAngle;
in float lightConeAngle;
in float lightSize;
in vec4 lightColor;
in float lightIntensity;
in float lightSpill;
flat in int lightFlags;
flat in int firstEdgeIndex;
flat in int lastEdgeIndex;

out vec4 fragColor;

uniform sampler2D normalMap;
uniform sampler2D occluderMap;

uniform int edgeCount;
uniform vec4 ambientColor;
uniform vec2 resolution;
uniform float hasNormalMap;
uniform float hasOccluderMap;
uniform vec2 drawOffset;
uniform float textureScale;

struct Mask
{
    int m0;
    int m1;
    int m2;
    int m3;
};

struct Edge
{
    vec2 point0;
    vec2 point1;
};

layout(std430, binding = 0) buffer edgeLayout
{
    Edge edges[];
};

const int MASK_RES = 128;
const Mask fullMask = Mask(-0x1, -0x1, -0x1, -0x1);
const Mask emptyMask = Mask(0, 0, 0, 0);

////////////////////////////////////////// UTIL //////////////////////////////////////////

// Calculates what side of a line a point lies
float det(vec2 p, vec2 edge0, vec2 edge1)
{
    return sign((edge1.y - p.y) * (edge0.x - p.x) - (edge1.x - p.x) * (edge0.y - p.y));
}

vec2 getLineIntersection(vec2 p0, vec2 p1, vec2 p2, vec2 p3)
{
    vec2 normal0 = vec2(p1.y - p0.y, p0.x - p1.x);
    vec2 normal1 = vec2(p3.y - p2.y, p2.x - p3.x);
    float det = normal0.x * normal1.y - normal1.x * normal0.y;
    float c0 = dot(normal0, p0);
    float c1 = dot(normal1, p2);
    return (det == 0.0)
        ? vec2(p0.x + (p1.x - p0.x) * 100000.0, p0.y + (p1.y - p0.y) * 100000.0)
        : vec2((normal1.y * c0 - normal0.y * c1) / det, (normal0.x * c1 - normal1.x * c0) / det);
}

vec3 getLineSegmentIntersection(vec2 p0, vec2 p1, vec2 p2, vec2 p3)
{
    vec2 vec10 = p1 - p0;
    vec2 vec32 = p3 - p2;
    vec2 vec02 = p0 - p2;
    float d = 1.0 / (-vec32.x * vec10.y + vec10.x * vec32.y);
    float s = (-vec10.y * vec02.x + vec10.x * vec02.y) * d;
    float t = (vec32.x * vec02.y - vec32.y * vec02.x) * d;
    return (s < 0.0 || s > 1.0 || t < 0.0 || t > 1.0) ? vec3(0.0) : vec3(p0 + t * vec10, 1.0);
}

vec2 closestPointOnLineSegment(vec2 point, vec2 line0, vec2 line1)
{
    vec2 lineDir = line1 - line0;
    vec2 pointLine0 = point - line0;
    float lengthSquared = dot(lineDir, lineDir);
    float param = (lengthSquared == 0.0) ? -1.0 : dot(pointLine0, lineDir) / lengthSquared;
    return (param < 0.0) ? line0 : (param > 1.0 ? line1 : line0 + lineDir * param);
}

bool hasIntersection(vec2 p0, vec2 p1, vec2 p2, vec2 p3)
{
    float s1_x = p1.x - p0.x;
    float s1_y = p1.y - p0.y;
    float s2_x = p3.x - p2.x;
    float s2_y = p3.y - p2.y;
    float d = 1.0 / (-s2_x * s1_y + s1_x * s2_y);
    float s = (-s1_y * (p0.x - p2.x) + s1_x * (p0.y - p2.y)) * d;
    float t = ( s2_x * (p0.y - p2.y) - s2_y * (p0.x - p2.x)) * d;
    return s >= 0.0 && s <= 1.0 && t >= 0.0 && t <= 1.0;
}

////////////////////////////////////////// SOFT SHADOWS UTILS //////////////////////////////////////////

Mask createAndFillMask(int start, int end)
{
    int start0 = clamp(start, 0, 32);
    int end0   = clamp(end, 0, 32);
    int start1 = clamp(start, 32, 64) - 32;
    int end1   = clamp(end, 32, 64) - 32;
    int start2 = clamp(start, 64, 96) - 64;
    int end2   = clamp(end, 64, 96) - 64;
    int start3 = clamp(start, 96, 127) - 96;
    int end3   = clamp(end, 96, 127) - 96;

    return Mask(
        bitfieldInsert(0, -0x1, start0, (end0 - start0)),
        bitfieldInsert(0, -0x1, start1, (end1 - start1)),
        bitfieldInsert(0, -0x1, start2, (end2 - start2)),
        bitfieldInsert(0, -0x1, start3, (end3 - start3))
    );
}

Mask combineMasks(Mask mask0, Mask mask1)
{
    return Mask(mask0.m0 | mask1.m0, mask0.m1 | mask1.m1, mask0.m2 | mask1.m2, mask0.m3 | mask1.m3);
}

Mask calculateShadowMask(vec2 pixelPos, vec2 light0, vec2 light1, vec2 edge0, vec2 edge1, vec2 lightAxis, vec2 lightProjection)
{
    float detPixelEdge = det(pixelPos, edge0, edge1);
    float detLight0Edge = det(light0, edge0, edge1);
    float detLight1Edge = det(light1, edge0, edge1);

    // Both light points and the pixel is on the same side of the wall - no occlusion
    if (detPixelEdge == detLight0Edge && detPixelEdge == detLight1Edge)
        return emptyMask;

    float detPixelLight = det(pixelPos, light0, light1);
    float detEdge0Light = det(edge0, light0, light1);
    float detEdge1Light = det(edge1, light0, light1);

    // The pixel is on the opposite side of the light axis compared to the wall - no occlusion
    if (detPixelLight != detEdge0Light && detPixelLight != detEdge1Light)
        return emptyMask;

    // Calculate pixel to edge vectors
    vec2 pixelEdge0 = edge0 - pixelPos;
    vec2 pixelEdge1 = edge1 - pixelPos;

    // Determines the dot products of the pixel-to-edge-normal along the light-axis
    float point0Dot = (-pixelEdge0.y * lightAxis.x + pixelEdge0.x * lightAxis.y) * detPixelLight;
    float point1Dot = (-pixelEdge1.y * lightAxis.x + pixelEdge1.x * lightAxis.y) * detPixelLight;

    // When both dot products are negative, the pixel-to-point vectors are flipped - no occlusion
    if (point0Dot < 0.0 && point1Dot < 0.0)
        return emptyMask;

    // Determines the offset direction and magnitude to move the point in case any of the dot products are negative
    float offset = (detPixelEdge * -detPixelLight) * 100000;

    // Calculate the points where the pixel-to-edge lines intersect the light axis line
    vec2 point0 = (point0Dot >= 0.0)
        ? getLineIntersection(pixelPos, edge0, light0, light1)
        : pixelPos + lightAxis * offset;

    vec2 point1 = (point1Dot >= 0.0)
        ? getLineIntersection(pixelPos, edge1, light0, light1)
        : pixelPos - lightAxis * offset;

    // Use intersection between light and wall if light passes through wall
    vec3 intersection = getLineSegmentIntersection(edge0, edge1, light0, light1);
    if (intersection.z == 1.0)
    {
        vec2 pixelPoint0 = point0 - pixelPos;
        vec2 pixelPoint1 = point1 - pixelPos;
        float pixelPoint0Length = dot(pixelPoint0, pixelPoint0);
        float pixelPoint1Length = dot(pixelPoint1, pixelPoint1);
        float pixelEdge0Length = dot(pixelEdge0, pixelEdge0);
        float pixelEdge1Length = dot(pixelEdge1, pixelEdge1);

        if (pixelPoint0Length < pixelEdge0Length)
            point0 = intersection.xy;

        if (pixelPoint1Length < pixelEdge1Length)
            point1 = intersection.xy;
    }

    // Project points on the light axis
    float point0Proj = dot(lightAxis, point0);
    float point1Proj = dot(lightAxis, point1);
    float occlusionMin = min(point0Proj, point1Proj);
    float occlusionMax = max(point0Proj, point1Proj);

    // Get min and max projection scalars on light axis
    float lightMin = lightProjection.x;
    float lightMax = lightProjection.y;

    if (occlusionMin <= lightMin && occlusionMax >= lightMax)
        return fullMask;

    // Calculate start and end positions
    float invRange = MASK_RES / (lightMax - lightMin);
    float start = (occlusionMin - lightMin) * invRange;
    float end = (occlusionMax - lightMin) * invRange;

    // Create bit mask
    return createAndFillMask(int(start), int(end));
}

// Returns a value in range (0.0 - 1.0) determening how much of the light is covered (in shadow)
float getShadowCoverage(Mask mask)
{
    return float(bitCount(mask.m0) + bitCount(mask.m1) + bitCount(mask.m2) + bitCount(mask.m3)) / MASK_RES;
}

////////////////////////////////////////// LINEAR SOFT SHADOW //////////////////////////////////////////

float calcluateLinearSoftShadow(vec2 pixelPos, vec2 light0, vec2 light1)
{
    // Axis between first and second light point
    vec2 lightAxis = normalize(light1 - light0);

    // Light points projected onto the axis
    float lightDot0 = dot(lightAxis, light0);
    float lightDot1 = dot(lightAxis, light1);

    // Store the lowest and highest projection scalars in a 2D vector
    vec2 lightProjection = vec2(min(lightDot0, lightDot1), max(lightDot0, lightDot1));

    // 128bit shadow max to determine which parts of the light is covered by edges/walls
    Mask shadowMask = Mask(0, 0, 0, 0);

    for (int i = firstEdgeIndex; i < lastEdgeIndex; i++)
    {
        Edge edge = edges[i];

        // Calculate shadow mask for current edge
        Mask mask = calculateShadowMask(pixelPos, light0, light1, edge.point0, edge.point1, lightAxis, lightProjection);

        // Add mask to final shadow mask
        shadowMask = combineMasks(shadowMask, mask);
    }

    return (shadowMask == fullMask) ? 1.0 : getShadowCoverage(shadowMask);
}

////////////////////////////////////////// LINEAR HARD SHADOW //////////////////////////////////////////

float calcluateLinearHardShadow(vec2 pixelPos, vec2 light0, vec2 light1)
{
    vec2 closestLightPoint = closestPointOnLineSegment(pixelPos, light0, light1);

    for (int i = firstEdgeIndex; i < lastEdgeIndex; i++)
    {
        Edge edge = edges[i];
        if (hasIntersection(pixelPos, closestLightPoint, edge.point0, edge.point1))
            return 1.0;
    }

    return 0.0;
}

////////////////////////////////////////// RADIAL SOFT SHADOWS //////////////////////////////////////////

float calcluateRadialSoftShadow(vec2 pixelPos)
{
    vec2 pixelToLight = normalize(lightPos0 - pixelPos);
    vec2 delta = vec2(-pixelToLight.y, pixelToLight.x) * lightSize * 0.5;
    vec2 light0 = lightPos0 - delta;
    vec2 light1 = lightPos0 + delta;

    return calcluateLinearSoftShadow(pixelPos, light0, light1);
}

////////////////////////////////////////// RADIAL HARD SHADOWS //////////////////////////////////////////

float calcluateRadialHardShadow(vec2 pixelPos)
{
    for (int i = firstEdgeIndex; i < lastEdgeIndex; i++)
    {
        Edge edge = edges[i];

        if (hasIntersection(lightPos0, pixelPos, edge.point0, edge.point1))
           return 1.0;
    }

    return 0.0;
}

////////////////////////////////////////// MAIN //////////////////////////////////////////

float calculateShadow(vec2 pixelPos)
{
    if ((lightFlags & SHADOW_TYPE_NONE) != 0)
    {
        return 0.0;
    }
    else if ((lightFlags & LIGHT_TYPE_RADIAL) != 0)
    {
        if ((lightFlags & SHADOW_TYPE_SOFT) != 0)
        {
            return calcluateRadialSoftShadow(pixelPos);
        }
        else
        {
            return calcluateRadialHardShadow(pixelPos);
        }
    }
    else if ((lightFlags & LIGHT_TYPE_LINEAR) != 0)
    {
        if ((lightFlags & SHADOW_TYPE_SOFT) != 0)
        {
            return calcluateLinearSoftShadow(pixelPos, lightPos0, lightPos1);
        }
        else
        {
            return calcluateLinearHardShadow(pixelPos, lightPos0, lightPos1);
        }
    }

    return 0.0;
}

float getSquaredDistanceToLight(vec2 pixelPos)
{
    if ((lightFlags & LIGHT_TYPE_RADIAL) != 0)
    {
        vec2 delta = lightPos0 - pixelPos;
        float distSquared = dot(delta, delta) / (lightRadius * lightRadius);

        // Calculate light cone
        vec2 lightDir = normalize(vec2(-delta.x, delta.y));
        float coneAngleDelta = cos(lightConeAngle) - dot(lightDir, vec2(cos(lightDirectionAngle), sin(lightDirectionAngle)));
        distSquared *= pow(1.0 + clamp(coneAngleDelta, 0.0, 100.0), 10.0);

        return distSquared;
    }
    else if ((lightFlags & LIGHT_TYPE_LINEAR) != 0)
    {
        vec2 point = closestPointOnLineSegment(pixelPos, lightPos0, lightPos1);
        vec2 delta = point - pixelPos;
        float distSquared = dot(delta, delta) / (lightRadius * lightRadius);
        return distSquared;
    }

    return 0.5;
}

vec3 getDirectionToLight(vec2 pixelPos)
{
    vec2 lightCenter = lightPos0;
    if ((lightFlags & LIGHT_TYPE_LINEAR) != 0)
        lightCenter = closestPointOnLineSegment(pixelPos, lightPos0, lightPos1);

    // Direction vector with length
    vec2 delta = (pixelPos - lightCenter) / lightRadius;

    return normalize(vec3(-delta.x, delta.y, lightDepth));
}

void main()
{
    // Postion on screen
    vec2 pixelPos = vec2(gl_FragCoord.x, resolution.y - gl_FragCoord.y);

    // Squarded distance from pixel position to light
    float distSquared = getSquaredDistanceToLight(pixelPos);

    // Early exit
    if (distSquared > 1.0)
        discard;

    // Direction from pixel postion to light
    vec3 lightDir = getDirectionToLight(pixelPos);

    // Calculate shadow (0 = no shadow, 1 = full shadow)
    float shadow = calculateShadow(pixelPos);

    // Calculate texure sample coordinate (offset compensates for jitter when texture scale is below 0)
    vec2 offset = vec2(drawOffset.x, -drawOffset.y) * textureScale;
    vec2 texSampleCoord = (gl_FragCoord.xy + offset) / resolution;

    // Sample occluder map to determine spill light
    if (hasOccluderMap > 0)
    {
        vec4 occluder = texture(occluderMap, texSampleCoord);
        if (occluder.a > 0.0)
        {
            shadow *= 1.0 - lightSpill;
            distSquared *= 1.0 + 40.0 * clamp(1.0 - lightSpill, 0.0, 1.0);
        }
    }

    // Fetch surface normal
    vec3 surfaceNormal = vec3(0.0, 0.0, 1.0);
    if (hasNormalMap > 0)
        surfaceNormal = texture(normalMap, texSampleCoord).xyz * 2.0 - 1.0;

    // Calculate lambertian based on surace normal and light direction
    float lambertian = max(0.0, dot(surfaceNormal, lightDir));

    // Calculate diffuse color
    vec3 diffuse = lightColor.rgb * lambertian * lightIntensity;

    // Attenuation based on distance from center of light
    float attenuation = clamp(1.0 - sqrt(distSquared), 0.0, 1.0);

    // Final color cobines diffuse color, attenuation and shadow
    vec3 finalColor = diffuse * attenuation * attenuation * (1.0 - shadow);

    // Set fragment color
    fragColor = vec4(finalColor, 1.0);
}