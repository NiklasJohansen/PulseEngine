package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.api.ShaderProgram

fun Model.selectProgram(skinnedProgram: ShaderProgram, staticProgram: ShaderProgram): ShaderProgram
{
    return if (hasBones && bones.isNotEmpty()) skinnedProgram else staticProgram
}
