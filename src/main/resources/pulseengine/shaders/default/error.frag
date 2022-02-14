#version 150 core

out vec4 fragColor;

const float GRID_SIZE = 100;

void main() {

    if ((int(gl_FragCoord.x / GRID_SIZE) + int(gl_FragCoord.y / GRID_SIZE)) % 2 == 0)
        fragColor = vec4(1.0, 0.0, 0.85, 1.0);
    else
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
}