#version 150 core

in vec2 position;
in vec2 texCoord;

out vec2 texCoordNW;
out vec2 texCoordNE;
out vec2 texCoordSW;
out vec2 texCoordSE;
out vec2 texCoordM;
out vec2 baseTexCoord;

uniform vec2 resolution;
uniform vec2 samplingOffset; // Used to prevent jitter when lightmap scale is below 1.0

void main() {
    vec2 fragCoord = texCoord * resolution;
    vec2 invRes = 1.0 / resolution;

    // Calculate offset texture coordinates for FXAA
    texCoordNW = samplingOffset + (fragCoord + vec2(-1.0, -1.0)) * invRes;
    texCoordNE = samplingOffset + (fragCoord + vec2(1.0,  -1.0)) * invRes;
    texCoordSW = samplingOffset + (fragCoord + vec2(-1.0,  1.0)) * invRes;
    texCoordSE = samplingOffset + (fragCoord + vec2(1.0,   1.0)) * invRes;
    texCoordM  = samplingOffset + texCoord;

    baseTexCoord = texCoord;
    gl_Position = vec4(position, 0.0, 1.0);
}