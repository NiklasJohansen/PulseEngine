package no.njoh.pulseengine.core.graphics.util

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.api.ModelShaderVariant

fun Model.selectShaderVariant(): ModelShaderVariant
{
    return if (hasBones && bones.isNotEmpty()) ModelShaderVariant.SKINNED else ModelShaderVariant.STATIC
}
