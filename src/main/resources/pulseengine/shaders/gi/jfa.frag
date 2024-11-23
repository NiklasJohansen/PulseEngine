#version 330 core

in vec2 uv;
layout(location=0) out vec4 externalOut;
layout(location=1) out vec4 internalOut;

uniform sampler2D externalTex;
uniform sampler2D internalTex;

uniform vec2 resolution;
uniform float uOffset;

void main()
{
    vec2 nearestLocalSeedInternal = vec2(0.0);
    vec2 nearestLocalSeedExternal = vec2(0.0);
    vec2 nearestGlobalSeedExternal = vec2(0.0);

    float nearestLocalDistInternal = 9999.0;
    float nearestLocalDistExternal = 9999.0;
    float nearestGlobalDistExternal = 9999.0;

    vec2 invRes = 1.0 / resolution;

    for (float y = -1.0; y <= 1.0; y += 1.0)
    {
        for (float x = -1.0; x <= 1.0; x += 1.0)
        {
            vec2 samplePos = uv + vec2(x, y) * uOffset * invRes;

            if (samplePos.x < 0.0 || samplePos.x > 1.0 || samplePos.y < 0.0 || samplePos.y > 1.0)
                continue;

            vec4 sampleInternal = texture(externalTex, samplePos);
            vec2 localSampleExternal = sampleInternal.xy;
            vec2 globalSampleExternal = sampleInternal.zw;
            vec2 localSampleInternal = texture(internalTex, samplePos).xy;

            if (localSampleExternal.x != 0.0 || localSampleExternal.y != 0.0)
            {
                vec2 diff = localSampleExternal - uv;
                float dist = dot(diff, diff);
                if (dist < nearestLocalDistExternal)
                {
                    nearestLocalDistExternal = dist;
                    nearestLocalSeedExternal = localSampleExternal;
                }
            }

            if (localSampleInternal.x != 0.0 || localSampleInternal.y != 0.0)
            {
                vec2 diff = localSampleInternal - uv;
                float dist = dot(diff, diff);
                if (dist < nearestLocalDistInternal)
                {
                    nearestLocalDistInternal = dist;
                    nearestLocalSeedInternal = localSampleInternal;
                }
            }

            if (globalSampleExternal.x != 0.0 || globalSampleExternal.y != 0.0)
            {
                vec2 diff = globalSampleExternal - uv;
                float dist = dot(diff, diff);
                if (dist < nearestGlobalDistExternal)
                {
                    nearestGlobalDistExternal = dist;
                    nearestGlobalSeedExternal = globalSampleExternal;
                }
            }
        }
    }

    externalOut = vec4(nearestLocalSeedExternal, nearestGlobalSeedExternal);
    internalOut = vec4(nearestLocalSeedInternal, 0.0, 1.0);
}