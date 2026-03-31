#version 330 core

in vec2 uv;

layout(location = 0) out vec4 fragColor;

uniform sampler2D uSceneColorTex;
uniform sampler2D uVolumeTex;

void main()
{
    vec4 scene = texture(uSceneColorTex, uv);
    vec3 volume = texture(uVolumeTex, uv).rgb;
    fragColor = vec4(scene.rgb + volume, scene.a);
}