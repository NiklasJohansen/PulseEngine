#version 150 core

in vec2 textureCoord;

out vec4 fragColor;

uniform sampler2D tex;
uniform vec2 resolution;

vec4 applyVignette(vec4 color)
{

    vec2 uv = gl_FragCoord.xy / resolution.xy;

    uv *=  1.0 - uv.yx;

    float vig = uv.x*uv.y * 15.0;

    vig = pow(vig, 0.25);

    return color * vig;
}

void main() {
    vec4 textureColor = texture(tex, textureCoord);
    fragColor = applyVignette(textureColor);
}