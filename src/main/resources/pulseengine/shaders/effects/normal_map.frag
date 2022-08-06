#version 150 core

in vec2 texStart;
in vec2 texSize;
in vec2 texCoord;
in float texAngleRad;
in vec2 texTiling;
in float texIndex;
in vec2 quadSize;
in float scale;

out vec4 fragColor;

uniform sampler2DArray textureArray;

vec2 rotate(vec2 v, float a) {
    float s = sin(a);
    float c = cos(a);
    mat2 m = mat2(-c, -s, -s, c);
    return m * v;
}

void main() {
    vec4 normal = vec4(0.5, 0.5, 1.0, 1.0);

    if (texIndex >= 0)
    {
        vec2 sampleCoord = texStart + texSize * (texTiling == 1.0 ? texCoord : fract(texCoord * texTiling));
        normal = texture(textureArray, vec3(sampleCoord, floor(texIndex)));

        // Scale and rotate normal
        normal.xy = (rotate((normal.xy * 2.0 - 1.0) * scale, texAngleRad) + 1.0) * 0.5;
    }

    if (normal.a < 0.5)
        discard;

    fragColor = normal;
}