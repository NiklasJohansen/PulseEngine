#version 330 core

out vec4 fragColor;

uniform isampler2D jfaTex;

uniform bool isSigned;

void main()
{
    ivec2 fragCoord = ivec2(gl_FragCoord.xy);
    ivec4 seed = texelFetch(jfaTex, fragCoord, 0); // xy = external, zw = internal
    ivec2 eDelta = seed.xy - fragCoord;
    int externalDist = eDelta.x * eDelta.x + eDelta.y * eDelta.y;

    if (isSigned)
    {
        ivec2 iDelta = seed.zw - fragCoord;
        int internalDist = iDelta.x * iDelta.x + iDelta.y * iDelta.y;
        float dist = (externalDist > internalDist) ? sqrt(float(externalDist)) : -sqrt(float(internalDist));
        fragColor = vec4(dist, 0.0, 0.0, 0.0);
    }
    else
    {
        fragColor = vec4(sqrt(float(externalDist)), 0.0, 0.0, 0.0);
    }
}