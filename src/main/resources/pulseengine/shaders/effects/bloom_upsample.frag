#version 150 core

uniform sampler2D srcTexture;

uniform float filterRadius;
uniform float intensity;

in vec2 uv;
out vec3 fragColor;

// https://learnopengl.com/Guest-Articles/2022/Phys.-Based-Bloom
// https://www.iryoku.com/next-generation-post-processing-in-call-of-duty-advanced-warfare/
void main()
{
    float x = filterRadius;
    float y = filterRadius;

    vec3 a = texture(srcTexture, vec2(uv.x - x, uv.y + y)).rgb;
    vec3 b = texture(srcTexture, vec2(uv.x,     uv.y + y)).rgb;
    vec3 c = texture(srcTexture, vec2(uv.x + x, uv.y + y)).rgb;
    vec3 d = texture(srcTexture, vec2(uv.x - x, uv.y)).rgb;
    vec3 e = texture(srcTexture, vec2(uv.x,     uv.y)).rgb;
    vec3 f = texture(srcTexture, vec2(uv.x + x, uv.y)).rgb;
    vec3 g = texture(srcTexture, vec2(uv.x - x, uv.y - y)).rgb;
    vec3 h = texture(srcTexture, vec2(uv.x,     uv.y - y)).rgb;
    vec3 i = texture(srcTexture, vec2(uv.x + x, uv.y - y)).rgb;

    fragColor = ((e * 4.0) + (b + d + f + h) * 2.0 + (a + c + g + i)) / 16.0 * intensity;
}