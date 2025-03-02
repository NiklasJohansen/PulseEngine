#version 150 core

in vec2 position;
in vec2 texCoord;
 
out vec2 uv;
out vec2 blurUvs[14];
 
uniform float radius;

void main()
{
    blurUvs[0]  = texCoord + vec2(-0.028 * radius, 0.0);
    blurUvs[1]  = texCoord + vec2(-0.024 * radius, 0.0);
    blurUvs[2]  = texCoord + vec2(-0.020 * radius, 0.0);
    blurUvs[3]  = texCoord + vec2(-0.016 * radius, 0.0);
    blurUvs[4]  = texCoord + vec2(-0.012 * radius, 0.0);
    blurUvs[5]  = texCoord + vec2(-0.008 * radius, 0.0);
    blurUvs[6]  = texCoord + vec2(-0.004 * radius, 0.0);
    blurUvs[7]  = texCoord + vec2( 0.004 * radius, 0.0);
    blurUvs[8]  = texCoord + vec2(0.008  * radius, 0.0);
    blurUvs[9]  = texCoord + vec2(0.012  * radius, 0.0);
    blurUvs[10] = texCoord + vec2(0.016  * radius, 0.0);
    blurUvs[11] = texCoord + vec2(0.020  * radius, 0.0);
    blurUvs[12] = texCoord + vec2(0.024  * radius, 0.0);
    blurUvs[13] = texCoord + vec2(0.028  * radius, 0.0);

    uv = texCoord;
    gl_Position = vec4(position, 0.0, 1.0);
}