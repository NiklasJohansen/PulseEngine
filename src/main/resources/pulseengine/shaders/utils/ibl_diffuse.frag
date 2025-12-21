#version 330 core

const float PI = 3.14159265359;

in vec2 uv;
out vec4 fragColor;

uniform sampler2DArray textureArray;
uniform vec3 srcEnv; // x: layer, y: uMax, z: vMax

vec2 dirToLatLong(vec3 dir)
{
    dir = normalize(dir);
    float phi = atan(dir.z, dir.x);
    float theta = acos(clamp(dir.y, -1.0, 1.0));
    float u = phi / (2.0 * PI) + 0.5;
    float v = theta / PI;
    return vec2(u, v);
}

vec3 latLongToDir(vec2 uv)
{
    float phi = (uv.x - 0.5) * 2.0 * PI;
    float theta = uv.y * PI;
    float x = cos(phi) * sin(theta);
    float y = cos(theta);
    float z = sin(phi) * sin(theta);
    return vec3(x, y, z);
}

vec3 sampleEnv(vec3 dir)
{
    float layer = srcEnv.x;
    vec2 uvMax = srcEnv.yz;
    vec2 uv = dirToLatLong(dir) * uvMax;
    return texture(textureArray, vec3(uv, layer)).rgb;
}

void main()
{
    vec3 N = normalize(latLongToDir(uv));
    vec3 up = abs(N.y) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 T = normalize(cross(up, N));
    vec3 B = cross(N, T);
    
    const int SAMPLE_PHI = 128;
    const int SAMPLE_THETA = 32;
    const float invSamples = 1.0 / float(SAMPLE_PHI * SAMPLE_THETA);

    vec3 irradiance = vec3(0.0);

    for (int i = 0; i < SAMPLE_PHI; ++i)
    {
        float phi = 2.0 * PI * (float(i) + 0.5) / float(SAMPLE_PHI);

        for (int j = 0; j < SAMPLE_THETA; ++j)
        {
            float theta = 0.5 * PI * (float(j) + 0.5) / float(SAMPLE_THETA); // [0, pi/2]
            float sinTheta = sin(theta);
            float cosTheta = cos(theta);
            vec3 Llocal = vec3(
                cos(phi) * sinTheta,
                cosTheta,
                sin(phi) * sinTheta
            );
            
            vec3 L = normalize(T * Llocal.x + N * Llocal.y + B * Llocal.z);
            float NdotL = max(dot(N, L), 0.0);

            if (NdotL > 0.0)
            {
                vec3 envColor = sampleEnv(L);
                irradiance += envColor * NdotL * sinTheta;
            }
        }
    }

    irradiance *= PI * (1.0 / float(SAMPLE_PHI * SAMPLE_THETA));

    fragColor = vec4(irradiance, 1.0);
}