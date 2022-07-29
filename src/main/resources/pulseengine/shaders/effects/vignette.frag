#version 150 core

in vec2 textureCoord;

out vec4 fragColor;

uniform sampler2D tex;
uniform vec2 resolution;
uniform float strength;

void main() {
    vec4 texColor = texture(tex, textureCoord);

    vec2 uv = gl_FragCoord.xy / resolution.xy;
    uv *= 1.0 - uv.yx;

    float vig = uv.x * uv.y * 15.0;
    vig = pow(vig, 0.25 * strength);

    fragColor = vec4(texColor.rgb * vig, texColor.a);
}