package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.api.ShaderVariant

fun Model.selectShaderVariant(): ShaderVariant
{
    return if (hasBones && bones.isNotEmpty()) ShaderVariant.SKINNED else ShaderVariant.STATIC
}
