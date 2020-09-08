package no.njoh.pulseengine.modules.graphics.postprocessing

import no.njoh.pulseengine.data.assets.Texture

class PostProcessingPipeline
{
    private val effects = mutableListOf<PostProcessingEffect>()

    fun init() =
        effects.forEach { it.init() }

    fun addEffect(effect: PostProcessingEffect) =
        effect.init().also { effects.add(effect) }

    fun process(texture: Texture): Texture
    {
        var latestTexture = texture
        effects.forEach { latestTexture = it.process(latestTexture) }
        return latestTexture
    }

    fun cleanUp() =
        effects.forEach { it.cleanUp() }
}