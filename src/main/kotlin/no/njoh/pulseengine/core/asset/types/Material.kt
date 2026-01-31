package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.primitives.Color

class Material(
    name: String,
    val baseColor: Color,
    val albedo: Texture?,
    val normal: Texture?,
    val aoMetalRough: Texture?,
    val emissive: Texture?,
    val height: Texture?,
    val cullMode: CullMode,
    val blendMode: BlendMode,
    val alphaCutoff: Float,
    val metallicFactor: Float,
    val roughnessFactor: Float,
    val emissiveFactor: Color,
    val occlusionStrength: Float,
    val normalScale: Float
): Asset(name, name) {

    override fun load() {}
    override fun unload() {}

    enum class BlendMode 
    {
        OPAQUE,     // Alpha ignored (solids)
        MASK,       // Alpha-tested (fences, leaves, etc.)
        TRANSPARENT // Alpha-blended (glass, smoke, etc.)
    }

    enum class CullMode { NONE, BACK }
}