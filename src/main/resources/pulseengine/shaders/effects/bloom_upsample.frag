#version 150 core

in vec2 uv;

out vec3 fragColor;

uniform sampler2D srcTex;

uniform float filterRadius;
uniform float intensity;

// https://learnopengl.com/Guest-Articles/2022/Phys.-Based-Bloom
// https://www.iryoku.com/next-generation-post-processing-in-call-of-duty-advanced-warfare/
void main()
{
    vec2 texelSize = 1.0 / vec2(textureSize(srcTex, 0));
    float x = filterRadius * texelSize.x;
    float y = filterRadius * texelSize.y;

    vec3 a = texture(srcTex, vec2(uv.x - x, uv.y + y)).rgb;
    vec3 b = texture(srcTex, vec2(uv.x,     uv.y + y)).rgb;
    vec3 c = texture(srcTex, vec2(uv.x + x, uv.y + y)).rgb;
    vec3 d = texture(srcTex, vec2(uv.x - x, uv.y)).rgb;
    vec3 e = texture(srcTex, vec2(uv.x,     uv.y)).rgb;
    vec3 f = texture(srcTex, vec2(uv.x + x, uv.y)).rgb;
    vec3 g = texture(srcTex, vec2(uv.x - x, uv.y - y)).rgb;
    vec3 h = texture(srcTex, vec2(uv.x,     uv.y - y)).rgb;
    vec3 i = texture(srcTex, vec2(uv.x + x, uv.y - y)).rgb;

    fragColor = ((e * 4.0) + (b + d + f + h) * 2.0 + (a + c + g + i)) / 16.0 * intensity;
}