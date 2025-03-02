#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D jfaTex;

const float sdfEncodeScale = 65000 / sqrt(2);

void main()
{
    vec4 nearestSeed = texture(jfaTex, uv);
    vec2 nearestOustideSeed = nearestSeed.xy;
    float dist = distance(nearestOustideSeed, uv);

    if (dist == 0.0) // Inside
    {
        vec2 nearestInsideSeed = nearestSeed.zw;
        dist = -distance(nearestInsideSeed, uv);
    }

    fragColor = vec4(dist * sdfEncodeScale, 0.0, 0.0, 1.0);
}