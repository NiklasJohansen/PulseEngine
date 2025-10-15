#version 150 core

in vec2 uv;
in vec2 quadUv;
in vec4 vertexColor;

out vec4 fragColor;

uniform sampler2D tex;
uniform bool sampleTexture;
uniform float cornerRadius;
uniform vec2 size;

void main()
{
    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);
    if (sampleTexture)
    {
        textureColor = texture(tex, uv);

        if (cornerRadius > 0.0)
        {
            vec2 pos = quadUv * size;
            float border = clamp(cornerRadius, 0.0, 0.5 * min(size.x, size.y));
            vec2 corner = clamp(pos, vec2(border), size - border);
            float distFromCorner = length(pos - corner) - border;
            textureColor.a *= 1.0f - smoothstep(0.0, 0.01, distFromCorner);
        }
    }

    fragColor = vertexColor * textureColor;
}