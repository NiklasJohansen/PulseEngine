#version 150 core

in vec4 vertexColor;
in vec2 texCoord;
in vec2 quadSize;
in float quadCornerRadius;
in float sourceIntensity;
in float sourceAngle;
in float sourceConeAngle;
in float sourceRadius; // light radius / occluder edge light

out vec4 sceneColor;
out vec4 metadata;

void main()
{
    vec4 color = vertexColor;

    if (quadCornerRadius > 0.0)
    {
        vec2 pos = texCoord * quadSize;
        float border = clamp(quadCornerRadius, 0.0, 0.5 * min(quadSize.x, quadSize.y));
        vec2 corner = clamp(pos, vec2(border), quadSize - border);
        float distFromCorner = length(pos - corner) - border;
        float alpha = 1.0f - smoothstep(0, 1.0, distFromCorner);
        color.a *= alpha;
    }

    sceneColor = color;
    metadata = vec4(sourceConeAngle / 360.0, sourceAngle / 360.0, sourceIntensity, sourceRadius);
}