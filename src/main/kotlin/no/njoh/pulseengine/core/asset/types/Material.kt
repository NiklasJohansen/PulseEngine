package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.primitives.Color

class Material(
    name: String,
    var baseColor: Color                     = Color(1f, 1f, 1f),
    var albedo: Texture?                     = null,
    var normal: Texture?                     = null,
    var aoRoughMetal: Texture?               = null,
    var emissive: Texture?                   = null,
    var height: Texture?                     = null,
    var cullMode: CullMode                   = CullMode.BACK,
    var blendMode: BlendMode                 = BlendMode.OPAQUE,
    var alphaCutoff: Float                   = 0.5f,
    var metallicFactor: Float                = 0f,
    var roughnessFactor: Float               = 1f,
    var emissiveFactor: Color                = Color(0f, 0f, 0f),
    var occlusionStrength: Float             = 1f,
    var heightScale: Float                   = DEFAULT_HEIGHT_SCALE,
    var normalScale: Float                   = 1f,
    var normalOrientation: NormalOrientation = NormalOrientation.NORMAL,
    var xTiling: Float                       = 1f,
    var yTiling: Float                       = 1f,
    var layerMask: Int                       = 1
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

    enum class NormalOrientation { NORMAL, INVERT_U, INVERT_V, INVERT_UV }

    companion object
    {
        const val DEFAULT_ID = 0
        const val DEFAULT_HEIGHT_SCALE = 0.1f
    }
}