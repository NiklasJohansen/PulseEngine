#version 330 core

in vec2 uv;
in vec2 quadUv;
in vec4 vertexColor;

out vec4 fragColor;

uniform sampler2D tex;

uniform bool sampleTexture;
uniform bool isDepthTexture;
uniform bool isPremultipliedAlpha;
uniform vec2 cornerRadiusPacked;
uniform vec2 borderPacked;
uniform vec2 size;

vec4 unpackAndConvert(uint rgba)
{
    vec4 sRgba = vec4((rgba >> 24u) & 255u, (rgba >> 16u) & 255u, (rgba >> 8u) & 255u, rgba & 255u) / 255.0;
    vec3 lowRange = sRgba.rgb / 12.92;
    vec3 highRange = pow((sRgba.rgb + 0.055) / 1.055, vec3(2.4));
    vec3 linearRgb = mix(highRange, lowRange, lessThanEqual(sRgba.rgb, vec3(0.04045)));
    return vec4(linearRgb, sRgba.a);
}

float roundedRectDistance(vec2 pos, vec2 size, vec4 radii)
{
    bool isLeft = pos.x < size.x * 0.5;
    bool isTop = pos.y < size.y * 0.5;
    float radius = isTop ? (isLeft ? radii.x : radii.y) : (isLeft ? radii.w : radii.z);
    radius = clamp(radius, 0.0, 0.5 * min(size.x, size.y));
    vec2 halfSize = size * 0.5;
    vec2 q = abs(pos - halfSize) - (halfSize - vec2(radius));
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

void main()
{
    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);
    
    if (sampleTexture)
    {
        textureColor = texture(tex, uv);

        // Render-texture inputs are premultiplied surface outputs. Convert them back to
        // straight alpha as the destination surface performs straight-source accumulation.
        if (isPremultipliedAlpha)
        {
            if (textureColor.a > 0.000001)
                textureColor.rgb /= textureColor.a;
            else
                textureColor.rgb = vec3(0.0);
        }
    }

    if (isDepthTexture)
    {
        textureColor.rgb = vec3(textureColor.r);
    }

    vec4 fillColor = vertexColor * textureColor;
    uvec2 packedRadii = floatBitsToUint(cornerRadiusPacked);
    uvec2 packedBorder = floatBitsToUint(borderPacked);
    float borderWidth = float(packedBorder.y & 0xFFFFu) / 16.0;
    bool hasRoundedCorners = any(notEqual(packedRadii, uvec2(0u)));

    if (hasRoundedCorners || borderWidth > 0.0)
    {
        vec4 cornerRadii = vec4(
            float(packedRadii.x & 0xFFFFu),
            float(packedRadii.x >> 16u),
            float(packedRadii.y & 0xFFFFu),
            float(packedRadii.y >> 16u)
        ) / 16.0;

        float distance = roundedRectDistance(quadUv * size, size, cornerRadii);
        float antialiasWidth = max(fwidth(distance) * 0.5, 0.01);
        float outerCoverage = 1.0 - smoothstep(-antialiasWidth, antialiasWidth, distance);

        if (borderWidth > 0.0)
        {
            vec4 borderColor = unpackAndConvert(packedBorder.x);
            float innerCoverage = 1.0 - smoothstep(-antialiasWidth, antialiasWidth, distance + borderWidth);
            float borderCoverage = max(outerCoverage - innerCoverage, 0.0);
            float fillAlpha = fillColor.a * innerCoverage;
            float borderAlpha = borderColor.a * borderCoverage;
            float combinedAlpha = fillAlpha + borderAlpha;
            vec3 combinedPremultiplied = fillColor.rgb * fillAlpha + borderColor.rgb * borderAlpha;

            fillColor.rgb = combinedAlpha > 0.000001 ? combinedPremultiplied / combinedAlpha : vec3(0.0);
            fillColor.a = combinedAlpha;
        }
        else
        {
            fillColor.a *= outerCoverage;
        }
    }

    fragColor = fillColor;
}