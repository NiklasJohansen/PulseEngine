package no.njoh.pulseengine.core.graphics.postprocessing

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.api.RenderTexture

interface PostProcessingEffect
{
    val name: String
    val order: Int
    fun init(engine: PulseEngineInternal)
    fun process(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    fun getTexture(index: Int): RenderTexture?
    fun destroy()
}