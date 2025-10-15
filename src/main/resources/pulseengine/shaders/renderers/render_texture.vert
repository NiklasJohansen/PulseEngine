#version 150 core

in vec2 vertexPos;
in vec2 texCoord;

out vec2 uv;
out vec2 quadUv;
out vec4 vertexColor;

uniform mat4 viewProjection;
uniform vec3 position;
uniform vec2 size;
uniform vec2 origin;
uniform float angle;
uniform int color;
uniform vec4 uvMinMax;

vec4 unpackAndConvert(uint rgba)
{
    // Unpack the rgba color and convert it from sRGB to linear space
    vec4 sRgba = vec4((rgba >> 24u) & 255u, (rgba >> 16u) & 255u, (rgba >> 8u) & 255u, rgba & 255u) / 255.0;
    vec3 lowRange = sRgba.rgb / 12.92;
    vec3 highRange = pow((sRgba.rgb + 0.055) / 1.055, vec3(2.4));
    vec3 linearRgb = mix(highRange, lowRange, lessThanEqual(sRgba.rgb, vec3(0.0031308)));
    return vec4(linearRgb, sRgba.a);
}

mat2 rotate(float angle)
{
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, s, -s, c);
}

void main()
{
    vec2 uvMin = vec2(uvMinMax.x, 1.0 - uvMinMax.y);
    vec2 uvMax = vec2(uvMinMax.z, 1.0 - uvMinMax.w);
    uv = uvMin + (uvMax - uvMin) * vertexPos;
    quadUv = vertexPos;
    vertexColor = unpackAndConvert(uint(color));

    vec2 offset = (vertexPos - origin) * size * rotate(radians(angle));
    vec4 vertexPos = vec4(position, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = viewProjection * vertexPos;
}
