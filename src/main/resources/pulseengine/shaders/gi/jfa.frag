#version 150 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D inputTexture;

uniform vec2 resolution;
uniform float uOffset;
uniform float index;

void main()
{
    vec4 nearestSeed = vec4(0.0);
    float nearestDist = 9999.0;
    vec2 invRes = 1.0 / resolution;

    for (float y = -1.0; y <= 1.0; y += 1.0)
    {
        for (float x = -1.0; x <= 1.0; x += 1.0)
        {
            vec2 sampleUV = uv + vec2(x, y) * uOffset * invRes;

            if (sampleUV.x < 0.0 || sampleUV.x > 1.0 || sampleUV.y < 0.0 || sampleUV.y > 1.0)
                continue;

            vec4 sampleValue = texture(inputTexture, sampleUV);

            if (sampleValue.x != 0.0 || sampleValue.y != 0.0)
            {
                vec2 diff = sampleValue.xy - uv;
                float dist = dot(diff, diff);
                if (dist < nearestDist)
                {
                    nearestDist = dist;
                    nearestSeed = sampleValue;
                }
            }
        }
    }

    fragColor = vec4(nearestSeed.xy, 0, 1);
}