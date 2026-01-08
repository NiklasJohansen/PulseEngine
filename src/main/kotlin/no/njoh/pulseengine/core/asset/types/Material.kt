package no.njoh.pulseengine.core.asset.types

class Material(
    name: String,
    val albedo: Texture?,
    val normal: Texture?,
    val aoMetalRough: Texture?,
    val emissive: Texture?,
    val height: Texture?,
    val cullMode: CullMode,
    val blendMode: BlendMode,
    val alphaCutoff: Float
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