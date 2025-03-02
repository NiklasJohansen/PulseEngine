#version 150 core

in vec2 position;
in vec2 texCoord;

out vec2 uv;
out vec2 uvLastFrame;

uniform mat4 lastViewProjectionMatrix;
uniform mat4 currentViewProjectionMatrix;
uniform vec2 resolution;

void main()
{
    vec4 homogeneousTexCoord = vec4((texCoord) * 2.0 - 1.0, 0.0, 1.0);
    vec4 worldPos = inverse(currentViewProjectionMatrix) * homogeneousTexCoord;
    vec4 reprojectedPos = lastViewProjectionMatrix * worldPos;
    uvLastFrame = (reprojectedPos.xy / reprojectedPos.w) * 0.5 + 0.5; // Convert back from non-homogeneous coordinates
    uv = texCoord;
    gl_Position = vec4(position, 0.0, 1.0);
}