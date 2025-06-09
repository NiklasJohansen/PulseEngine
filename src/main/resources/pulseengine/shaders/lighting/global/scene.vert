#version 150 core

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
in float radius;
in vec2 uvMin;
in vec2 uvMax;
in uint textureHandle;

out vec4 vertexColor;
out vec2 quadSize;
out float quadCornerRadius;
out float sourceIntensity;
out float sourceAngle;
out float sourceConeAngle;
out float sourceRadius;
out vec2 texStart;
out vec2 texSize;
out vec2 texCoord;
out float texIndex;
flat out uint texSamplerIndex;

uniform mat4 viewProjection;
uniform vec2 uvDrawOffset; // Used to prevent jitter when lightmap scale is below 1.0
uniform vec2 resolution;
uniform float camScale;
uniform float worldScale;
uniform bool upscaleSmallSources;

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
    return mat2(c, s, -s,	c);
}

uint getSamplerIndex(uint textureHandle)
{
    return (textureHandle >> uint(16)) & ((uint(1) << uint(16)) - uint(1));
}

float getTexIndex(uint textureHandle)
{
    return float(textureHandle & ((uint(1) << uint(16)) - uint(1)));
}

void main()
{
    vertexColor = unpackAndConvert(color);
    texStart = uvMin;
    texSize = (uvMax - uvMin) * 0.98; // Use 0.98 to avoid artifacts at the edges of the texture
    texCoord = vertexPos;
    quadSize = size;
    quadCornerRadius = cornerRadius;
    sourceIntensity = intensity;
    sourceRadius = radius;
    sourceAngle = angle;
    sourceConeAngle = coneAngle;

    texSamplerIndex = getSamplerIndex(textureHandle);
    texIndex = getTexIndex(textureHandle);

    // Adjust size to make sure it covers at least one pixel
    vec4 screenSpacePos = viewProjection * vec4(worldPos, 1.0);
    float pixelSizeInWorld = screenSpacePos.w / resolution.y;
    vec2 adjustedSize = max(size, vec2(pixelSizeInWorld * 1500.0 / camScale));

    // Increase the size of small light sources (and reduce intensity) if enabled
    // This prevents small sources from being too small to be visible in the zoomed out global/world sdf
    float threshold = 10 * worldScale;
    if (upscaleSmallSources && intensity > 0.0 && adjustedSize.x < threshold && adjustedSize.y < threshold)
    {
        vec2 pos = screenSpacePos.xy * worldScale;
        float fade = smoothstep(0.55, 0.6, max(abs(pos.x - 0.5), abs(pos.y - 0.5)));
        float amount = 1.0 + 2.0 * fade;
        adjustedSize *= amount;
        sourceIntensity = intensity / amount;
    }

    vec2 offset = (vertexPos - vec2(0.5)) * adjustedSize * rotate(radians(angle));
    vec4 vertexPos = vec4(worldPos, 1.0) + vec4(offset, 0.0, 0.0);

    gl_Position = viewProjection * vertexPos + vec4(2.0 * uvDrawOffset, 0, 0);
}