#version 150 core

in vec2 position;
in vec2 texCoord;
 
out vec2 v_texCoord;
out vec2 v_blurTexCoords[14];
 
uniform float radius;

void main()
{
    gl_Position = vec4(position, 0.0, 1.0);
    v_texCoord = texCoord;

    v_blurTexCoords[ 0] = v_texCoord + vec2(-0.028 * radius, 0.0);
    v_blurTexCoords[ 1] = v_texCoord + vec2(-0.024 * radius, 0.0);
    v_blurTexCoords[ 2] = v_texCoord + vec2(-0.020 * radius, 0.0);
    v_blurTexCoords[ 3] = v_texCoord + vec2(-0.016 * radius, 0.0);
    v_blurTexCoords[ 4] = v_texCoord + vec2(-0.012 * radius, 0.0);
    v_blurTexCoords[ 5] = v_texCoord + vec2(-0.008 * radius, 0.0);
    v_blurTexCoords[ 6] = v_texCoord + vec2(-0.004 * radius, 0.0);
    v_blurTexCoords[ 7] = v_texCoord + vec2( 0.004 * radius, 0.0);
    v_blurTexCoords[ 8] = v_texCoord + vec2( 0.008 * radius, 0.0);
    v_blurTexCoords[ 9] = v_texCoord + vec2( 0.012 * radius, 0.0);
    v_blurTexCoords[10] = v_texCoord + vec2( 0.016 * radius, 0.0);
    v_blurTexCoords[11] = v_texCoord + vec2( 0.020 * radius, 0.0);
    v_blurTexCoords[12] = v_texCoord + vec2( 0.024 * radius, 0.0);
    v_blurTexCoords[13] = v_texCoord + vec2( 0.028 * radius, 0.0);
}