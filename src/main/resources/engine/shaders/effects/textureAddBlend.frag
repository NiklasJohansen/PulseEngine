#version 420 core

in vec2 textureCoord;
out vec4 fragColor;

layout(binding=0) uniform sampler2D tex0;
layout(binding=1) uniform sampler2D tex1;

uniform float exposure;

void main() {

    vec4 tex0Color = texture(tex0, textureCoord);
    vec4 tex1Color = texture(tex1, textureCoord);

    // Additive blending
    tex0Color += tex1Color;

    // Tone mapping
    tex0Color = vec4(1.0) - exp(-tex0Color * exposure);

    fragColor = tex0Color;
}