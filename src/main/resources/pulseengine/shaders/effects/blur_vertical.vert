#version 150 core

in vec2 position;
in vec2 texCoord;

out vec2 uv;
out vec2 blurUvs[14];

uniform float radius; 

void main()
{
    blurUvs[0]  = texCoord + vec2(0.0, -0.028 * radius);
    blurUvs[1]  = texCoord + vec2(0.0, -0.024 * radius);
    blurUvs[2]  = texCoord + vec2(0.0, -0.020 * radius);
    blurUvs[3]  = texCoord + vec2(0.0, -0.016 * radius);
    blurUvs[4]  = texCoord + vec2(0.0, -0.012 * radius);
    blurUvs[5]  = texCoord + vec2(0.0, -0.008 * radius);
    blurUvs[6]  = texCoord + vec2(0.0, -0.004 * radius);
    blurUvs[7]  = texCoord + vec2(0.0,  0.004 * radius);
    blurUvs[8]  = texCoord + vec2(0.0,  0.008 * radius);
    blurUvs[9]  = texCoord + vec2(0.0,  0.012 * radius);
    blurUvs[10] = texCoord + vec2(0.0,  0.016 * radius);
    blurUvs[11] = texCoord + vec2(0.0,  0.020 * radius);
    blurUvs[12] = texCoord + vec2(0.0,  0.024 * radius);
    blurUvs[13] = texCoord + vec2(0.0,  0.028 * radius);

    uv = texCoord;
    gl_Position = vec4(position, 0.0, 1.0);
}