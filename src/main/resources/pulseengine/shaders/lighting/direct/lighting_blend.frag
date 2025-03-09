#version 330 core

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

uniform sampler2D baseTex;
uniform sampler2D lightTex;

uniform vec4 ambientColor;
uniform vec2 resolution;
uniform bool enableFxaa;
uniform float dithering;
uniform float fogIntensity;
uniform float fogScale;
uniform vec2 camPos;
uniform float time;

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

float hash(vec2 uv)
{
    return fract(sin(dot(uv, vec2(0.613, 0.697))) * 43759.329);
}

float noise(vec2 uv)
{
    vec2 i = floor(uv);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0));
    vec2 f = fract(uv);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 uv) // Fractal brownian motion
{
    float value = 0.0;
    float amplitude = 0.5;
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.50));
    for (int i = 0; i < 6; ++i)
    {
        value += amplitude * noise(uv);
        uv = uv * rot * 2.0 + 100.0;
        amplitude *= 0.5;
    }
    return value;
}

/**
 * Based on code from: https://www.shadertoy.com/view/NdKGzz and https://thebookofshaders.com/13/
 */
float fog()
{
    vec2 uv = (baseTexCoord - 0.5 * (camPos - vec2(fogScale - 1.0))) / fogScale + (time * vec2(0.1, -0.2));
    vec2 q = vec2(fbm(uv), fbm(uv + 1.0));
    vec2 r = vec2(fbm(q + uv + time * 0.73 + vec2(1.68, 9.35)), fbm(q + uv + time * 0.67 + vec2(8.14, 3.1)));
    float f = fbm(uv + r);
    float c = mix(0.0, fogIntensity, clamp(f * f * 4.0, 0.0, 1.0));
    c = mix(c, 1.0, clamp(length(q), 0.0, 1.0));
    c = mix(c, 1.0, clamp(length(r.x), 0.0, 1.0));
    return c * (f * f * f + 0.3 * f * f + 0.5 * f) * fogIntensity / 3.0;
}

void main()
{
    vec4 baseColor = texture(baseTex, baseTexCoord);
    vec4 lightColor = (enableFxaa ? fxaa(lightTex) : texture(lightTex, texCoordM)) * baseColor.a;
    float noise = fract(sin(dot(baseTexCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    vec3 lighting = (ambientColor.rgb + lightColor.rgb) + mix(-dithering / 255.0, dithering / 255.0, noise);
    vec3 fogColor = fog() * lightColor.rgb;
    float alpha = (baseColor.a < 0.1) ? ambientColor.a : 1.0;

    fragColor = vec4(baseColor.rgb * lighting + fogColor, alpha);
}