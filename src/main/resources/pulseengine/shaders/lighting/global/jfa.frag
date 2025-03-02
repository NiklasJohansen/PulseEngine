#version 330 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D seedTex;

uniform float uOffset;
uniform vec2 resolution;

void main()
{
    vec2 nearestOutsideSeed = vec2(0.0);
    vec2 nearestInsideSeed = vec2(0.0);
    float nearestOutsideDist = 9999.0;
    float nearestInsideDist = 9999.0;

    vec2 invRes = 1.0 / resolution;

    for (float y = -1.0; y <= 1.0; y += 1.0)
    {
        for (float x = -1.0; x <= 1.0; x += 1.0)
        {
            vec2 samplePos = uv + vec2(x, y) * uOffset * invRes;

            if (samplePos.x < 0.0 || samplePos.x > 1.0 || samplePos.y < 0.0 || samplePos.y > 1.0)
                continue;

            vec4 seed = texture(seedTex, samplePos);
            vec2 outsideSeed = seed.xy;
            vec2 insideSeed = seed.zw;

            if (outsideSeed.x != 0.0 || outsideSeed.y != 0.0)
            {
                vec2 diff = outsideSeed - uv;
                float dist = dot(diff, diff);
                if (dist < nearestOutsideDist)
                {
                    nearestOutsideDist = dist;
                    nearestOutsideSeed = outsideSeed;
                }
            }

            if (insideSeed.x != 0.0 || insideSeed.y != 0.0)
            {
                vec2 diff = insideSeed - uv;
                float dist = dot(diff, diff);
                if (dist < nearestInsideDist)
                {
                    nearestInsideDist = dist;
                    nearestInsideSeed = insideSeed;
                }
            }
        }
    }

    fragColor = vec4(nearestOutsideSeed, nearestInsideSeed);
}