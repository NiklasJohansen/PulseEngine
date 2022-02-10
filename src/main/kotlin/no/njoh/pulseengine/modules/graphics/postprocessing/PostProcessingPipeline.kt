package no.njoh.pulseengine.modules.graphics.postprocessing

import no.njoh.pulseengine.modules.asset.types.Texture
import no.njoh.pulseengine.util.forEachFast

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

    fun reloadShaders() = effects.forEachFast { it.reloadShaders() }

    fun cleanUp() = effects.forEachFast { it.cleanUp() }
}