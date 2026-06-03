package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.graphics.api.ShaderProgramSet.ShaderVariant.*

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