#version 150 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D sceneTex;

uniform vec2 resolution;
uniform float uOffset;
uniform float index;

void main()
{
    vec2 nearestLocalSeed = vec2(0.0);
    vec2 nearestGlobalSeed = vec2(0.0);
    float nearestLocalDist = 9999.0;
    float nearestGlobalDist = 9999.0;

    vec2 invRes = 1.0 / resolution;

    for (float y = -1.0; y <= 1.0; y += 1.0)
    {
        for (float x = -1.0; x <= 1.0; x += 1.0)
        {
            vec2 sampleUV = uv + vec2(x, y) * uOffset * invRes;

            if (sampleUV.x < 0.0 || sampleUV.x > 1.0 || sampleUV.y < 0.0 || sampleUV.y > 1.0)
                continue;

            vec4 sceneSample = texture(sceneTex, sampleUV);
            vec2 localSample = sceneSample.xy;
            vec2 globalSample = sceneSample.zw;

            if (localSample.x != 0.0 || localSample.y != 0.0)
            {
                vec2 diff = localSample - uv;
                float dist = dot(diff, diff);
                if (dist < nearestLocalDist)
                {
                    nearestLocalDist = dist;
                    nearestLocalSeed = localSample;
                }
            }

            if (globalSample.x != 0.0 || globalSample.y != 0.0)
            {
                vec2 diff = globalSample - uv;
                float dist = dot(diff, diff);
                if (dist < nearestGlobalDist)
                {
                    nearestGlobalDist = dist;
                    nearestGlobalSeed = globalSample;
                }
            }
        }
    }

    fragColor = vec4(nearestLocalSeed, nearestGlobalSeed);
}