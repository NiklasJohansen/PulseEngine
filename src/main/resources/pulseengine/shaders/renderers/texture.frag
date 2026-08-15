#version 150 core
#define EDGE_SOFTNESS 0.01
#define NO_TEXTURE 65534u

in vec4 vertexColor;
in vec2 texStart;
in vec2 texSize;
in vec2 texCoord;
in vec2 texTiling;
in vec2 quadSize;
flat in uvec2 quadCornerRadiusPacked;
flat in uvec2 quadBorderPacked;
flat in uint texIndex;
flat in uint samplerIndex;

out vec4 fragColor;

uniform sampler2DArray uTextureBanks[16];
uniform float alphaDiscardThreshold;

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

// Use fixed sampler indices as some OpenGL drivers reject dynamic indexing of sampler arrays.
vec4 sampleTextureBankGrad(int index, vec3 texCoords, vec2 ddx, vec2 ddy)
{
    switch (index)
    {
        case 0:  return textureGrad(uTextureBanks[0],  texCoords, ddx, ddy);
        case 1:  return textureGrad(uTextureBanks[1],  texCoords, ddx, ddy);
        case 2:  return textureGrad(uTextureBanks[2],  texCoords, ddx, ddy);
        case 3:  return textureGrad(uTextureBanks[3],  texCoords, ddx, ddy);
        case 4:  return textureGrad(uTextureBanks[4],  texCoords, ddx, ddy);
        case 5:  return textureGrad(uTextureBanks[5],  texCoords, ddx, ddy);
        case 6:  return textureGrad(uTextureBanks[6],  texCoords, ddx, ddy);
        case 7:  return textureGrad(uTextureBanks[7],  texCoords, ddx, ddy);
        case 8:  return textureGrad(uTextureBanks[8],  texCoords, ddx, ddy);
        case 9:  return textureGrad(uTextureBanks[9],  texCoords, ddx, ddy);
        case 10: return textureGrad(uTextureBanks[10], texCoords, ddx, ddy);
        case 11: return textureGrad(uTextureBanks[11], texCoords, ddx, ddy);
        case 12: return textureGrad(uTextureBanks[12], texCoords, ddx, ddy);
        case 13: return textureGrad(uTextureBanks[13], texCoords, ddx, ddy);
        case 14: return textureGrad(uTextureBanks[14], texCoords, ddx, ddy);
        case 15: return textureGrad(uTextureBanks[15], texCoords, ddx, ddy);
        default: break;
    }
    return vec4(0.0);
}

void main()
{
    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);

    if (texIndex != NO_TEXTURE)
    {
        // Compute derivatives before tiling as tiled uv coordinates create discontinuities
        // at tile edges causing the wrong mip level to be selected
        vec2 coord = texCoord * texTiling;
        vec2 tiled = fract(coord);
        vec2 ddx = dFdx(coord) * texSize;
        vec2 ddy = dFdy(coord) * texSize;
        vec2 uv = texStart + texSize * tiled;
        textureColor = sampleTextureBankGrad(int(samplerIndex), vec3(uv, float(texIndex)), ddx, ddy);
    }

    vec4 fillColor = vertexColor * textureColor;
    float borderWidth = float(quadBorderPacked.y & 0xFFFFu) / 16.0;
    bool hasRoundedCorners = any(notEqual(quadCornerRadiusPacked, uvec2(0u)));

    if (hasRoundedCorners || borderWidth > 0.0)
    {
        vec2 pos = texCoord * quadSize;
        vec4 cornerRadii = vec4(
            float(quadCornerRadiusPacked.x & 0xFFFFu),
            float(quadCornerRadiusPacked.x >> 16u),
            float(quadCornerRadiusPacked.y & 0xFFFFu),
            float(quadCornerRadiusPacked.y >> 16u)
        ) / 16.0;

        float distance = roundedRectDistance(pos, quadSize, cornerRadii);
        float antialiasWidth = max(fwidth(distance) * 0.5, EDGE_SOFTNESS);
        float outerCoverage = 1.0 - smoothstep(-antialiasWidth, antialiasWidth, distance);

        if (borderWidth > 0.0)
        {
            vec4 borderColor = unpackAndConvert(quadBorderPacked.x);
            float innerCoverage = 1.0 - smoothstep(-antialiasWidth, antialiasWidth, distance + borderWidth);
            float borderCoverage = max(outerCoverage - innerCoverage, 0.0);
            float fillAlpha = fillColor.a * innerCoverage;
            float borderAlpha = borderColor.a * borderCoverage;
            float combinedAlpha = fillAlpha + borderAlpha;
            vec3 combinedPremultiplied = fillColor.rgb * fillAlpha + borderColor.rgb * borderAlpha;

            fillColor.rgb = combinedAlpha > 0.000001
                ? combinedPremultiplied / combinedAlpha
                : vec3(0.0);
            fillColor.a = combinedAlpha;
        }
        else
        {
            fillColor.a *= outerCoverage;
        }
    }

    if (fillColor.a < alphaDiscardThreshold)
        discard;

    fragColor = fillColor;
}