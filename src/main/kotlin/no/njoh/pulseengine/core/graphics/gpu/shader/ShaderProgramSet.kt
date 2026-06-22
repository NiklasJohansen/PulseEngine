package no.njoh.pulseengine.core.graphics.gpu.shader

import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant.*

class ShaderProgramSet(
    val staticProgram: ShaderProgram,
    val skinnedProgram: ShaderProgram
) {
    operator fun get(variant: ShaderVariant) = when (variant)
    {
        STATIC -> staticProgram
        SKINNED -> skinnedProgram
    }

    enum class ShaderVariant
    {
        STATIC, SKINNED
    }
}