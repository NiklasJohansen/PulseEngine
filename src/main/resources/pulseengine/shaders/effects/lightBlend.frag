#version 420 core

in vec2 textureCoord;
out vec4 fragColor;

layout(binding=0) uniform sampler2D baseTex;
layout(binding=1) uniform sampler2D lightText;

uniform vec4 ambientColor;
uniform vec2 samplingOffset;
uniform vec2 resolution;

void main() {
    vec4 baseColor = texture(baseTex, textureCoord);
    vec4 lightColor = texture(lightText, textureCoord + samplingOffset);
    vec3 lighting = ambientColor.rgb + lightColor.rgb * baseColor.a;
    float alpha = (baseColor.a < 0.1) ? ambientColor.a : 1.0;

    fragColor = vec4(baseColor.rgb * lighting, alpha);
}