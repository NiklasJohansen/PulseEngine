#version 330 core

const float PI = 3.14159265359;

in vec3 worldDir;
out vec4 fragColor;

uniform sampler2DArray textureArray;
uniform vec3 texDesc; // x: layer, y: uMax, z: vMax

vec2 dirToLatLong(vec3 dir)
{
    dir = normalize(dir);
    float phi = atan(dir.z, dir.x);
    float theta = acos(clamp(dir.y, -1.0, 1.0));
    float u = phi / (2.0 * PI) + 0.5;
    float v = theta / PI;
    return vec2(u, v);
}

void main()
{
    float layer = texDesc.x;
    vec2 uvMax = texDesc.yz;
    vec2 uv = dirToLatLong(worldDir) * uvMax;

    fragColor = textureLod(textureArray, vec3(uv, layer), 0);
}