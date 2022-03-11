package no.njoh.pulseengine.core.graphics.postprocessing

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

class PostProcessingPipeline
{
    private val effects = mutableListOf<PostProcessingEffect>()

    fun init() = effects.forEachFast { it.init() }

    fun addEffect(effect: PostProcessingEffect) = effects.add(effect)

    fun removeEffect(effect: PostProcessingEffect) = effects.remove(effect)

    fun process(texture: Texture): Texture
    {
        var latestTexture = texture
        effects.forEachFast { latestTexture = it.process(latestTexture) }
        return latestTexture
    }

    fun getFinalTexture() = effects.lastOrNull()?.getTexture()

    fun cleanUp() = effects.forEachFast { it.cleanUp() }
}