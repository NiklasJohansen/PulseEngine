package no.njoh.pulseengine.core.graphics.postprocessing

import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.shared.utils.Extensions.firstOrNullFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.removeWhen

class PostProcessingPipeline
{
    private val effects = mutableListOf<PostProcessingEffect>()

    fun init() = effects.forEachFast { it.init() }

    fun addEffect(effect: PostProcessingEffect) = effects.add(effect)

    fun getEffect(name: String) = effects.firstOrNullFast { it.name == name }

    fun removeEffect(name: String) = effects.removeWhen { it.name == name }

    fun process(texture: Texture): Texture
    {
        var latestTexture = texture
        effects.forEachFast { latestTexture = it.process(latestTexture) }
        return latestTexture
    }

    fun getFinalTexture() = effects.lastOrNull()?.getTexture()

    fun destroy() = effects.forEachFast { it.destroy() }
}