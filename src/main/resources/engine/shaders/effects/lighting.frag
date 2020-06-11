#version 420 core

in vec2 textureCoord;
out vec4 fragColor;

uniform sampler2D tex0;
uniform int lightCount;
uniform int edgeCount;
uniform vec2 resolution;

const int NR_LIGHTS = 1000;
const int NR_EDGES = 1000;

struct Light {
    vec2 position;
    float radius;
    float intensity;
    float type;
    float red;
    float green;
    float blue;
};

struct Edge {
    vec2 point1;
    vec2 point2;
};


layout (std140, binding=1) uniform LightBlock {
    Light lights[NR_LIGHTS];
};

layout (std140, binding=2) uniform EdgeBlock {
    Edge edges[NR_EDGES];
};

bool hasIntersection(vec2 p0, vec2 p1, vec2 p2, vec2 p3)
{
    float s1_x = p1.x - p0.x;
    float s1_y = p1.y - p0.y;
    float s2_x = p3.x - p2.x;
    float s2_y = p3.y - p2.y;
    float s = (-s1_y * (p0.x - p2.x) + s1_x * (p0.y - p2.y)) / (-s2_x * s1_y + s1_x * s2_y);
    float t = ( s2_x * (p0.y - p2.y) - s2_y * (p0.x - p2.x)) / (-s2_x * s1_y + s1_x * s2_y);
    return s >= 0 && s <= 1 && t >= 0 && t <= 1;
}

void main() {

    vec4 tex0Color = texture(tex0, textureCoord);
    vec2 uv = gl_FragCoord.xy;
    uv.y = resolution.y - uv.y;

    for (int i = 0; i < lightCount; i++)
    {
        Light light = lights[i];

        vec2 diff = light.position - uv;
        float dist = length(diff);
        float radius = light.radius;

        if (dist < radius)
        {
            bool inShadow = false;
            for (int j = 0; j < edgeCount; j++)
            {
                Edge edge = edges[j];

                if (hasIntersection(light.position, uv, edge.point1, edge.point2))
                {
                    // TODO: soft shadows send two additional rays from normals of light point, need a light size
                    
                    inShadow = true;
                    break;
                }
            }

            if (!inShadow)
            {
                float att = clamp(1.0 - dist*dist/(radius*radius), 0.0, 1.0);

                att = clamp(1.0 - dist/radius, 0.0, 1.0);

                att *= att;

                tex0Color.rgb += vec3(light.red, light.green, light.blue) * light.intensity * att;
            }
        }
    }

    fragColor = tex0Color;
}