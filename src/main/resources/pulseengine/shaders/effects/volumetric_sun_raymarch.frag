#version 330 core

#define CASCADE_COUNT 4
#define MAX_STEPS 96

in vec2 uv;

layout(location = 0) out vec3 outScattering;

uniform sampler2D uDepthTex;
uniform sampler2DShadow uShadowMapTex;

uniform mat4 uInvViewProjection;
uniform mat4 uView;
uniform mat4 uShadowViewProjections[CASCADE_COUNT];
uniform vec4 uShadowCascadeSplitDistances;

uniform vec3 uCameraPos;
uniform vec3 uSunDirection;
uniform vec4 uSunColor;

uniform float uSunRadius;
uniform float uIntensity;
uniform float uDensity;
uniform float uAnisotropy;
uniform float uMaxDistance;
uniform float uJitterStrength;
uniform float uHeightFogStart;
uniform float uHeightFogFalloff;
uniform float uShadowMapTexSize;

uniform int uStepCount;

const float PI = 3.14159265359;
const vec2 CASCADE_OFFSETS[CASCADE_COUNT] = vec2[](
    vec2(0.0, 0.0),
    vec2(0.5, 0.0),
    vec2(0.0, 0.5),
    vec2(0.5, 0.5)
);

const vec2 SHADOW_TAPS[8] = vec2[](
    vec2(-0.7071, -0.7071),
    vec2( 0.0000, -1.0000),
    vec2( 0.7071, -0.7071),
    vec2(-1.0000,  0.0000),
    vec2( 1.0000,  0.0000),
    vec2(-0.7071,  0.7071),
    vec2( 0.0000,  1.0000),
    vec2( 0.7071,  0.7071)
);

float interleavedGradientNoise(vec2 uv)
{
    return fract(52.9829189 * fract( dot( uv, vec2(.06711056, .00583715) ) ) );
}

vec3 reconstructWorldPosition(vec2 uvCoord, float depth01)
{
    vec4 clip = vec4(uvCoord * 2.0 - 1.0, depth01 * 2.0 - 1.0, 1.0);
    vec4 world = uInvViewProjection * clip;
    return world.xyz / max(world.w, 1e-6);
}

vec3 worldRayDirection(vec2 uvCoord)
{
    vec4 farClip = vec4(uvCoord * 2.0 - 1.0, 1.0, 1.0);
    vec4 farWorld = uInvViewProjection * farClip;
    vec3 farPos = farWorld.xyz / max(farWorld.w, 1e-6);
    return normalize(farPos - uCameraPos);
}

float phaseHenyeyGreenstein(float mu, float g)
{
    float g2 = g * g;
    float denom = max(1.0 + g2 - 2.0 * g * mu, 1e-3);
    return (1.0 - g2) / (4.0 * PI * pow(denom, 1.5));
}

float sampleShadowCascade(vec3 worldPos, int cascade)
{
    vec4 lightPos = uShadowViewProjections[cascade] * vec4(worldPos, 1.0);
    vec3 pos = (lightPos.xyz / max(lightPos.w, 1e-6)) * 0.5 + 0.5;

    if (pos.x < 0.0 || pos.x > 1.0 || pos.y < 0.0 || pos.y > 1.0 || pos.z < 0.0 || pos.z > 1.0)
        return 1.0;

    vec2 atlasOffset = CASCADE_OFFSETS[cascade];
    vec2 atlasUv = pos.xy * 0.5 + atlasOffset;
    float halfRes = uShadowMapTexSize * 0.5;
    float texelUv = 1.0 / max(halfRes, 1.0);
    float filterRadius = texelUv * (1.5 + uSunRadius);
    float bias = max(0.00035, texelUv * 0.75);

    vec2 clampMin = atlasOffset + vec2(texelUv * 0.5);
    vec2 clampMax = atlasOffset + vec2(0.5) - vec2(texelUv * 0.5);

    float visibility = 0.0;
    for (int i = 0; i < 8; i++)
    {
        vec2 sampleUv = clamp(atlasUv + SHADOW_TAPS[i] * filterRadius, clampMin, clampMax);
        visibility += texture(uShadowMapTex, vec3(sampleUv, pos.z - bias));
    }

    return visibility / 8.0;
}

float sampleShadow(vec3 worldPos)
{
    float viewDepth = -(uView * vec4(worldPos, 1.0)).z;
    int cascade = CASCADE_COUNT - 1;

    for (int i = 0; i < CASCADE_COUNT; i++)
    {
        if (viewDepth < uShadowCascadeSplitDistances[i])
        {
            cascade = i;
            break;
        }
    }

    float shadow = sampleShadowCascade(worldPos, cascade);

    if (cascade < CASCADE_COUNT - 1)
    {
        float prevSplit = cascade > 0 ? uShadowCascadeSplitDistances[cascade - 1] : 0.0;
        float split = uShadowCascadeSplitDistances[cascade];
        float blendZone = (split - prevSplit) * 0.15;
        float distToEdge = split - viewDepth;

        if (distToEdge < blendZone)
        {
            float nextShadow = sampleShadowCascade(worldPos, cascade + 1);
            float t = smoothstep(0.0, blendZone, distToEdge);
            shadow = mix(nextShadow, shadow, t);
        }
    }

    return shadow;
}

float sampleMediumDensity(vec3 worldPos)
{
    float heightFactor = exp(-max(worldPos.y - uHeightFogStart, 0.0) * uHeightFogFalloff);
    return uDensity * heightFactor;
}

void main()
{
    float depth01 = texture(uDepthTex, uv).r;
    vec3 rayDir = worldRayDirection(uv);

    float rayLength = uMaxDistance;
    if (depth01 < 0.999999)
    {
        vec3 scenePos = reconstructWorldPosition(uv, depth01);
        rayLength = min(length(scenePos - uCameraPos), uMaxDistance);
    }

    if (rayLength <= 0.001)
    {
        outScattering = vec3(0.0);
        return;
    }

    int stepCount = clamp(uStepCount, 1, MAX_STEPS);
    float stepLength = rayLength / float(stepCount);
    float jitter = (interleavedGradientNoise(gl_FragCoord.xy) - 0.5) * uJitterStrength;
    float phase = phaseHenyeyGreenstein(clamp(dot(-rayDir, uSunDirection), -1.0, 1.0), clamp(uAnisotropy, -0.98, 0.98));

    float transmittance = 1.0;
    vec3 scattering = vec3(0.0);

    for (int i = 0; i < MAX_STEPS; i++)
    {
        if (i >= stepCount)
            break;

        float t = (float(i) + 0.5 + jitter) * stepLength;
        t = clamp(t, 0.0, rayLength);

        vec3 samplePos = uCameraPos + rayDir * t;
        float density = sampleMediumDensity(samplePos);
        float shadow = sampleShadow(samplePos);
        vec3 inscattering = uSunColor.rgb * (uIntensity * density * phase * shadow);

        scattering += transmittance * inscattering * stepLength;
        transmittance *= exp(-density * stepLength);

        if (transmittance < 0.01)
            break;
    }

    outScattering = scattering;
}
