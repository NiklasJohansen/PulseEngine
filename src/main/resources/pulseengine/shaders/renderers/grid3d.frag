#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform mat4 uInvViewProjection;
uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;
uniform float uFadeDistance;
uniform bool uIs2D;
uniform vec3 uPlaneOrigin;
uniform vec3 uPlaneNormal;
uniform vec3 uPlaneU;
uniform vec3 uPlaneV;
uniform float uMinorSpacing;
uniform float uMajorSpacing;

float gridLine(vec2 position, float spacing)
{
    vec2 coordinate = position / spacing;
    vec2 derivative = max(fwidth(coordinate), vec2(0.0001));
    vec2 distanceToLine = abs(fract(coordinate - 0.5) - 0.5) / derivative;
    return 1.0 - min(min(distanceToLine.x, distanceToLine.y), 1.0);
}

float axisLine(float distance)
{
    float width = max(fwidth(distance), 0.0001);
    return 1.0 - smoothstep(width * 0.5, width * 1.5, abs(distance));
}

void main()
{
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 nearClip = uInvViewProjection * vec4(ndc, -1.0, 1.0);
    vec4 farClip = uInvViewProjection * vec4(ndc, 1.0, 1.0);
    vec3 nearWorld = nearClip.xyz / nearClip.w;
    vec3 farWorld = farClip.xyz / farClip.w;
    vec3 ray = farWorld - nearWorld;

    float denominator = dot(ray, uPlaneNormal);
    if (abs(denominator) < 0.00001) discard;

    float rayDistance = dot(uPlaneOrigin - nearWorld, uPlaneNormal) / denominator;
    if (rayDistance < 0.0 || rayDistance > 1.0) discard;

    vec3 worldPosition = nearWorld + ray * rayDistance;
    vec4 clipPosition = uViewProjection * vec4(worldPosition, 1.0);
    float depth = clipPosition.z / clipPosition.w * 0.5 + 0.5;
    if (!uIs2D && (depth <= 0.0 || depth >= 1.0)) discard;

    vec3 planeOffset = worldPosition - uPlaneOrigin;
    vec2 gridPosition = vec2(dot(planeOffset, uPlaneU), dot(planeOffset, uPlaneV));

    float minor = gridLine(gridPosition, uMinorSpacing);
    float major = gridLine(gridPosition, uMajorSpacing);
    float worldUnitsPerPixel = max(max(fwidth(gridPosition.x), fwidth(gridPosition.y)), 0.0001);
    float minorCellPixels = uMinorSpacing / worldUnitsPerPixel;
    minor *= smoothstep(1.0, 3.0, minorCellPixels);

    float lineAlpha = max(minor * 0.20, major * 0.34);
    vec3 color = vec3(0.18);

    float xAxis = axisLine(gridPosition.y);
    float zAxis = axisLine(gridPosition.x);
    color = mix(color, vec3(0.65, 0.12, 0.10), xAxis);
    color = mix(color, vec3(0.10, 0.28, 0.70), zAxis);
    lineAlpha = max(lineAlpha, max(xAxis, zAxis) * 0.72);

    vec3 cameraOffset = uCameraPosition - uPlaneOrigin;
    vec2 cameraGridPosition = vec2(dot(cameraOffset, uPlaneU), dot(cameraOffset, uPlaneV));
    float distanceFade = uIs2D ? 1.0 : 1.0 - smoothstep(uFadeDistance * 0.45, uFadeDistance, distance(cameraGridPosition, gridPosition));
    float horizonFade = uIs2D ? 1.0 : smoothstep(0.0, 0.035, abs(dot(normalize(ray), uPlaneNormal)));
    float alpha = lineAlpha * distanceFade * horizonFade;
    
    if (alpha < 0.002) discard;

    gl_FragDepth = uIs2D ? 1.0 : depth;
    fragColor = vec4(color, alpha);
}