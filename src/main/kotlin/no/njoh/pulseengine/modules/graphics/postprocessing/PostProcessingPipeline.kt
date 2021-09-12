package no.njoh.pulseengine.modules.graphics.postprocessing

import no.njoh.pulseengine.data.assets.Texture

class PostProcessingPipeline
{
    private val effects = mutableListOf<PostProcessingEffect>()

    fun init() = effects.forEach { it.init() }

    fun addEffect(effect: PostProcessingEffect) = effects.add(effect)

    fun removeEffect(effect: PostProcessingEffect) = effects.remove(effect)

    fun process(texture: Texture): Texture
    {
        var latestTexture = texture
        effects.forEach { latestTexture = it.process(latestTexture) }
        return latestTexture
    }

    fun reloadShaders() = effects.forEach { it.reloadShaders() }

    fun cleanUp() = effects.forEach { it.cleanUp() }
}