#version 150 core

in vec4 vertexColor;
flat in vec2 dashAxis;

uniform sampler2D sceneDepth;
uniform bool hasSceneDepth;
uniform vec2 viewportSize;
uniform vec2 screenSize;

out vec4 fragColor;

void main()
{
    // Six-pixel dashes and gaps, anchored in screen space rather than at each line's start.
    vec2 pixel = gl_FragCoord.xy / viewportSize * screenSize;
    if (dot(dashAxis, dashAxis) > 0.0 && mod(dot(pixel, dashAxis), 12.0) >= 6.0)
        discard;

    fragColor = vertexColor;

    if (hasSceneDepth)
    {
        const float depthBias = 0.00001f;
        float depth = textureLod(sceneDepth, gl_FragCoord.xy / viewportSize, 0.0).r;

        if (depth < 1.0 && gl_FragCoord.z > depth + depthBias)
        {
            fragColor.rgb = vec3(1.0);
            fragColor.a *= 0.02;
        }
    }
}