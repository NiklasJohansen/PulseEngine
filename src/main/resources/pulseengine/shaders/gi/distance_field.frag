#version 330 core

in vec2 uv;

layout(location=0) out vec4 externalOut;
layout(location=1) out vec4 internalOut;

uniform sampler2D jfaExternalTex;
uniform sampler2D jfaInternalTex;

void main()
{
    vec4 nearestSeedInternal = texture(jfaInternalTex, uv);
    vec4 nearestSeedExternal = texture(jfaExternalTex, uv);

    vec2 nearestLocalSeedInternal  = nearestSeedInternal.xy;
    vec2 nearestLocalSeedExternal  = nearestSeedExternal.xy;
    vec2 nearestGlobalSeedExternal = nearestSeedExternal.zw;

    vec2 localDistVectorInternal = nearestLocalSeedInternal - uv;
    float localDistExternal  = distance(uv, nearestLocalSeedExternal);
    float globalDistExternal = distance(uv, nearestGlobalSeedExternal);

    externalOut = vec4(localDistExternal, globalDistExternal, 0.0, 1.0);
    internalOut = vec4(localDistVectorInternal, 0.0, 1.0);
}