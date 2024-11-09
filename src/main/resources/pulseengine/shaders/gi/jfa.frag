#version 330 core

in vec2 uv;
layout(location=0) out vec4 outside;
layout(location=1) out vec4 inside;

uniform sampler2D outsideTex;
uniform sampler2D insideTex;

uniform vec2 resolution;
uniform float uOffset;

void main()
{
    vec2 nearestLocalSeedInside = vec2(0.0);
    float nearestLocalDistInside = 9999.0;

    vec2 nearestLocalSeed = vec2(0.0);
    float nearestLocalDist = 9999.0;

    vec2 nearestGlobalSeed = vec2(0.0);
    float nearestGlobalDist = 9999.0;

    vec2 invRes = 1.0 / resolution;

    for (float y = -1.0; y <= 1.0; y += 1.0)
    {
        for (float x = -1.0; x <= 1.0; x += 1.0)
        {
            vec2 samplePos = uv + vec2(x, y) * uOffset * invRes;

            if (samplePos.x < 0.0 || samplePos.x > 1.0 || samplePos.y < 0.0 || samplePos.y > 1.0)
                continue;

            vec4 uvSample = texture(outsideTex, samplePos);
            vec2 localSample = uvSample.xy;
            vec2 globalSample = uvSample.zw;
            vec2 localSampleInside = texture(insideTex, samplePos).xy;

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

            if (localSampleInside.x != 0.0 || localSampleInside.y != 0.0)
            {
                vec2 diff = localSampleInside - uv;
                float dist = dot(diff, diff);
                if (dist < nearestLocalDistInside)
                {
                    nearestLocalDistInside = dist;
                    nearestLocalSeedInside = localSampleInside;
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

    outside = vec4(nearestLocalSeed, nearestGlobalSeed);
    inside = vec4(nearestLocalSeedInside, 0.0, 1.0);
}