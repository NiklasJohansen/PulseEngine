#version 150 core

in vec2 uv;
in vec2 blurUvs[14];

out vec4 fragColor;

uniform sampler2D tex;

void main()
{
	vec4 color = vec4(0.0);
	color += texture(tex, blurUvs[0])  * 0.0044299121055113265;
	color += texture(tex, blurUvs[1])  * 0.00895781211794;
	color += texture(tex, blurUvs[2])  * 0.0215963866053;
	color += texture(tex, blurUvs[3])  * 0.0443683338718;
	color += texture(tex, blurUvs[4])  * 0.0776744219933;
	color += texture(tex, blurUvs[5])  * 0.115876621105;
	color += texture(tex, blurUvs[6])  * 0.147308056121;
	color += texture(tex, uv)          * 0.159576912161;
	color += texture(tex, blurUvs[7])  * 0.147308056121;
	color += texture(tex, blurUvs[8])  * 0.115876621105;
	color += texture(tex, blurUvs[9])  * 0.0776744219933;
	color += texture(tex, blurUvs[10]) * 0.0443683338718;
	color += texture(tex, blurUvs[11]) * 0.0215963866053;
	color += texture(tex, blurUvs[12]) * 0.00895781211794;
	color += texture(tex, blurUvs[13]) * 0.0044299121055113265;

	fragColor = color;
}