package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.primitives.Color

class Material(
    name: String,
    var baseColor: Color,
    var albedo: Texture?,
    var normal: Texture?,
    var aoMetalRough: Texture?,
    var emissive: Texture?,
    var height: Texture?,
    var cullMode: CullMode,
    var blendMode: BlendMode,
    var alphaCutoff: Float,
    var metallicFactor: Float,
    var roughnessFactor: Float,
    var emissiveFactor: Color,
    var occlusionStrength: Float,
    var normalScale: Float,
    var xTiling: Float = 1f,
    var yTiling: Float = 1f
): Asset(name, name) {

    var id = DEFAULT_ID; private set
    var isDirty = true

    fun markDirty()
    {
        isDirty = true
    }

    fun onUploaded(id: Int)
    {
        this.id = id
        isDirty = false
    }

    fun onDeleted()
    {
        id = DEFAULT_ID
        isDirty = true
    }

    override fun load() {}
    override fun unload() {}

    enum class BlendMode 
    {
        OPAQUE, // Alpha ignored (solids)
        MASK,   // Alpha-tested (fences, leaves, etc.)
        BLEND   // Alpha-blended (glass, smoke, etc.)
    }

    enum class CullMode { NONE, BACK }

    companion object
    {
        const val DEFAULT_ID = 0
    }
}