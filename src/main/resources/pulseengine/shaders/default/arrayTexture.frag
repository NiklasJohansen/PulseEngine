// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec4 vertexColor;
in vec2 textureCoord;
in float textureIndex;

out vec4 fragColor;

uniform sampler2DArray textureArray;

void main() {

    vec4 textureColor = vec4(1.0, 1.0, 1.0, 1.0);

    if(textureIndex >= 0)
        textureColor = texture(textureArray, vec3(textureCoord.x, textureCoord.y, floor(textureIndex)));

    if(textureColor.a < 0.4)
        discard;

    fragColor = vertexColor * textureColor;
}