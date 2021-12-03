#version 420 core

#define RADIAL_LIGHT_TYPE 1
#define LINEAR_LIGHT_TYPE 2

#define NONE_SHADOW_TYPE 64
#define HARD_SHADOW_TYPE 128
#define SOFT_SHADOW_TYPE 256

#define PI 3.1415926538

in vec2 textureCoord;
out vec4 fragColor;

layout(binding=0) uniform sampler2D base;
layout(binding=1) uniform sampler2D normals;
layout(binding=2) uniform sampler2D bleedColor;

uniform int lightCount;
uniform int edgeCount;
uniform vec2 resolution;
uniform vec4 ambientColor;
uniform float lightBleed;

const int NR_LIGHTS = 1000;
const int NR_EDGES = 1000;
const int MASK_RES = 128;

struct Mask {
    int m0;
    int m1;
    int m2;
    int m3;
};

const Mask fullMask = Mask(-0x1, -0x1, -0x1, -0x1);
const Mask emptyMask = Mask(0, 0, 0, 0);

struct Light {
    vec2 position;
    float depth;
    float radius;
    float directionAngle;
    float coneAngle;
    float size;
    float red;
    float green;
    float blue;
    float intensity;
    int flags;
};

struct Edge {
    vec2 point0;
    vec2 point1;
};

layout (std140) uniform LightBlock {
    Light lights[NR_LIGHTS];
};

layout (std140) uniform EdgeBlock {
    Edge edges[NR_EDGES];
};

bool hasIntersection(vec2 p0, vec2 p1, vec2 p2, vec2 p3)
{
    float s1_x = p1.x - p0.x;
    float s1_y = p1.y - p0.y;
    float s2_x = p3.x - p2.x;
    float s2_y = p3.y - p2.y;
    float s = (-s1_y * (p0.x - p2.x) + s1_x * (p0.y - p2.y)) / (-s2_x * s1_y + s1_x * s2_y);
    float t = ( s2_x * (p0.y - p2.y) - s2_y * (p0.x - p2.x)) / (-s2_x * s1_y + s1_x * s2_y);
    return s >= 0 && s <= 1 && t >= 0 && t <= 1;
}

vec2 closestPointOnLineSeg(vec2 point, vec2 line0, vec2 line1)
{
    vec2 lineDir = line1 - line0;
    vec2 pointLine0 = point - line0;
    float lengthSquared = dot(lineDir, lineDir);
    float param = (lengthSquared == 0f) ? -1f : dot(pointLine0, lineDir) / lengthSquared;

    if (param < 0.0)
        return line0;
    else if (param > 1.0f)
        return line1;
    else
        return line0 + lineDir * param;
}

float det(vec2 p, vec2 edge0, vec2 edge1)
{
    // Calculates what side of a line a point lies
    return ((edge1.y - p.y) * (edge0.x - p.x) > (edge1.x - p.x) * (edge0.y - p.y)) ? 1.0 : -1.0;
}

float detContinoues(vec2 p, vec2 edge0, vec2 edge1)
{
    // Calculates what side of a line a point lies
    return sign(((edge1.y - p.y) * (edge0.x - p.x) - (edge1.x - p.x) * (edge0.y - p.y)));
}

vec2 getLineIntersection(vec2 p0, vec2 p1, vec2 p2, vec2 p3)
{
    vec2 normal0 = vec2(p1.y - p0.y, p0.x - p1.x);
    vec2 normal1 = vec2(p3.y - p2.y, p2.x - p3.x);
    float det = normal0.x * normal1.y - normal1.x * normal0.y;
    float c0 = dot(normal0, p0);
    float c1 = dot(normal1, p2);

    if (det == 0.0)
        return vec2(p0.x + (p1.x - p0.x) * 100000f, p0.y + (p1.y - p0.y) * 100000f);
    else
    {
        float c0 = dot(normal0, p0);
        float c1 = dot(normal1, p2);
        return vec2((normal1.y * c0 - normal0.y * c1) / det, (normal0.x * c1 - normal1.x * c0) / det);
    }
}

vec3 getLineSegmentIntersection(vec2 p0, vec2 p1, vec2 p2, vec2 p3)
{
    vec2 vec10 = p1 - p0;
    vec2 vec32 = p3 - p2;
    vec2 vec02 = p0 - p2;

    float s = (-vec10.y * vec02.x + vec10.x * vec02.y) / (-vec32.x * vec10.y + vec10.x * vec32.y);
    if (s < 0.0 || s > 1.0)
        return vec3(0.0, 0.0, 0.0);

    float t = (vec32.x * vec02.y - vec32.y * vec02.x) / (-vec32.x * vec10.y + vec10.x * vec32.y);
    if (t < 0.0 || t > 1.0)
        return vec3(0.0, 0.0, 0.0);

    return vec3(p0 + t * vec10, 1.0);
}

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

float countBits(Mask mask)
{
    return bitCount(mask.m0) + bitCount(mask.m1) + bitCount(mask.m2) + bitCount(mask.m3);
}

Mask combineMask(Mask mask0, Mask mask1)
{
    return Mask(mask0.m0 | mask1.m0, mask0.m1 | mask1.m1, mask0.m2 | mask1.m2, mask0.m3 | mask1.m3);
}

Mask calculateOcculsionMask(vec2 pixelPos, vec2 light0, vec2 light1, vec2 edge0, vec2 edge1, vec2 lightDir, vec3 lightProj)
{
    float detPixelEdge = detContinoues(pixelPos, edge0, edge1);
    float detLight0Edge = detContinoues(light0, edge0, edge1);
    float detLight1Edge = detContinoues(light1, edge0, edge1);

    // Both light points and the pixel is on the same side of the wall - no occlusion
    if (detPixelEdge == detLight0Edge && detPixelEdge == detLight1Edge)
        return emptyMask;

    float detPixelLight = detContinoues(pixelPos, light0, light1);
    float detEdge0Light = detContinoues(edge0, light0, light1);
    float detEdge1Light = detContinoues(edge1, light0, light1);

    // The pixel is on the opposite side of the light axis compared to the wall - no occlusion
    if (detPixelLight != detEdge0Light && detPixelLight != detEdge1Light)
        return emptyMask;

    // Calculate pixel to edge vectors
    vec2 pixelEdge0 = edge0 - pixelPos;
    vec2 pixelEdge1 = edge1 - pixelPos;

    // Determines the dot products of the pixel-to-edge-normal along the light-axis
    float point0Dot = (-pixelEdge0.y * lightDir.x + pixelEdge0.x * lightDir.y) * detPixelLight;
    float point1Dot = (-pixelEdge1.y * lightDir.x + pixelEdge1.x * lightDir.y) * detPixelLight;

    // When both dot products are negative, the pixel-to-point vectors are flipped - no occlusion
    if (point0Dot < 0.0 && point1Dot < 0.0)
        return emptyMask;

    // Determines the offset direction and magnitude to move the point in case any of the dot products are negative
    float offset = (detPixelEdge * -detPixelLight) * 100000f;

    // Calculate the points where the pixel-to-edge lines intersect the light axis line
    vec2 point0 = (point0Dot >= 0.0)
        ? getLineIntersection(pixelPos, edge0, light0, light1)
        : pixelPos + lightDir * offset;

    vec2 point1 = (point1Dot >= 0.0)
        ? getLineIntersection(pixelPos, edge1, light0, light1)
        : pixelPos - lightDir * offset;

    // Use intersection between light and wall if light passes though wall
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
    float point0Proj = dot(lightDir, point0);
    float point1Proj = dot(lightDir, point1);
    float occlusionMin = min(point0Proj, point1Proj);
    float occlusionMax = max(point0Proj, point1Proj);

    // Get min and max projections from light on light axis
    float lightMin = lightProj.x;
    float lightMax = lightProj.y;
    float invLightRange = lightProj.z;

    if (occlusionMin <= lightMin && occlusionMax >= lightMax)
        return fullMask;

    // Calculate start and end positions
    float start = (occlusionMin - lightMin) * invLightRange;
    float end = (occlusionMax - lightMin) * invLightRange;

    // Create bit mask
    return createAndFillMask(int(start), int(end));
}

vec3 softShadows(vec2 pos, vec3 normal, bool insideLightOccluder, Light light)
{
    float radius = max(0.0001, light.radius);
    float halfSize = max(0.01, light.size) * 0.5f;
    vec2 light0 = light.position;
    vec2 light1 = light.position;
    vec2 closestPoint = light.position;
    float distSquared = 1000000f;
    float actualDistSquared = 0.0;

    if ((light.flags & RADIAL_LIGHT_TYPE) != 0)
    {
        vec2 posLight = light.position - pos;
        vec2 posLightDir = normalize(posLight);
        vec2 delta = vec2(-posLightDir.y, posLightDir.x) * halfSize;
        light0 -= delta;
        light1 += delta;
        distSquared = dot(posLight, posLight);
        actualDistSquared = distSquared;

        float coneAngleDelta = cos(light.coneAngle) - dot(posLightDir, vec2(cos(light.directionAngle), sin(light.directionAngle)));
        distSquared *= pow(1.0 + clamp(coneAngleDelta, 0.0, 100.0), 10.0);
    }
    else if ((light.flags & LINEAR_LIGHT_TYPE) != 0)
    {
        vec2 delta = vec2(cos(light.directionAngle), sin(light.directionAngle)) * halfSize;
        light0 -= delta;
        light1 += delta;
        closestPoint = closestPointOnLineSeg(pos, light0, light1);
        vec2 posToClosestPoint = closestPoint - pos;
        distSquared = dot(posToClosestPoint, posToClosestPoint);
        actualDistSquared = distSquared;
    }

    if (distSquared > radius * radius)
        return vec3(0f);

    float occlusion = 0.8;
    if ((light.flags & NONE_SHADOW_TYPE) == 0)
    {
        vec2 lightVec = light1 - light0;
        vec2 lightDir = normalize(lightVec);
        float lightDot0 = dot(lightDir, light0);
        float lightDot1 = dot(lightDir, light1);
        float minProjection = min(lightDot0, lightDot1);
        float maxProjection = max(lightDot0, lightDot1);
        float invProjectionRange = MASK_RES / (maxProjection - minProjection);
        vec3 lightProjection = vec3(minProjection, maxProjection, invProjectionRange);

        Mask occlusionMask = Mask(0, 0, 0, 0);

        for (int j = 0; j < edgeCount; j++)
        {
            Edge edge = edges[j];
            Mask mask = calculateOcculsionMask(pos, light0, light1, edge.point0, edge.point1, lightDir, lightProjection);
            occlusionMask = combineMask(occlusionMask, mask);
        }

        occlusion = (occlusionMask == fullMask) ? 1.0 : float(countBits(occlusionMask)) / MASK_RES;

        if (insideLightOccluder)
        {
            occlusion /= 1.0 + 5.0 * lightBleed;
            distSquared *= 1.0 + 50.0 * (1.0 - lightBleed);
        }
    }

    vec3 dir = normalize(vec3(closestPoint.x - pos.x, pos.y - closestPoint.y, -10.0 * light.depth));
    float lambertian = max(0.0, dot(normal, dir));
    vec3 lightColor = vec3(light.red, light.green, light.blue);
    vec3 diffuse = lightColor * lambertian * light.intensity * (1.0 - occlusion * occlusion * occlusion);
    float attenuation = clamp(1.0 - sqrt(distSquared) / light.radius, 0.0, 1.0);

    return diffuse * attenuation * attenuation;
}

vec3 radialHardShadows(vec2 pos, vec3 normal, bool insideLightOccluder, Light light)
{
    vec2 delta = light.position.xy - pos;
    float distSquared = delta.x * delta.x + delta.y * delta.y;

    float coneAngleDelta = cos(light.coneAngle) - dot(normalize(delta), vec2(cos(light.directionAngle), sin(light.directionAngle)));
    distSquared *= pow(1.0 + clamp(coneAngleDelta, 0.0, 100.0), 10.0);

    if (distSquared > light.radius * light.radius)
        return vec3(0.0);

    if ((light.flags & NONE_SHADOW_TYPE) == 0)
    {
        for (int j = 0; j < edgeCount; j++)
        {
            Edge edge = edges[j];

            if (hasIntersection(light.position, pos, edge.point0, edge.point1))
            {
                if (insideLightOccluder && lightBleed != 0.0)
                {
                    distSquared *= 1.0 + 50.0 * clamp(1.0 - lightBleed, 0.0, 1.0);
                    if (distSquared < light.radius * light.radius)
                        break; // Inside light occluder and inside radius of light, break out of loop
                }

                return vec3(0.0);
            }
        }
    }

    vec3 lightDir = normalize(vec3(delta.x, -delta.y, -10.0 * light.depth));
    float lambertian = max(0.0, dot(normal, lightDir));
    vec3 lightColor = vec3(light.red, light.green, light.blue);
    vec3 diffuse = lightColor * lambertian * light.intensity;
    float attenuation = clamp(1.0 - sqrt(distSquared) / light.radius, 0.0, 1.0);

    return diffuse * attenuation * attenuation;
}

void main()
{
    vec4 baseColor = texture(base, textureCoord);
    float dist = 0.001;
    vec4 normal = texture(normals, textureCoord) * 2.0 - 1.0;
    bool insideLightOccluder = texture(bleedColor, textureCoord).a > 0.0;
    vec2 pos = gl_FragCoord.xy;
    pos.y = resolution.y - pos.y;

    vec3 lighting = vec3(0.0);

    for (int i = 0; i < lightCount; i++)
    {
        Light light = lights[i];
        if ((light.flags & RADIAL_LIGHT_TYPE) != 0 && ((light.flags & HARD_SHADOW_TYPE) != 0 || light.size == 0.0))
            lighting += radialHardShadows(pos, normal.xyz, insideLightOccluder, light);
        else
            lighting += softShadows(pos, normal.xyz, insideLightOccluder, light);
    }

    vec3 intensity = ambientColor.rgb + lighting * baseColor.a;
    float alpha = (baseColor.a < 0.1) ? ambientColor.a : 1.0;
    fragColor = vec4(baseColor.rgb * intensity, alpha);
}