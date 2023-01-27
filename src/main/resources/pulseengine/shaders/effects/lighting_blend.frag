#version 420 core

#define FXAA_REDUCE_MIN (1.0 / 128.0)
#define FXAA_REDUCE_MUL (1.0 / 8.0)
#define FXAA_SPAN_MAX 8.0

in vec2 texCoordNW;
in vec2 texCoordNE;
in vec2 texCoordSW;
in vec2 texCoordSE;
in vec2 texCoordM;
in vec2 baseTexCoord;

out vec4 fragColor;

layout(binding=0) uniform sampler2D baseTex;
layout(binding=1) uniform sampler2D lightTex;

uniform vec4 ambientColor;
uniform vec2 resolution;
uniform bool enableFxaa;

vec4 fxaa(sampler2D tex)
{
    /**
     * FXAA implementation based on code from: https://github.com/mattdesl/glsl-fxaa
     *
     * The MIT License (MIT) Copyright (c) 2014 Matt DesLauriers
     * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
     * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
     */
    vec3 rgbNW = texture(tex, texCoordNW).rgb;
    vec3 rgbNE = texture(tex, texCoordNE).rgb;
    vec3 rgbSW = texture(tex, texCoordSW).rgb;
    vec3 rgbSE = texture(tex, texCoordSE).rgb;
    vec4 texColor = texture(tex, texCoordM);

    vec3 luma = vec3(0.299, 0.587, 0.114);
    float lumaNW = dot(rgbNW, luma);
    float lumaNE = dot(rgbNE, luma);
    float lumaSW = dot(rgbSW, luma);
    float lumaSE = dot(rgbSE, luma);
    float lumaM  = dot(texColor.rgb, luma);
    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    vec2 dir = vec2((lumaSW + lumaSE) - (lumaNW + lumaNE), (lumaNW + lumaSW) - (lumaNE + lumaSE));
    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * FXAA_REDUCE_MUL), FXAA_REDUCE_MIN);

    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = min(vec2(FXAA_SPAN_MAX), max(vec2(-FXAA_SPAN_MAX), dir * rcpDirMin)) / resolution;

    vec3 rgbA = 0.50 * (texture(tex, texCoordM + dir * (1.0 / 3.0 - 0.5)).rgb + texture(tex, texCoordM + dir * (2.0 / 3.0 - 0.5)).rgb);
    vec3 rgbB = 0.25 * (texture(tex, texCoordM + dir * -0.5).rgb + texture(tex, texCoordM + dir * 0.5).rgb) + 0.5 * rgbA;

    float lumaB = dot(rgbB, luma);
    return ((lumaB < lumaMin) || (lumaB > lumaMax)) ? vec4(rgbA, texColor.a) : vec4(rgbB, texColor.a);
}

void main() {
    vec4 baseColor = texture(baseTex, baseTexCoord);
    vec4 lightColor = enableFxaa ? fxaa(lightTex) : texture(lightTex, texCoordM);
    vec3 lighting = ambientColor.rgb + lightColor.rgb * baseColor.a;
    float alpha = (baseColor.a < 0.1) ? ambientColor.a : 1.0;
    fragColor = vec4(baseColor.rgb * lighting, alpha);
}