// GLSL 1.50 used with OpenGL 3.2
#version 150 core

in vec4 vertexColor;
in vec2 textureCoord;
in float textureIndex;

out vec4 fragColor;

uniform sampler2DArray textureArray;

void main() {
    vec4 textureColor = texture(textureArray, vec3(textureCoord.x, textureCoord.y, floor(textureIndex)));

    if(textureColor.a < 0.2)
        discard;

    // A texture index with decimal higher than 0 is used as alpha mask
    bool isAlphaMaskTexture = fract(textureIndex) > 0.0;

    if(isAlphaMaskTexture)
        fragColor = vertexColor * vec4(1.0, 1.0, 1.0, textureColor.a);
    else
        fragColor = vertexColor * textureColor;
}