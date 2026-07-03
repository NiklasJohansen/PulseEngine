#version 430 core

layout(early_fragment_tests) in;

flat in ivec2 vObjectId;
layout(location = 0) out ivec2 outObjectId;

void main()
{
    outObjectId = vObjectId;
}