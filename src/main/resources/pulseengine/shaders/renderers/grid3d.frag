#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform mat4 uInvViewProjection;
uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;
uniform float uFadeDistance;

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
    vec4 farClip = uInvViewProjection * vec4(ndc, 1.0, 1.0);
    vec3 farWorld = farClip.xyz / farClip.w;
    vec3 ray = farWorld - uCameraPosition;

    if (abs(ray.y) < 0.00001) discard;

    float rayDistance = -uCameraPosition.y / ray.y;
    if (rayDistance <= 0.0) discard;

    vec3 worldPosition = uCameraPosition + ray * rayDistance;
    vec4 clipPosition = uViewProjection * vec4(worldPosition, 1.0);
    float depth = clipPosition.z / clipPosition.w * 0.5 + 0.5;
    if (depth <= 0.0 || depth >= 1.0) discard;

    float minor = gridLine(worldPosition.xz, 1.0);
    float major = gridLine(worldPosition.xz, 10.0);
    float minorCellPixels = 1.0 / max(max(fwidth(worldPosition.x), fwidth(worldPosition.z)), 0.0001);
    minor *= smoothstep(1.0, 3.0, minorCellPixels);

    float lineAlpha = max(minor * 0.20, major * 0.34);
    vec3 color = vec3(0.18);

    float xAxis = axisLine(worldPosition.z);
    float zAxis = axisLine(worldPosition.x);
    color = mix(color, vec3(0.65, 0.12, 0.10), xAxis);
    color = mix(color, vec3(0.10, 0.28, 0.70), zAxis);
    lineAlpha = max(lineAlpha, max(xAxis, zAxis) * 0.72);

    float distanceFade = 1.0 - smoothstep(uFadeDistance * 0.45, uFadeDistance, distance(uCameraPosition.xz, worldPosition.xz));
    float horizonFade = smoothstep(0.0, 0.035, abs(normalize(ray).y));
    float alpha = lineAlpha * distanceFade * horizonFade;
    if (alpha < 0.002) discard;

    gl_FragDepth = depth;
    fragColor = vec4(color, alpha);
}