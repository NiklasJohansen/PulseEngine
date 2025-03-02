package no.njoh.pulseengine.core.graphics.postprocessing

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture

interface PostProcessingEffect
{
    val name: String
    val order: Int
    fun init()
    fun process(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    fun getTexture(index: Int): Texture?
    fun destroy()
}