#version 420 core

in vec2 uv;
out vec4 fragColor;

layout(binding=0) uniform sampler2D baseTex;
layout(binding=1) uniform sampler2D lightTex;

uniform vec2 sampleOffset;
uniform float dithering;

void main()
{
    vec4 baseColor = texture(baseTex, uv);
    vec4 lightColor = texture(lightTex, uv + sampleOffset);
    vec3 finalColor = baseColor.rgb * lightColor.rgb;

    // Add some dithering to prevent color banding
    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);
    finalColor += mix(-dithering / 255.0, dithering / 255.0, noise);

    fragColor = vec4(finalColor, baseColor.a);
}