// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec4 vertexColor;
in vec2 textureArrayCoord;
in vec2 textureCoord;
in float textureIndex;
in vec2 quadSize;
in float quadCornerRadius;

out vec4 fragColor;

// 19:00 and 1:08:43 https://gdcvault.com/play/1020791/
uniform sampler2DArray textureArray; // TODO: Can be an array of sampler2DArray, to have support for multiple texture sizes and sampling types

float roundCorners(vec2 coord, vec2 center, float radius)
{
    return length(max(abs(coord) - center + radius, 0.0)) - radius;
}

float distance_from_rect(vec2 pixel_pos, vec2 rect_center, vec2 rect_corner, float corner_radius) {
    vec2 p = pixel_pos - rect_center;
    vec2 q = abs(p) - (rect_corner - corner_radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - corner_radius;
}

void main() {
    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);

    if (textureIndex >= 0)
        textureColor = texture(textureArray, vec3(textureArrayCoord.x, textureArrayCoord.y, floor(textureIndex)));

    if (quadCornerRadius > 0.0)
    {
        vec2 pos = textureCoord * quadSize;
        float border = clamp(quadCornerRadius, 0.0, 0.5 * min(quadSize.x, quadSize.y));
        vec2 corner = clamp(pos, vec2(border), quadSize - border);
        float distFromCorner = length(pos - corner) - border;
        textureColor.a *= 1.0f - smoothstep(0.0, 1.0, distFromCorner);
    }

    if (textureColor.a < 0.4)
        discard;

    fragColor = vertexColor * textureColor;
}