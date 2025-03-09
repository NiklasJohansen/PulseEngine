#version 150 core

in vec2 uv;

out vec3 fragColor;

uniform sampler2D srcTex;

uniform vec2 resolution;
uniform vec4 prefilterParams;
uniform bool prefilterEnabled;

// https://catlikecoding.com/unity/tutorials/advanced-rendering/bloom/
vec3 prefilter(vec3 c)
{
    float brightness = max(c.r, max(c.g, c.b));
    float soft = clamp(brightness - prefilterParams.y, 0, prefilterParams.z);
    soft = soft * soft * prefilterParams.w;
    float contribution = max(soft, brightness - prefilterParams.x) / max(brightness, 0.00001);
    return c * contribution;
}

// https://learnopengl.com/Guest-Articles/2022/Phys.-Based-Bloom
// https://www.iryoku.com/next-generation-post-processing-in-call-of-duty-advanced-warfare/
void main()
{
    vec2 texelSize = 1.0 / resolution;
    float x = texelSize.x;
    float y = texelSize.y;

    vec3 a = texture(srcTex, vec2(uv.x - 2 * x, uv.y + 2 * y)).rgb;
    vec3 b = texture(srcTex, vec2(uv.x,         uv.y + 2 * y)).rgb;
    vec3 c = texture(srcTex, vec2(uv.x + 2 * x, uv.y + 2 * y)).rgb;
    vec3 d = texture(srcTex, vec2(uv.x - 2 * x, uv.y)).rgb;
    vec3 e = texture(srcTex, vec2(uv.x,         uv.y)).rgb; // Current texel
    vec3 f = texture(srcTex, vec2(uv.x + 2 * x, uv.y)).rgb;
    vec3 g = texture(srcTex, vec2(uv.x - 2 * x, uv.y - 2 * y)).rgb;
    vec3 h = texture(srcTex, vec2(uv.x,         uv.y - 2 * y)).rgb;
    vec3 i = texture(srcTex, vec2(uv.x + 2 * x, uv.y - 2 * y)).rgb;
    vec3 j = texture(srcTex, vec2(uv.x - x,     uv.y + y)).rgb;
    vec3 k = texture(srcTex, vec2(uv.x + x,     uv.y + y)).rgb;
    vec3 l = texture(srcTex, vec2(uv.x - x,     uv.y - y)).rgb;
    vec3 m = texture(srcTex, vec2(uv.x + x,     uv.y - y)).rgb;

    vec3 result = (e * 0.125) + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;

    if (prefilterEnabled)
        result = prefilter(result);

    fragColor = max(result, 0.0000001f);
}