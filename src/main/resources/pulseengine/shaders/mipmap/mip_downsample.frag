#version 330 core

out vec4 fragColor;

uniform sampler2D tex;

uniform int prevMipLevel;
uniform ivec2 prevTexSize;

void main()
{
    ivec2 currLevelPos = ivec2(gl_FragCoord.xy - vec2(0.5));
    ivec2 prevLevelOrigin = currLevelPos * 2; // Origin of the 2×2 block in the previous mip level
    vec3 sum = vec3(0.0);

    for (int y = 0; y < 2; y++)
    {
        for (int x = 0; x < 2; x++)
        {
            ivec2 prevLevelPos = clamp(prevLevelOrigin + ivec2(x, y), ivec2(0), prevTexSize - 1);
            vec2 uv = (prevLevelPos + 0.5) / prevTexSize;
            sum += textureLod(tex, uv, float(prevMipLevel)).xyz;
        }
    }

    fragColor = vec4(sum * 0.25, 1.0); // Average of the 2×2 block
}