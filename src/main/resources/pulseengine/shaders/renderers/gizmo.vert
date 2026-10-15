#version 150 core

in vec3 position;
in uint rgbaColor;
in vec2 pixelOffset;
in vec3 dashDirection;

uniform mat4 viewProjection;
uniform vec2 screenSize;

out vec4 vertexColor;
flat out vec2 dashAxis;

vec4 unpackAndConvert(uint rgba)
{
    // Unpack the rgba color and convert it from sRGB to linear space
    vec4 sRgba = vec4((rgba >> 24u) & 255u, (rgba >> 16u) & 255u, (rgba >> 8u) & 255u, rgba & 255u) / 255.0;
    vec3 lowRange = sRgba.rgb / 12.92;
    vec3 highRange = pow((sRgba.rgb + 0.055) / 1.055, vec3(2.4));
    vec3 linearRgb = mix(highRange, lowRange, lessThanEqual(sRgba.rgb, vec3(0.04045)));
    return vec4(linearRgb, sRgba.a);
}

void main()
{
    vec4 clipPosition = viewProjection * vec4(position, 1.0);
    vec4 direction = viewProjection * vec4(dashDirection, 0.0);
    vec2 screenDirection = (direction.xy * clipPosition.w - clipPosition.xy * direction.w) * screenSize;
    float directionLength = length(screenDirection);

    vertexColor = unpackAndConvert(rgbaColor);
    dashAxis = directionLength > 0.0 ? screenDirection / directionLength : vec2(0.0);

    gl_Position = clipPosition;
    gl_Position.xy += vec2(pixelOffset.x, -pixelOffset.y) * (2.0 / screenSize) * clipPosition.w;
}