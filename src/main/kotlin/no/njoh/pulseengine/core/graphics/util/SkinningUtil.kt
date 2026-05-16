package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.shared.utils.Logger

const val MAX_SKINNING_BONES = 128
private val warnedBoneLimitModels = HashSet<String>()

fun Model.selectProgram(skinnedProgram: ShaderProgram, staticProgram: ShaderProgram): ShaderProgram
{
    val canSkin = hasBones && bones.isNotEmpty() && bones.size <= MAX_SKINNING_BONES

    if (!canSkin)
    {
        if (hasBones && bones.size > MAX_SKINNING_BONES && warnedBoneLimitModels.add(name))
            Logger.warn { "Model '$name' has ${bones.size} bones, but the shader limit is $MAX_SKINNING_BONES. Rendering depth without skinning." }
        
        return staticProgram
    }

    return skinnedProgram
}