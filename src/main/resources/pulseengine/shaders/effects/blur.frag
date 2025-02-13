#version 150 core

in vec2 v_texCoord;
in vec2 v_blurTexCoords[14];

out vec4 fragColor;

uniform sampler2D s_texture;

void main()
{
	vec4 color = vec4(0.0);
	color += texture(s_texture, v_blurTexCoords[ 0]) * 0.0044299121055113265;
	color += texture(s_texture, v_blurTexCoords[ 1]) * 0.00895781211794;
	color += texture(s_texture, v_blurTexCoords[ 2]) * 0.0215963866053;
	color += texture(s_texture, v_blurTexCoords[ 3]) * 0.0443683338718;
	color += texture(s_texture, v_blurTexCoords[ 4]) * 0.0776744219933;
	color += texture(s_texture, v_blurTexCoords[ 5]) * 0.115876621105;
	color += texture(s_texture, v_blurTexCoords[ 6]) * 0.147308056121;
	color += texture(s_texture, v_texCoord         ) * 0.159576912161;
	color += texture(s_texture, v_blurTexCoords[ 7]) * 0.147308056121;
	color += texture(s_texture, v_blurTexCoords[ 8]) * 0.115876621105;
	color += texture(s_texture, v_blurTexCoords[ 9]) * 0.0776744219933;
	color += texture(s_texture, v_blurTexCoords[10]) * 0.0443683338718;
	color += texture(s_texture, v_blurTexCoords[11]) * 0.0215963866053;
	color += texture(s_texture, v_blurTexCoords[12]) * 0.00895781211794;
	color += texture(s_texture, v_blurTexCoords[13]) * 0.0044299121055113265;

	fragColor = color;
}