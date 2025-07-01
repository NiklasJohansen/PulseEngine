#version 330 core

out ivec4 fragColor;

uniform isampler2D seedTex;

uniform int offset;
uniform ivec2 resolution;
uniform bool computeInternal;

void main()
{
    ivec2 fragCoord = ivec2(gl_FragCoord.xy);
    ivec2 nearestExternalSeed = ivec2(0);
    ivec2 nearestInternalSeed = ivec2(0);
    int nearestExternalDist = 100000000;
    int nearestInternalDist = 100000000;

    for (int dy = -1; dy <= 1; dy++)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            ivec2 neighbor = fragCoord + ivec2(dx, dy) * offset;
            if (neighbor.x < 0 || neighbor.y < 0 || neighbor.x >= resolution.x || neighbor.y >= resolution.y)
                continue;

            ivec4 seed = texelFetch(seedTex, neighbor, 0); // xy = external, zw = internal

            if (seed.x >= 0) // Check external seed
            {
                ivec2 d = seed.xy - fragCoord;
                int dist = d.x * d.x + d.y * d.y;
                if (dist < nearestExternalDist)
                {
                    nearestExternalDist = dist;
                    nearestExternalSeed = seed.xy;
                }
            }

            if (computeInternal && seed.z >= 0) // Check internal seed
            {
                ivec2 d = seed.zw - fragCoord;
                int dist = d.x * d.x + d.y * d.y;
                if (dist < nearestInternalDist)
                {
                    nearestInternalDist = dist;
                    nearestInternalSeed = seed.zw;
                }
            }
        }
    }

    fragColor = ivec4(nearestExternalSeed, nearestInternalSeed);
}