#version 330 core

in vec2 uv;
out vec4 fragColor;
uniform sampler2DArray skyTexture;
uniform vec3 skyDescriptor;
uniform float brightness;
uniform int face;

vec3 faceDirection(vec2 p)
{
    if (face == 0) return vec3( 1.0, -p.y, -p.x);
    if (face == 1) return vec3(-1.0, -p.y,  p.x);
    if (face == 2) return vec3( p.x,  1.0,  p.y);
    if (face == 3) return vec3( p.x, -1.0, -p.y);
    if (face == 4) return vec3( p.x, -p.y,  1.0);
    return vec3(-p.x, -p.y, -1.0);
}

void main()
{
    vec3 dir = normalize(faceDirection(uv * 2.0 - 1.0));
    vec2 latLong = vec2(atan(dir.z, dir.x) / 6.28318530718 + 0.5, acos(clamp(dir.y, -1.0, 1.0)) / 3.14159265359);
    fragColor = vec4(textureLod(skyTexture, vec3(latLong * skyDescriptor.yz, skyDescriptor.x), 0.0).rgb * brightness, 1.0);
}